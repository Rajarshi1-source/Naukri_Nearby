package com.naukrinearby.service.search;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.naukrinearby.config.SearchProperties;
import com.naukrinearby.model.dto.search.SearchQuery;
import com.naukrinearby.model.dto.search.SearchResponse;
import com.naukrinearby.model.dto.search.SearchResultItem;
import com.naukrinearby.model.entity.Job;
import com.naukrinearby.repository.JobRepository;
import com.naukrinearby.service.EmbeddingService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Hybrid ranking (master plan G4): fuses the Elasticsearch BM25 lexical leg with the pgvector cosine
 * semantic leg via {@link ReciprocalRankFusion}. Returns jobs ordered by fused score; this lets a
 * query match on either exact words ("plumber") or meaning ("pipe repair") without tuning weights.
 * On ES failure the caller ({@code SearchService}) catches and degrades to PostGIS.
 */
@Component
@RequiredArgsConstructor
public class HybridSearchStrategy {

	private final ElasticsearchClient es;
	private final JobRepository jobRepo;
	private final EmbeddingService embeddingService;
	private final SearchProperties props;

	/** Runs both legs, fuses, and materializes the ordered results. Throws on ES failure. */
	public SearchResponse search(SearchQuery query) throws Exception {
		int window = props.rrfWindow();
		List<Long> bm25 = bm25Ids(query, window);
		List<Long> vector = vectorIds(query, window);

		List<Long> fused = ReciprocalRankFusion.fuse(props.rrfK(), List.of(bm25, vector));
		int size = query.size() > 0 ? query.size() : props.maxResults();
		List<Long> top = fused.size() > size ? fused.subList(0, size) : fused;

		Map<Long, Job> jobs = new LinkedHashMap<>();
		jobRepo.findAllById(top).forEach(j -> jobs.put(j.getId(), j));

		List<SearchResultItem> items = new ArrayList<>();
		for (Long id : top) {
			Job job = jobs.get(id);
			if (job == null) {
				continue;
			}
			Double distanceKm = query.hasGeo() ? jobRepo.distanceKm(id, query.lat(), query.lng()) : null;
			items.add(toItem(job, distanceKm));
		}
		return new SearchResponse(items, items.size(), "hybrid", false);
	}

	@SuppressWarnings("rawtypes")
	private List<Long> bm25Ids(SearchQuery query, int window) throws Exception {
		var resp = es.search(s -> s
				.index(props.index())
				.size(window)
				.source(src -> src.filter(f -> f.includes("id")))
				.sort(so -> so.score(sc -> sc.order(SortOrder.Desc)))
				.query(q -> q.bool(b -> {
					b.filter(f -> f.term(t -> t.field("status").value("ACTIVE")));
					if (query.category() != null && !query.category().isBlank()) {
						b.filter(f -> f.term(t -> t.field("category").value(query.category())));
					}
					if (query.hasGeo() && query.radiusKm() != null) {
						b.filter(f -> f.geoDistance(g -> g
								.field("location")
								.distance(query.radiusKm() + "km")
								.location(loc -> loc.latlon(ll -> ll.lat(query.lat()).lon(query.lng())))));
					}
					if (query.q() != null && !query.q().isBlank()) {
						b.must(m -> m.multiMatch(mm -> mm
								.query(query.q())
								.fields("title^2", "description", "skills", "city")));
					}
					else {
						b.must(m -> m.matchAll(ma -> ma));
					}
					return b;
				})), Map.class);

		List<Long> ids = new ArrayList<>();
		for (Hit<Map> hit : resp.hits().hits()) {
			Map<?, ?> src = hit.source();
			if (src != null && src.get("id") != null) {
				ids.add(((Number) src.get("id")).longValue());
			}
			else if (hit.id() != null) {
				ids.add(Long.parseLong(hit.id()));
			}
		}
		return ids;
	}

	private List<Long> vectorIds(SearchQuery query, int window) {
		if (query.q() == null || query.q().isBlank()) {
			return List.of();
		}
		String vec = embeddingService.toVectorLiteral(query.q());
		if (vec == null) {
			return List.of();
		}
		if (query.hasGeo()) {
			double radiusM = (query.radiusKm() != null ? query.radiusKm() : 10) * 1000.0;
			return jobRepo.findIdsBySimilarityWithinRadius(vec, query.lat(), query.lng(), radiusM, window);
		}
		return jobRepo.findIdsBySimilarity(vec, window);
	}

	private static SearchResultItem toItem(Job job, Double distanceKm) {
		String company = job.getEmployer() == null ? null : job.getEmployer().getCompanyName();
		return new SearchResultItem(
				job.getId(), job.getTitle(), job.getSlug(),
				job.getCategory() == null ? null : job.getCategory().name(),
				job.getCity(), company, job.getSalaryMin(), job.getSalaryMax(), distanceKm);
	}
}
