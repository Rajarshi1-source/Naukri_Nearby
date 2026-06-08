package com.naukrinearby.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.naukrinearby.config.NotificationProperties;
import com.naukrinearby.generation.WhatsAppSender;
import com.naukrinearby.model.entity.Job;
import com.naukrinearby.model.entity.NotificationLog;
import com.naukrinearby.repository.CandidateProfileRepository;
import com.naukrinearby.repository.JobRepository;
import com.naukrinearby.repository.NotificationLogRepository;
import com.naukrinearby.util.RedisRateLimiter;
import com.naukrinearby.worker.JobIndexedEvent;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WhatsApp job-alert pipeline (master plan §9.4). Matching candidates are enqueued to a Redis Stream;
 * the consumer dedups with SETNX on {@code (user, job)} AND a unique DB row, so each candidate is
 * notified about a job at most once even under retries/concurrency (idempotent by design).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

	private static final int MATCH_LIMIT = 500;
	private static final Duration DEDUP_TTL = Duration.ofDays(7);

	private final CandidateProfileRepository candidateProfileRepo;
	private final NotificationLogRepository notificationLogRepo;
	private final JobRepository jobRepo;
	private final TranslationService translationService;
	private final WhatsAppSender whatsAppSender;
	private final StringRedisTemplate redis;
	private final RedisRateLimiter rateLimiter;
	private final NotificationProperties props;
	private final MeterRegistry meterRegistry;

	/** Reacts to a newly-indexed job: find nearby, skill-matched candidates and enqueue alerts. */
	@Async
	@EventListener
	public void onJobIndexed(JobIndexedEvent event) {
		notifyMatchingCandidates(event.jobId());
	}

	public void notifyMatchingCandidates(Long jobId) {
		List<Object[]> rows = candidateProfileRepo.findMatchingCandidates(jobId, props.matchThreshold(), MATCH_LIMIT);
		String day = LocalDate.now().toString();
		int enqueued = 0;
		for (Object[] row : rows) {
			Long userId = ((Number) row[0]).longValue();
			String phone = (String) row[2];
			String lang = (String) row[3];
			if (phone == null) {
				continue;
			}
			// Daily cap so we never spam a candidate (master plan §9.4).
			if (!rateLimiter.tryAcquire("notif:daily:" + userId + ":" + day, props.dailyLimit(), Duration.ofDays(1))) {
				continue;
			}
			// Cheap pre-check; the SETNX + DB unique constraint are the real guards.
			if (notificationLogRepo.existsByCandidateIdAndJobId(userId, jobId)) {
				continue;
			}
			enqueue(jobId, userId, phone, lang);
			enqueued++;
		}
		log.info("Enqueued {} WhatsApp alerts for job {}", enqueued, jobId);
	}

	private void enqueue(Long jobId, Long userId, String phone, String lang) {
		Map<String, String> fields = new HashMap<>();
		fields.put("jobId", String.valueOf(jobId));
		fields.put("userId", String.valueOf(userId));
		fields.put("phone", phone);
		fields.put("lang", lang == null ? "en" : lang);
		redis.opsForStream().add(props.stream(), fields);
	}

	/**
	 * Consumes one queued alert. Returns true if it was acked-equivalent (processed or safely skipped);
	 * false means the consumer should route it to the DLQ.
	 */
	@Transactional
	public boolean process(Map<String, String> fields) {
		Long jobId = Long.valueOf(fields.get("jobId"));
		Long userId = Long.valueOf(fields.get("userId"));
		String phone = fields.get("phone");
		String lang = fields.getOrDefault("lang", "en");

		if (notificationLogRepo.existsByCandidateIdAndJobId(userId, jobId)) {
			return true;
		}
		// SETNX claim: only the first consumer for this (user,job) proceeds.
		Boolean claimed = redis.opsForValue().setIfAbsent("notif:sent:" + userId + ":" + jobId, "1", DEDUP_TTL);
		if (claimed == null || !claimed) {
			return true;
		}
		try {
			Job job = jobRepo.findById(jobId).orElse(null);
			if (job == null) {
				return true;
			}
			String text = buildMessage(job);
			String translated = translationService.translateOrEnglish(text, lang);
			String sid = whatsAppSender.send(phone, translated);
			meterRegistry.counter("notifications_sent_total", "channel", "whatsapp").increment();

			NotificationLog logEntry = NotificationLog.of(userId, jobId, "WHATSAPP", "SENT", translated, lang);
			logEntry.setMessageSid(sid);
			notificationLogRepo.save(logEntry);
			return true;
		}
		catch (DataIntegrityViolationException dup) {
			// DB unique backstop fired — another path already logged this. Treat as done.
			return true;
		}
		catch (Exception ex) {
			// Release the SETNX claim so a retry/DLQ replay can succeed later.
			redis.delete("notif:sent:" + userId + ":" + jobId);
			log.warn("Failed to send WhatsApp alert job={} user={}: {}", jobId, userId, ex.getMessage());
			return false;
		}
	}

	private String buildMessage(Job job) {
		String company = job.getEmployer() == null ? "an employer" : job.getEmployer().getCompanyName();
		StringBuilder sb = new StringBuilder();
		sb.append("New job near you: ").append(job.getTitle())
				.append(" at ").append(company)
				.append(" in ").append(job.getCity()).append(".");
		if (job.getSalaryMin() != null || job.getSalaryMax() != null) {
			sb.append(" Salary: Rs.")
					.append(job.getSalaryMin() == null ? "?" : job.getSalaryMin())
					.append("-")
					.append(job.getSalaryMax() == null ? "?" : job.getSalaryMax())
					.append("/month.");
		}
		sb.append(" Apply on NaukriNearby.");
		return sb.toString();
	}
}
