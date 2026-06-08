package com.naukrinearby.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.naukrinearby.exception.NotFoundException;
import com.naukrinearby.model.dto.job.JobResponse;
import com.naukrinearby.model.entity.Job;
import com.naukrinearby.model.entity.SavedJob;
import com.naukrinearby.repository.JobRepository;
import com.naukrinearby.repository.SavedJobRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Candidate saved/bookmarked jobs. Save is idempotent on the {@code UNIQUE(user_id, job_id)}
 * constraint (re-saving is a no-op, never a second row or an error).
 */
@Service
@RequiredArgsConstructor
public class SavedJobService {

	private final SavedJobRepository savedJobRepo;
	private final JobRepository jobRepo;

	@Transactional
	public void save(Long userId, Long jobId) {
		if (!jobRepo.existsById(jobId)) {
			throw new NotFoundException("Job not found: " + jobId);
		}
		if (savedJobRepo.existsByUserIdAndJobId(userId, jobId)) {
			return;
		}
		try {
			savedJobRepo.save(new SavedJob(userId, jobId));
		}
		catch (DataIntegrityViolationException ex) {
			// Lost the race against a concurrent save — the unique constraint held; treat as success.
		}
	}

	@Transactional
	public void unsave(Long userId, Long jobId) {
		savedJobRepo.deleteByUserIdAndJobId(userId, jobId);
	}

	@Transactional(readOnly = true)
	public List<JobResponse> list(Long userId) {
		List<Long> jobIds = savedJobRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
				.map(SavedJob::getJobId)
				.toList();
		if (jobIds.isEmpty()) {
			return List.of();
		}
		Map<Long, Job> byId = jobRepo.findAllById(jobIds).stream()
				.collect(Collectors.toMap(Job::getId, Function.identity()));
		// Preserve saved-time order; skip any job that was deleted out from under a saved row.
		return jobIds.stream()
				.map(byId::get)
				.filter(j -> j != null)
				.map(JobResponse::from)
				.toList();
	}
}
