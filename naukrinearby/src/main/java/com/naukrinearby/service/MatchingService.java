package com.naukrinearby.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.naukrinearby.model.dto.job.JobResponse;
import com.naukrinearby.model.dto.search.JobMatch;
import com.naukrinearby.model.entity.Job;
import com.naukrinearby.repository.JobRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Semantic + geospatial matching via pgvector cosine ({@code <=>}) and PostGIS {@code ST_DWithin}
 * (master plan §8.3). Used for candidate "recommended jobs".
 */
@Service
@RequiredArgsConstructor
public class MatchingService {

	private static final int DEFAULT_LIMIT = 20;

	private final JobRepository jobRepo;

	@Transactional(readOnly = true)
	public List<JobMatch> recommendedJobs(Long userId) {
		List<Object[]> rows = jobRepo.findRecommendedForCandidate(userId, DEFAULT_LIMIT);
		if (rows.isEmpty()) {
			return List.of();
		}
		// Preserve similarity ordering while batch-loading jobs to avoid N+1.
		Map<Long, Double[]> scores = new LinkedHashMap<>();
		for (Object[] row : rows) {
			Long jobId = ((Number) row[0]).longValue();
			double similarity = row[1] == null ? 0 : ((Number) row[1]).doubleValue();
			Double distanceKm = row[2] == null ? null : ((Number) row[2]).doubleValue();
			scores.put(jobId, new Double[] { similarity, distanceKm });
		}
		Map<Long, Job> jobs = new LinkedHashMap<>();
		jobRepo.findAllById(scores.keySet()).forEach(j -> jobs.put(j.getId(), j));

		List<JobMatch> matches = new ArrayList<>();
		for (var entry : scores.entrySet()) {
			Job job = jobs.get(entry.getKey());
			if (job == null) {
				continue;
			}
			Double[] sd = entry.getValue();
			matches.add(new JobMatch(JobResponse.from(job), sd[0], sd[1]));
		}
		return matches;
	}
}
