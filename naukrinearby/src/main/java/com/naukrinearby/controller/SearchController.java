package com.naukrinearby.controller;

import java.util.List;

import com.naukrinearby.model.dto.job.JobResponse;
import com.naukrinearby.model.dto.search.JobMatch;
import com.naukrinearby.model.dto.search.SearchQuery;
import com.naukrinearby.model.dto.search.SearchResponse;
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

	/** Public job detail — read from Postgres (the source of truth). */
	@GetMapping("/api/jobs/{slug}")
	public JobResponse detail(@PathVariable String slug) {
		return JobResponse.from(jobService.getBySlug(slug));
	}

	/** Personalized recommendations for the signed-in candidate (pgvector + PostGIS). */
	@GetMapping("/api/candidate/recommendations")
	@PreAuthorize("hasRole('CANDIDATE')")
	public List<JobMatch> recommendations(@AuthenticationPrincipal AuthPrincipal principal) {
		return matchingService.recommendedJobs(principal.id());
	}
}
