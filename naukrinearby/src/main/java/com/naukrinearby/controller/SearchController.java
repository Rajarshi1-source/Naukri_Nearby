package com.naukrinearby.controller;

import java.util.List;

import com.naukrinearby.model.dto.job.JobResponse;
import com.naukrinearby.model.dto.search.JobMatch;
import com.naukrinearby.model.dto.search.SearchQuery;
import com.naukrinearby.model.dto.search.SearchResponse;
import com.naukrinearby.model.entity.Job;
import com.naukrinearby.security.AuthPrincipal;
import com.naukrinearby.service.JobService;
import com.naukrinearby.service.MatchingService;
import com.naukrinearby.service.SearchService;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SearchController {

	private final SearchService searchService;
	private final JobService jobService;
	private final MatchingService matchingService;

	/** Public hyperlocal job search (Elasticsearch, PostGIS fallback). */
	@GetMapping("/api/jobs/search")
	public SearchResponse search(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) Double lat,
			@RequestParam(required = false) Double lng,
			@RequestParam(required = false) Integer radius,
			@RequestParam(required = false) String category,
			@RequestParam(required = false, defaultValue = "0") int size) {
		return searchService.search(new SearchQuery(q, lat, lng, radius, category, size));
	}

	/**
	 * Personalized recommendations for the signed-in candidate (pgvector + PostGIS). Spec-aligned
	 * alias of {@code /api/candidate/recommendations}. Declared as a literal path so it takes
	 * precedence over {@code GET /api/jobs/{slug}} (Spring matches exact paths before path variables).
	 */
	@GetMapping("/api/jobs/recommended")
	@PreAuthorize("hasRole('CANDIDATE')")
	public List<JobMatch> recommended(@AuthenticationPrincipal AuthPrincipal principal) {
		return matchingService.recommendedJobs(principal.id());
	}

	/**
	 * Public job detail — read from Postgres (the source of truth). A job under review is visible only
	 * to its employer and admins, who also get the moderation outcome and risk reasons (ADR 0007).
	 */
	@GetMapping("/api/jobs/{slug}")
	public JobResponse detail(@PathVariable String slug, @AuthenticationPrincipal AuthPrincipal principal) {
		Long viewerId = principal == null ? null : principal.id();
		boolean admin = principal != null && "ADMIN".equals(principal.role());
		Job job = jobService.getForViewer(slug, viewerId, admin);
		boolean owner = viewerId != null && job.getEmployer() != null
				&& viewerId.equals(job.getEmployer().getUserId());
		return admin || owner ? JobResponse.forOwner(job) : JobResponse.from(job);
	}

	/** Personalized recommendations for the signed-in candidate (pgvector + PostGIS). */
	@GetMapping("/api/candidate/recommendations")
	@PreAuthorize("hasRole('CANDIDATE')")
	public List<JobMatch> recommendations(@AuthenticationPrincipal AuthPrincipal principal) {
		return matchingService.recommendedJobs(principal.id());
	}
}
