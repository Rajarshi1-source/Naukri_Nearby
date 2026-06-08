package com.naukrinearby.service;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import com.naukrinearby.exception.DuplicateApplicationException;
import com.naukrinearby.exception.ForbiddenException;
import com.naukrinearby.exception.NotFoundException;
import com.naukrinearby.exception.ValidationException;
import com.naukrinearby.model.entity.Application;
import com.naukrinearby.model.entity.Job;
import com.naukrinearby.model.enums.ApplicationStatus;
import com.naukrinearby.model.enums.JobStatus;
import com.naukrinearby.repository.ApplicationRepository;
import com.naukrinearby.repository.EmployerRepository;
import com.naukrinearby.repository.JobRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Job applications. Idempotent on the {@code UNIQUE(job_id, candidate_id)} constraint: the second
 * apply returns 409 (master plan §9.4). The DB constraint is the source of truth even under a race.
 */
@Service
@RequiredArgsConstructor
public class ApplicationService {

	private final ApplicationRepository applicationRepo;
	private final JobRepository jobRepo;
	private final EmployerRepository employerRepo;

	/** Allowed application status transitions (employer-driven). Terminal states have no successors. */
	private static final Map<ApplicationStatus, Set<ApplicationStatus>> TRANSITIONS = buildTransitions();

	private static Map<ApplicationStatus, Set<ApplicationStatus>> buildTransitions() {
		Map<ApplicationStatus, Set<ApplicationStatus>> m = new EnumMap<>(ApplicationStatus.class);
		m.put(ApplicationStatus.APPLIED,
				Set.of(ApplicationStatus.VIEWED, ApplicationStatus.SHORTLISTED, ApplicationStatus.REJECTED));
		m.put(ApplicationStatus.VIEWED,
				Set.of(ApplicationStatus.SHORTLISTED, ApplicationStatus.REJECTED));
		m.put(ApplicationStatus.SHORTLISTED,
				Set.of(ApplicationStatus.HIRED, ApplicationStatus.REJECTED));
		m.put(ApplicationStatus.REJECTED, Set.of());
		m.put(ApplicationStatus.HIRED, Set.of());
		return m;
	}

	@Transactional
	public Application apply(Long jobId, Long candidateId, String coverNote) {
		Job job = jobRepo.findById(jobId)
				.orElseThrow(() -> new NotFoundException("Job not found: " + jobId));
		if (job.getStatus() != JobStatus.ACTIVE) {
			throw new ValidationException("This job is no longer accepting applications");
		}
		if (applicationRepo.existsByJobIdAndCandidateId(jobId, candidateId)) {
			throw new DuplicateApplicationException("You have already applied to this job");
		}
		try {
			Application saved = applicationRepo.save(new Application(jobId, candidateId, coverNote));
			jobRepo.incrementApplicationCount(jobId);
			return saved;
		}
		catch (DataIntegrityViolationException ex) {
			// Lost the race against a concurrent apply — the unique constraint held.
			throw new DuplicateApplicationException("You have already applied to this job");
		}
	}

	@Transactional(readOnly = true)
	public Page<Application> myApplications(Long candidateId, Pageable pageable) {
		return applicationRepo.findByCandidateIdOrderByAppliedAtDesc(candidateId, pageable);
	}

	@Transactional(readOnly = true)
	public Page<Application> applicantsForJob(Long jobId, Long employerUserId, Pageable pageable) {
		Job job = jobRepo.findById(jobId)
				.orElseThrow(() -> new NotFoundException("Job not found: " + jobId));
		var employer = employerRepo.findByUserId(employerUserId)
				.orElseThrow(() -> new ForbiddenException("Employer profile not found"));
		if (job.getEmployer() == null || !job.getEmployer().getId().equals(employer.getId())) {
			throw new ForbiddenException("You do not own this job");
		}
		return applicationRepo.findByJobIdOrderByAppliedAtDesc(jobId, pageable);
	}

	/** Employer changes an application's status, enforcing valid transitions and job ownership. */
	@Transactional
	public Application updateStatus(Long applicationId, Long employerUserId, String newStatusRaw) {
		ApplicationStatus newStatus;
		try {
			newStatus = ApplicationStatus.valueOf(newStatusRaw.trim().toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			throw new ValidationException("Unknown application status: " + newStatusRaw);
		}
		Application application = applicationRepo.findById(applicationId)
				.orElseThrow(() -> new NotFoundException("Application not found: " + applicationId));
		Job job = jobRepo.findById(application.getJobId())
				.orElseThrow(() -> new NotFoundException("Job not found: " + application.getJobId()));
		var employer = employerRepo.findByUserId(employerUserId)
				.orElseThrow(() -> new ForbiddenException("Employer profile not found"));
		if (job.getEmployer() == null || !job.getEmployer().getId().equals(employer.getId())) {
			throw new ForbiddenException("You do not own this job");
		}
		ApplicationStatus current = application.getStatus();
		if (current == newStatus) {
			return application;
		}
		if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(newStatus)) {
			throw new ValidationException("Illegal status transition: " + current + " -> " + newStatus);
		}
		application.setStatus(newStatus);
		return applicationRepo.save(application);
	}
}
