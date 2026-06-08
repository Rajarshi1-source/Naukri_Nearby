package com.naukrinearby.service.search;

import java.util.ArrayList;
import java.util.List;

import co.elastic.clients.elasticsearch._types.DistanceUnit;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import com.naukrinearby.model.dto.search.SearchQuery;

import org.springframework.stereotype.Component;

/**
 * Default ranking: nearest-first when coordinates are present (hyperlocal intent), otherwise by
 * text relevance then recency.
 */
@Component
public class GeoRelevanceRankingStrategy implements SearchRankingStrategy {

	@Override
	public List<SortOptions> sorts(SearchQuery query) {
		List<SortOptions> sorts = new ArrayList<>();
		if (query.hasGeo()) {
			sorts.add(SortOptions.of(so -> so.geoDistance(g -> g
					.field("location")
					.location(loc -> loc.latlon(ll -> ll.lat(query.lat()).lon(query.lng())))
					.order(SortOrder.Asc)
					.unit(DistanceUnit.Kilometers))));
		}
		sorts.add(SortOptions.of(so -> so.score(sc -> sc.order(SortOrder.Desc))));
		sorts.add(SortOptions.of(so -> so.field(f -> f.field("created_at").order(SortOrder.Desc))));
		return sorts;
	}

	@Override
	public String id() {
		return "geo-relevance";
	}
}
