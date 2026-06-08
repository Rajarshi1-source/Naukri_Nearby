package com.naukrinearby.service.geocoding;

import java.util.Optional;

/**
 * Provider-agnostic forward geocoding (address -> coordinates). The MVP receives coordinates from the
 * map picker, so the default is a no-op; an OSM/Nominatim impl is selected via {@code naukri.geo.geocoder}.
 * Returns {@code [lat, lng]} when a match is found.
 */
public interface GeocodingProvider {

	Optional<double[]> geocode(String query);

	String providerId();
}
