package com.naukrinearby.model.dto.search;

import java.util.List;

public record SearchResponse(
		List<SearchResultItem> results,
		int total,
		String source,
		boolean fellBackToPostgis) {
}
