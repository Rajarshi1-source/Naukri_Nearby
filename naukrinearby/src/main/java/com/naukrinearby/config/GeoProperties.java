package com.naukrinearby.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naukri.geo")
public record GeoProperties(
		double minLat,
		double maxLat,
		double minLng,
		double maxLng,
		int maxRadiusKm) {

	public GeoProperties {
		if (maxRadiusKm <= 0) {
			maxRadiusKm = 100;
		}
	}
}
