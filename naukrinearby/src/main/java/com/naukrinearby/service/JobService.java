package com.naukrinearby.service;

import java.util.List;

import com.naukrinearby.exception.ForbiddenException;
import com.naukrinearby.exception.NotFoundException;
import com.naukrinearby.exception.ValidationException;
import com.naukrinearby.model.dto.job.JobCreateRequest;
import com.naukrinearby.model.dto.job.JobUpdateRequest;
import com.naukrinearby.model.entity.Employer;
import com.naukrinearby.model.entity.Job;
import com.naukrinearby.model.enums.JobStatus;
import com.naukrinearby.model.enums.ModerationStatus;
import com.naukrinearby.repository.EmployerRepository;
import com.naukrinearby.repository.JobRepository;
import com.naukrinearby.service.trust.JobRiskScorer;
import com.naukrinearby.service.trust.RiskAssessment;
import com.naukrinearby.util.SlugGenerator;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Job write-side. The job row AND its outbox event commit atomically — Elasticsearch is NOT touched
 * here; {@code OutboxSyncWorker} drains the outbox into ES (master plan §9.2, starter §1.2). The
 * domain {@code version} column is bumped on every change so the sync never indexes a stale update.
 *
 * <p>Every create/update is scored by {@link JobRiskScorer} (ADR 0007). A risky posting is stored
 * with {@code PENDING_REVIEW}; the moderation status rides the outbox payload and the sync worker
 * keeps such jobs out of the search index until an admin approves them.
 */
@Service
@RequiredArgsConstructor
public class JobService {

	private final JobRepository jobRepo;
	private final JobOutboxWriter outboxWriter;
	private final EmployerRepository employerRepo;
	private final EmbeddingService embeddingService;
	private final GeocodingService geocodingService;
	private final JobRiskScorer riskScorer;

	@Transactional
	public Job createJob(JobCreateRequest req, Long userId) {
		Employer employer = employerRepo.findByUserId(userId)
				.orElseThrow(() -> new ValidationException("Employer profile not found"));

		Job job = new Job();
		job.setEmployer(employer);
		job.setTitle(req.title());
		job.setSlug(SlugGenerator.slugify(req.title()));
		job.setDescription(req.description());
		job.setCategory(req.category());
		job.setSkillsRequired(toArray(req.skillsRequired()));
		job.setSalaryMin(req.salaryMin());
		job.setSalaryMax(req.salaryMax());
		if (req.salaryType() != null) {
			job.setSalaryType(req.salaryType());
		}
		if (req.experienceRequiredMonths() != null) {
			job.setExperienceRequiredMonths(req.experienceRequiredMonths());
		}
		job.setLocation(geocodingService.validateAndBuild(req.lat(), req.lng()));
		job.setAddress(req.address());
		job.setCity(req.city());
		job.setState(req.state());
		job.setPincode(req.pincode());
		job.setRadiusKm(geocodingService.clampRadiusKm(req.radiusKm()));
		job.setStatus(JobStatus.ACTIVE);
		job.setVersion(1);
		applyRiskAssessment(job, employer);

		job = jobRepo.save(job);
		writeRequirementVector(job);
		outboxWriter.write(job, "JOB_CREATED");
		return job;
	}

	@Transactional
	public Job updateJob(Long jobId, JobUpdateRequest req, Long userId) {
		Job job = loadOwned(jobId, userId);
		if (req.title() != null) {
			job.setTitle(req.title());
		}
		if (req.description() != null) {
			job.setDescription(req.description());
		}
		if (req.category() != null) {
			job.setCategory(req.category());
		}
		if (req.skillsRequired() != null) {
			job.setSkillsRequired(toArray(req.skillsRequired()));
		}
		if (req.salaryMin() != null) {
			job.setSalaryMin(req.salaryMin());
		}
		if (req.salaryMax() != null) {
			job.setSalaryMax(req.salaryMax());
		}
		if (req.salaryType() != null) {
			job.setSalaryType(req.salaryType());
		}
		if (req.experienceRequiredMonths() != null) {
			job.setExperienceRequiredMonths(req.experienceRequiredMonths());
		}
		if (req.radiusKm() != null) {
			job.setRadiusKm(geocodingService.clampRadiusKm(req.radiusKm()));
		}
		applyRiskAssessment(job, job.getEmployer());
		job.setVersion(job.getVersion() + 1);
		job = jobRepo.save(job);
		writeRequirementVector(job);
		outboxWriter.write(job, "JOB_UPDATED");
		return job;
	}

	@Transactional
	public Job updateStatus(Long jobId, JobStatus status, Long userId) {
		Job job = loadOwned(jobId, userId);
		job.setStatus(status);
		job.setVersion(job.getVersion() + 1);
		job = jobRepo.save(job);
		outboxWriter.write(job, status == JobStatus.CLOSED ? "JOB_CLOSED" : "JOB_UPDATED");
		return job;
	}

	/**
	 * Job detail by slug. Jobs that are not approved are visible only to their employer and admins;
	 * everyone else gets a 404, so a pending or rejected posting cannot be reached by URL.
	 */
	@Transactional(readOnly = true)
	public Job getForViewer(String slug, Long viewerUserId, boolean viewerIsAdmin) {
		Job job = jobRepo.findBySlug(slug)
				.orElseThrow(() -> new NotFoundException("Job not found: " + slug));
		if (job.getModerationStatus() != ModerationStatus.APPROVED
				&& !viewerIsAdmin && !isOwner(job, viewerUserId)) {
			throw new NotFoundException("Job not found: " + slug);
		}
		return job;
	}

	/** A job that is open and approved — what candidates may apply to over any channel. */
	@Transactional(readOnly = true)
	public Job getPublicJob(Long jobId) {
		return jobRepo.findById(jobId)
				.filter(Job::isPubliclyVisible)
				.orElseThrow(() -> new NotFoundException("Job not found: " + jobId));
	}

	@Transactional(readOnly = true)
	public Page<Job> employerJobs(Long userId, Pageable pageable) {
		Employer employer = employerRepo.findByUserId(userId)
				.orElseThrow(() -> new ValidationException("Employer profile not found"));
		return jobRepo.findByEmployer_IdOrderByCreatedAtDesc(employer.getId(), pageable);
	}

	private void applyRiskAssessment(Job job, Employer employer) {
		RiskAssessment risk = riskScorer.assess(job, employer);
		job.setRiskScore(risk.score());
		job.setRiskReasons(risk.reasonCodes());
		// An admin rejection sticks; anything else is re-decided from the current content.
		if (job.getModerationStatus() != ModerationStatus.REJECTED) {
			job.setModerationStatus(riskScorer.needsReview(risk)
					? ModerationStatus.PENDING_REVIEW : ModerationStatus.APPROVED);
		}
	}

	private static boolean isOwner(Job job, Long userId) {
		return userId != null && job.getEmployer() != null && userId.equals(job.getEmployer().getUserId());
	}

	private Job loadOwned(Long jobId, Long userId) {
		Job job = jobRepo.findById(jobId)
				.orElseThrow(() -> new NotFoundException("Job not found: " + jobId));
		if (!isOwner(job, userId)) {
			throw new ForbiddenException("You do not own this job");
		}
		return job;
	}

	private void writeRequirementVector(Job job) {
		String literal = embeddingService.toVectorLiteral(embeddingText(job));
		if (literal != null) {
			jobRepo.updateRequirementVector(job.getId(), literal);
		}
	}

	private static String embeddingText(Job job) {
		String skills = job.getSkillsRequired() == null ? "" : String.join(", ", job.getSkillsRequired());
		return (job.getTitle() == null ? "" : job.getTitle()) + ". " + skills;
	}

	private static String[] toArray(List<String> values) {
		return values == null ? new String[0] : values.toArray(new String[0]);
	}
}
