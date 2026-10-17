package com.naukrinearby.service;

import java.time.Instant;

import com.naukrinearby.config.TrustProperties;
import com.naukrinearby.exception.ConflictException;
import com.naukrinearby.exception.NotFoundException;
import com.naukrinearby.exception.ValidationException;
import com.naukrinearby.model.entity.Job;
import com.naukrinearby.model.entity.JobReport;
import com.naukrinearby.model.enums.ModerationStatus;
import com.naukrinearby.model.enums.ReportReason;
import com.naukrinearby.repository.JobReportRepository;
import com.naukrinearby.repository.JobRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Community reports and the admin review queue (ADR 0007). Every moderation change updates the job
 * row, bumps its version and writes an outbox event in the same transaction; the sync worker then
 * removes the job from (or restores it to) the search index. Elasticsearch is never written here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationService {

	private final JobRepository jobRepo;
	private final JobReportRepository reportRepo;
	private final JobOutboxWriter outboxWriter;
	private final TrustProperties props;

	/**
	 * Records a user's report. Reaching {@code naukri.trust.report-threshold} distinct reports since the
	 * last admin decision moves the job back to {@code PENDING_REVIEW}.
	 *
	 * @return true when this report escalated the job to review
	 */
	@Transactional
	public boolean report(Long jobId, Long reporterId, ReportReason reason, String details) {
		Job job = jobRepo.findById(jobId)
				.filter(Job::isPubliclyVisible)
				.orElseThrow(() -> new NotFoundException("Job not found: " + jobId));
		if (job.getEmployer() != null && reporterId.equals(job.getEmployer().getUserId())) {
			throw new ValidationException("You cannot report your own job");
		}
		if (reportRepo.existsByJobIdAndReporterId(jobId, reporterId)) {
			throw new ConflictException("You have already reported this job");
		}
		try {
			reportRepo.saveAndFlush(new JobReport(jobId, reporterId, reason, trim(details)));
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("You have already reported this job");
		}
		long reports = job.getModeratedAt() == null
				? reportRepo.countByJobId(jobId)
				: reportRepo.countByJobIdAndCreatedAtAfter(jobId, job.getModeratedAt());
		if (reports < props.reportThreshold()) {
			return false;
		}
		job.setModerationStatus(ModerationStatus.PENDING_REVIEW);
		job.setModerationNote("Escalated after " + reports + " user reports");
		job.setVersion(job.getVersion() + 1);
		jobRepo.save(job);
		outboxWriter.write(job, "JOB_UPDATED");
		log.info("Job {} moved to review after {} reports", jobId, reports);
		return true;
	}

	@Transactional(readOnly = true)
	public Page<Job> queue(ModerationStatus status, Pageable pageable) {
		return jobRepo.findByModerationStatusOrderByCreatedAtAsc(
				status == null ? ModerationStatus.PENDING_REVIEW : status, pageable);
	}

	@Transactional(readOnly = true)
	public long reportCount(Long jobId) {
		return reportRepo.countByJobId(jobId);
	}

	/** Admin decision. Approval re-indexes the job and lets alerts go out; rejection keeps it hidden. */
	@Transactional
	public Job decide(Long jobId, ModerationStatus decision, String note) {
		if (decision != ModerationStatus.APPROVED && decision != ModerationStatus.REJECTED) {
			throw new ValidationException("Decision must be APPROVED or REJECTED");
		}
		Job job = jobRepo.findById(jobId)
				.orElseThrow(() -> new NotFoundException("Job not found: " + jobId));
		boolean wasApproved = job.getModerationStatus() == ModerationStatus.APPROVED;
		job.setModerationStatus(decision);
		job.setModeratedAt(Instant.now());
		job.setModerationNote(trim(note));
		job.setVersion(job.getVersion() + 1);
		job = jobRepo.save(job);
		// JOB_APPROVED (not JOB_UPDATED) so the sync worker also triggers candidate alerts, which
		// were held back while the job was under review.
		outboxWriter.write(job, decision == ModerationStatus.APPROVED && !wasApproved ? "JOB_APPROVED" : "JOB_UPDATED");
		log.info("Job {} moderated: {}", jobId, decision);
		return job;
	}

	private static String trim(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		String t = s.strip();
		return t.length() > 1000 ? t.substring(0, 1000) : t;
	}
}
