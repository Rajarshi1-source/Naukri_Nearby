package com.naukrinearby.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naukri.geo")
public record GeoProperties(
		double minLat,
		double maxLat,
		double minLng,
		double maxLng,
		int maxRadiusKm,
		String geocoder,
		String geocoderBaseUrl) {

	public GeoProperties {
		if (maxRadiusKm <= 0) {
			maxRadiusKm = 100;
		}
		if (geocoder == null || geocoder.isBlank()) {
			geocoder = "none";
		}
		if (geocoderBaseUrl == null || geocoderBaseUrl.isBlank()) {
			geocoderBaseUrl = "https://nominatim.openstreetmap.org";
		}
	}
}
