package com.naukrinearby.service;

import com.naukrinearby.config.GeoProperties;
import com.naukrinearby.exception.ValidationException;
import com.naukrinearby.util.GeoUtils;

import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;

/**
 * Validates coordinates against India's bounding box and builds a WGS84 point (mitigation §13:
 * reject geo-spoofed / out-of-range coordinates). A real geocoder (address -> lat/lng) can be
 * added here later; the MVP receives coordinates from the map picker.
 */
@Service
@RequiredArgsConstructor
public class GeocodingService {

	private final GeoProperties geo;

	public Point validateAndBuild(double lat, double lng) {
		if (lat < geo.minLat() || lat > geo.maxLat() || lng < geo.minLng() || lng > geo.maxLng()) {
			throw new ValidationException("Coordinates are outside India's supported region");
		}
		return GeoUtils.point(lat, lng);
	}

	public int clampRadiusKm(Integer radiusKm) {
		if (radiusKm == null || radiusKm <= 0) {
			return 10;
		}
		return Math.min(radiusKm, geo.maxRadiusKm());
	}
}
