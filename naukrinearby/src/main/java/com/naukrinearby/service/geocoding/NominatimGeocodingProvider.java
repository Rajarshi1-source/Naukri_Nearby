package com.naukrinearby.service.geocoding;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.naukrinearby.config.GeoProperties;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Forward geocoding via OpenStreetMap Nominatim (free, good India coverage). Selected by
 * {@code naukri.geo.geocoder=nominatim}. Results are restricted to India ({@code countrycodes=in}).
 * Note: the public Nominatim service requires a descriptive User-Agent and rate-limits to ~1 req/s.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "naukri.geo.geocoder", havingValue = "nominatim")
public class NominatimGeocodingProvider implements GeocodingProvider {

	private final RestClient client;

	public NominatimGeocodingProvider(GeoProperties props) {
		var factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(5_000);
		factory.setReadTimeout(8_000);
		this.client = RestClient.builder()
				.baseUrl(props.geocoderBaseUrl())
				.requestFactory(factory)
				.defaultHeader("User-Agent", "NaukriNearby/1.0 (hyperlocal job board)")
				.build();
	}

	@Override
	public Optional<double[]> geocode(String query) {
		if (query == null || query.isBlank()) {
			return Optional.empty();
		}
		try {
			NominatimResult[] results = client.get()
					.uri(uriBuilder -> uriBuilder.path("/search")
							.queryParam("q", query)
							.queryParam("format", "json")
							.queryParam("limit", 1)
							.queryParam("countrycodes", "in")
							.build())
					.retrieve()
					.body(NominatimResult[].class);
			if (results == null || results.length == 0) {
				return Optional.empty();
			}
			NominatimResult top = results[0];
			return Optional.of(new double[] { Double.parseDouble(top.lat()), Double.parseDouble(top.lon()) });
		}
		catch (RuntimeException ex) {
			log.warn("Geocoding failed for '{}': {}", query, ex.getMessage());
			return Optional.empty();
		}
	}

	@Override
	public String providerId() {
		return "nominatim";
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record NominatimResult(String lat, String lon) {
	}
}
