package com.naukrinearby.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naukrinearby.config.SearchProperties;
import com.naukrinearby.model.dto.search.SearchQuery;
import com.naukrinearby.model.dto.search.SearchResponse;
import com.naukrinearby.model.dto.search.SearchResultItem;
import com.naukrinearby.model.entity.Job;
import com.naukrinearby.repository.JobRepository;
import com.naukrinearby.service.search.HybridSearchStrategy;
import com.naukrinearby.service.search.SearchRankingStrategy;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hyperlocal job search. Primary path is Elasticsearch (geo_distance + multi_match) with a short-TTL
 * Redis cache; if ES is unavailable it gracefully degrades to a PostGIS {@code ST_DWithin} query and
 * flags {@code fellBackToPostgis=true} (master plan §9.6 — availability over perfect relevance).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

	private final ElasticsearchClient es;
	private final JobRepository jobRepo;
	private final SearchRankingStrategy rankingStrategy;
	private final HybridSearchStrategy hybridSearchStrategy;
	private final SearchProperties props;
	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;
	private final MeterRegistry meterRegistry;

	public SearchResponse search(SearchQuery query) {
		Timer.Sample sample = Timer.start(meterRegistry);
		try {
			return doSearch(query);
		}
		finally {
			sample.stop(meterRegistry.timer("search_duration_seconds"));
		}
	}

	private SearchResponse doSearch(SearchQuery query) {
		String cacheKey = cacheKey(query);
		String cached = redis.opsForValue().get(cacheKey);
		if (cached != null) {
			try {
				return objectMapper.readValue(cached, SearchResponse.class);
			}
			catch (Exception ignored) {
				// fall through to a fresh search
			}
		}
		try {
			boolean useHybrid = props.hybridEnabled() && query.q() != null && !query.q().isBlank();
			SearchResponse result = useHybrid ? hybridSearchStrategy.search(query) : searchElasticsearch(query);
			redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), props.cacheTtl());
			return result;
		}
		catch (Exception ex) {
			log.warn("Elasticsearch search failed, falling back to PostGIS: {}", ex.getMessage());
			return searchPostgis(query);
		}
	}

	@SuppressWarnings("rawtypes")
	private SearchResponse searchElasticsearch(SearchQuery query) throws Exception {
		int size = query.size() > 0 ? query.size() : props.maxResults();
		var sorts = rankingStrategy.sorts(query);

		var resp = es.search(s -> s
				.index(props.index())
				.size(size)
				.sort(sorts)
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

		List<SearchResultItem> items = new ArrayList<>();
		for (Hit<Map> hit : resp.hits().hits()) {
			items.add(toItem(hit.source(), distanceOf(hit, query)));
		}
		TotalHits totalHits = resp.hits().total();
		long total = totalHits != null ? totalHits.value() : items.size();
		return new SearchResponse(items, (int) total, "elasticsearch", false);
	}

	@Transactional(readOnly = true)
	protected SearchResponse searchPostgis(SearchQuery query) {
		if (!query.hasGeo()) {
			return new SearchResponse(List.of(), 0, "postgis", true);
		}
		int size = query.size() > 0 ? query.size() : props.maxResults();
		double radiusM = (query.radiusKm() != null ? query.radiusKm() : 10) * 1000.0;
		List<Job> jobs = jobRepo.findWithinRadius(query.lat(), query.lng(), radiusM, size);
		List<SearchResultItem> items = jobs.stream()
				.map(j -> new SearchResultItem(
						j.getId(), j.getTitle(), j.getSlug(),
						j.getCategory() == null ? null : j.getCategory().name(),
						j.getCity(), null, j.getSalaryMin(), j.getSalaryMax(), null))
				.toList();
		return new SearchResponse(items, items.size(), "postgis", true);
	}

	@SuppressWarnings("rawtypes")
	private Double distanceOf(Hit<Map> hit, SearchQuery query) {
		if (!query.hasGeo() || hit.sort() == null || hit.sort().isEmpty()) {
			return null;
		}
		FieldValue fv = hit.sort().get(0);
		if (fv.isDouble()) {
			return fv.doubleValue();
		}
		if (fv.isLong()) {
			return (double) fv.longValue();
		}
		return null;
	}

	@SuppressWarnings("rawtypes")
	private SearchResultItem toItem(Map src, Double distanceKm) {
		if (src == null) {
			return null;
		}
		return new SearchResultItem(
				toLong(src.get("id")),
				str(src.get("title")),
				str(src.get("slug")),
				str(src.get("category")),
				str(src.get("city")),
				str(src.get("company_name")),
				toInt(src.get("salary_min")),
				toInt(src.get("salary_max")),
				distanceKm);
	}

	private static String str(Object o) {
		return o == null ? null : o.toString();
	}

	private static Long toLong(Object o) {
		if (o == null) {
			return null;
		}
		return (o instanceof Number n) ? n.longValue() : Long.parseLong(o.toString());
	}

	private static Integer toInt(Object o) {
		if (o == null) {
			return null;
		}
		return (o instanceof Number n) ? n.intValue() : Integer.parseInt(o.toString());
	}

	private String cacheKey(SearchQuery q) {
		return "search:" + String.join("|",
				nz(q.q()),
				q.lat() == null ? "" : String.format("%.3f", q.lat()),
				q.lng() == null ? "" : String.format("%.3f", q.lng()),
				q.radiusKm() == null ? "" : q.radiusKm().toString(),
				nz(q.category()),
				String.valueOf(q.size()));
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}
}
