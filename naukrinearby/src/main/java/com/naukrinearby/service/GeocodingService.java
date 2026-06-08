package com.naukrinearby.service;

import com.naukrinearby.config.GeoProperties;
import com.naukrinearby.exception.ValidationException;
import com.naukrinearby.service.geocoding.GeocodingProvider;
import com.naukrinearby.util.GeoUtils;

import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;

/**
 * Validates coordinates against India's bounding box and builds a WGS84 point (mitigation §13:
 * reject geo-spoofed / out-of-range coordinates). Forward geocoding (address -> lat/lng) is delegated
 * to a configurable {@link GeocodingProvider} (no-op by default); the MVP path receives coordinates
 * from the map picker.
 */
@Service
@RequiredArgsConstructor
public class GeocodingService {

	private final GeoProperties geo;
	private final GeocodingProvider geocodingProvider;

	public Point validateAndBuild(double lat, double lng) {
		if (lat < geo.minLat() || lat > geo.maxLat() || lng < geo.minLng() || lng > geo.maxLng()) {
			throw new ValidationException("Coordinates are outside India's supported region");
		}
		return GeoUtils.point(lat, lng);
	}

	/**
	 * Geocodes a free-text address to a validated India point. Throws if no geocoder is configured or
	 * the address can't be resolved within India's bounds. Callers with map-picker coordinates should
	 * use {@link #validateAndBuild(double, double)} instead.
	 */
	public Point geocodeToPoint(String address) {
		double[] latLng = geocodingProvider.geocode(address)
				.orElseThrow(() -> new ValidationException("Could not geocode address: " + address));
		return validateAndBuild(latLng[0], latLng[1]);
	}

	public int clampRadiusKm(Integer radiusKm) {
		if (radiusKm == null || radiusKm <= 0) {
			return 10;
		}
		return Math.min(radiusKm, geo.maxRadiusKm());
	}
}
