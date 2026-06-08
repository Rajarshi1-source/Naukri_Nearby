package com.naukrinearby;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiEndpointsIntegrationTests {

	private static final AtomicInteger SEQ = new AtomicInteger(0);

	@LocalServerPort int port;

	private RestTestClient client() {
		return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
	}

	@Test
	void candidate_updateProfile_andDashboardStats() {
		RestTestClient client = client();
		String token = token(client, nextPhone(), "CANDIDATE");

		client.put().uri("/api/candidate/profile")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("name", "Sita Devi", "city", "Lucknow"))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.name").isEqualTo("Sita Devi")
				.jsonPath("$.city").isEqualTo("Lucknow");

		client.get().uri("/api/candidate/dashboard/stats")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.totalApplications").isEqualTo(0);
	}

	@Test
	void employer_updatesApplicationStatus() {
		RestTestClient client = client();
		String employerToken = token(client, nextPhone(), "EMPLOYER");
		Number jobId = (Number) postJson(client, employerToken, "/api/jobs", jobBody()).get("id");

		String candidateToken = token(client, nextPhone(), "CANDIDATE");
		Number appId = (Number) postJson(client, candidateToken,
				"/api/jobs/" + jobId + "/apply", Map.of("coverNote", "Keen to join")).get("id");

		client.method(HttpMethod.PATCH).uri("/api/applications/" + appId + "/status")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + employerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("status", "VIEWED"))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("VIEWED");
	}

	private Map<String, Object> jobBody() {
		Map<String, Object> body = new HashMap<>();
		body.put("title", "Driver Needed");
		body.put("description", "Drive a delivery van around the city");
		body.put("category", "DRIVER");
		body.put("skillsRequired", List.of("Driving"));
		body.put("salaryMin", 12000);
		body.put("salaryMax", 20000);
		body.put("salaryType", "MONTHLY");
		body.put("experienceRequiredMonths", 0);
		body.put("lat", 26.8467);
		body.put("lng", 80.9462);
		body.put("address", "Hazratganj");
		body.put("city", "Lucknow");
		body.put("state", "Uttar Pradesh");
		body.put("pincode", "226001");
		body.put("radiusKm", 10);
		return body;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> postJson(RestTestClient client, String token, String uri, Object body) {
		return client.post().uri(uri)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectBody(Map.class)
				.returnResult()
				.getResponseBody();
	}

	@SuppressWarnings("unchecked")
	private String token(RestTestClient client, String phone, String role) {
		client.post().uri("/api/auth/send-otp")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("phone", phone))
				.exchange()
				.expectStatus().isOk();
		Map<String, Object> body = client.post().uri("/api/auth/verify-otp")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("phone", phone, "code", "123456", "role", role))
				.exchange()
				.expectStatus().isOk()
				.expectBody(Map.class)
				.returnResult()
				.getResponseBody();
		return (String) body.get("accessToken");
	}

	private static String nextPhone() {
		return "97600" + String.format("%05d", SEQ.incrementAndGet());
	}
}
