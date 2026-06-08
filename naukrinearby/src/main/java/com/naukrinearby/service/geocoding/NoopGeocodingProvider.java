package com.naukrinearby.service.geocoding;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default no-op geocoder: forward geocoding is disabled and callers must supply map-picker
 * coordinates. Selected by {@code naukri.geo.geocoder=none} (the default).
 */
@Component
@ConditionalOnProperty(name = "naukri.geo.geocoder", havingValue = "none", matchIfMissing = true)
public class NoopGeocodingProvider implements GeocodingProvider {

	@Override
	public Optional<double[]> geocode(String query) {
		return Optional.empty();
	}

	@Override
	public String providerId() {
		return "none";
	}
}
