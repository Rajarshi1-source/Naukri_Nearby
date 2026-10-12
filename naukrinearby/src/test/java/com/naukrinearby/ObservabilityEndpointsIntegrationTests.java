package com.naukrinearby;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prometheus can scrape {@code /actuator/prometheus} anonymously on the internal management port,
 * while every other actuator endpoint stays ADMIN-only and the public API port exposes no actuator
 * at all.
 */
// Boot tests disable metrics export by default; re-enable it so the Prometheus endpoint exists as in prod.
@AutoConfigureMetrics
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ObservabilityEndpointsIntegrationTests {

	private static final AtomicInteger SEQ = new AtomicInteger(0);

	@LocalServerPort int serverPort;
	@LocalManagementPort int managementPort;

	private RestTestClient api() {
		return RestTestClient.bindToServer().baseUrl("http://localhost:" + serverPort).build();
	}

	private RestTestClient management() {
		return RestTestClient.bindToServer().baseUrl("http://localhost:" + managementPort).build();
	}

	@Test
	void prometheus_isScrapeableAnonymously_onManagementPort_withCustomMetrics() {
		assertThat(managementPort).isNotEqualTo(serverPort);
		// Record a search so the custom timer exists in the registry.
		api().get().uri("/api/jobs/search?lat=26.85&lng=80.95&radius=5").exchange().expectStatus().isOk();

		String scrape = management().get().uri("/actuator/prometheus")
				.exchange()
				.expectStatus().isOk()
				.expectBody(String.class)
				.returnResult().getResponseBody();

		assertThat(scrape).contains("search_duration_seconds_count").contains("jvm_memory_used_bytes");
	}

	@Test
	void otherActuatorEndpoints_stayAdminOnly_onManagementPort() {
		for (String endpoint : new String[] { "/actuator/metrics", "/actuator/circuitbreakers", "/actuator/retries" }) {
			management().get().uri(endpoint).exchange().expectStatus().isUnauthorized();
		}
		String candidateToken = token(nextPhone(), "CANDIDATE");
		management().get().uri("/actuator/circuitbreakers")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + candidateToken)
				.exchange()
				.expectStatus().isForbidden();
		// Health stays public for probes.
		management().get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();
	}

	@Test
	void publicApiPort_neverServesTheScrapeAnonymously() {
		api().get().uri("/actuator/prometheus").exchange().expectStatus().isUnauthorized();
		// Even an authenticated caller gets nothing: actuator is not mapped on the public port.
		api().get().uri("/actuator/prometheus")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(nextPhone(), "CANDIDATE"))
				.exchange()
				.expectStatus().isForbidden();
		// Actuator isn't mapped on the public port: an unmapped path is a 404, never a 500.
		api().get().uri("/actuator/health").exchange().expectStatus().isNotFound();
	}

	private String token(String phone, String role) {
		RestTestClient client = api();
		client.post().uri("/api/auth/send-otp")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("phone", phone))
				.exchange()
				.expectStatus().isOk();
		Map<?, ?> body = client.post().uri("/api/auth/verify-otp")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("phone", phone, "code", "123456", "role", role))
				.exchange()
				.expectStatus().isOk()
				.expectBody(Map.class)
				.returnResult().getResponseBody();
		return (String) body.get("accessToken");
	}

	private static String nextPhone() {
		return "97700" + String.format("%05d", SEQ.incrementAndGet());
	}
}
