package com.naukrinearby.model.dto.search;

public record SearchQuery(
		String q,
		Double lat,
		Double lng,
		Integer radiusKm,
		String category,
		int size) {

	public boolean hasGeo() {
		return lat != null && lng != null;
	}
}
