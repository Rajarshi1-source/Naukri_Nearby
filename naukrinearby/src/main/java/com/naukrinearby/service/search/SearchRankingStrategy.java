package com.naukrinearby.service.search;

import java.util.List;

import co.elastic.clients.elasticsearch._types.SortOptions;
import com.naukrinearby.model.dto.search.SearchQuery;

/** Strategy pattern (master plan §7.4): pluggable result ordering for job search. */
public interface SearchRankingStrategy {

	List<SortOptions> sorts(SearchQuery query);

	String id();
}
