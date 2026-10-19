package com.naukrinearby;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.naukrinearby.util.WavFixtures;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.MultiValueMap;

/**
 * Bhashini speech endpoints over HTTP (ADR 0009) with the default stub provider: the voice-search
 * transcription upload (validation, anonymous access, per-caller rate limit) and the job "Listen" audio
 * (public jobs only). Real Postgres, Elasticsearch and Redis via Testcontainers.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpeechIntegrationTests {

	private static final AtomicInteger SEQ = new AtomicInteger(0);

	@LocalServerPort int port;

	// ---------------------------------------------------------------- transcribe

	@Test
	void transcribe_isAnonymous_andTheStubHearsNothing() {
		client().post().uri("/api/speech/transcribe")
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(multipart(WavFixtures.voiceSearchClip(1), "hi"))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.text").isEqualTo("")
				.jsonPath("$.language").isEqualTo("hi")
				.jsonPath("$.provider").isEqualTo("stub");
	}

	@Test
	void transcribe_validatesLanguageFormatSizeAndPresence() {
		RestTestClient client = client();
		expectBadRequest(client, multipart(WavFixtures.voiceSearchClip(1), "xx"));
		expectBadRequest(client, multipart(WavFixtures.voiceSearchClip(1), null));
		expectBadRequest(client, multipart("not audio".getBytes(), "hi"));
		expectBadRequest(client, multipart(WavFixtures.silence(44_100, 2, 16, 0.5), "hi"));
		expectBadRequest(client, multipart(WavFixtures.voiceSearchClip(33), "hi")); // over 1 MB

		MultipartBodyBuilder noAudio = new MultipartBodyBuilder();
		noAudio.part("lang", "hi");
		expectBadRequest(client, noAudio.build());

		client.post().uri("/api/speech/transcribe")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("lang", "hi"))
				.exchange()
				.expectStatus().isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
	}

	@Test
	void transcribe_isRateLimitedPerCaller() {
		RestTestClient client = client();
		String token = token(client, nextPhone(), "CANDIDATE");
		for (int i = 0; i < 10; i++) {
			transcribeAs(client, token).expectStatus().isOk();
		}
		transcribeAs(client, token).expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
	}

	// ---------------------------------------------------------------- job audio

	@Test
	void jobAudio_stubHasNoAudio_validatesLanguage_andHidesNonPublicJobs() {
		RestTestClient client = client();
		String employer = token(client, nextPhone(), "EMPLOYER");
		Map<String, Object> live = postJson(client, employer, "/api/jobs",
				jobBody("Tailor", "Stitching and alterations at a boutique."));

		client.get().uri("/api/jobs/" + live.get("slug") + "/audio?lang=hi")
				.exchange()
				.expectStatus().isNoContent()
				.expectHeader().valueEquals("X-Speech-Provider", "stub");
		client.get().uri("/api/jobs/" + live.get("slug") + "/audio?lang=xx")
				.exchange().expectStatus().isBadRequest();
		client.get().uri("/api/jobs/" + live.get("slug") + "/audio")
				.exchange().expectStatus().isBadRequest();
		client.get().uri("/api/jobs/no-such-job/audio?lang=hi")
				.exchange().expectStatus().isNotFound();

		// Held for review (fraud signals): not public, so no audio.
		Map<String, Object> pending = postJson(client, employer, "/api/jobs",
				jobBody("Helper", "Pay registration fee Rs 500. Ghar baithe earn daily."));
		client.get().uri("/api/jobs/" + pending.get("slug") + "/audio?lang=hi")
				.exchange().expectStatus().isNotFound();

		// Approved but closed: not public either.
		client.patch().uri("/api/jobs/" + live.get("id") + "/status")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + employer)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("status", "CLOSED"))
				.exchange().expectStatus().isOk();
		client.get().uri("/api/jobs/" + live.get("slug") + "/audio?lang=hi")
				.exchange().expectStatus().isNotFound();
	}

	// ---------------------------------------------------------------- helpers

	private RestTestClient.ResponseSpec transcribeAs(RestTestClient client, String token) {
		return client.post().uri("/api/speech/transcribe")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(multipart(WavFixtures.voiceSearchClip(0.5), "en"))
				.exchange();
	}

	private static void expectBadRequest(RestTestClient client, MultiValueMap<String, ?> body) {
		client.post().uri("/api/speech/transcribe")
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(body)
				.exchange()
				.expectStatus().isBadRequest();
	}

	private static MultiValueMap<String, ?> multipart(byte[] audio, String lang) {
		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.part("audio", new ByteArrayResource(audio) {
			@Override
			public String getFilename() {
				return "voice.wav";
			}
		}).contentType(MediaType.parseMediaType("audio/wav"));
		if (lang != null) {
			builder.part("lang", lang);
		}
		return builder.build();
	}

	private RestTestClient client() {
		return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
	}

	private static Map<String, Object> jobBody(String title, String description) {
		Map<String, Object> body = new HashMap<>();
		body.put("title", title);
		body.put("description", description);
		body.put("category", "OTHER");
		body.put("skillsRequired", List.of("Hard work"));
		body.put("salaryMin", 10000);
		body.put("salaryMax", 15000);
		body.put("salaryType", "MONTHLY");
		body.put("experienceRequiredMonths", 0);
		body.put("lat", 26.8467);
		body.put("lng", 80.9462);
		body.put("address", "Aminabad");
		body.put("city", "Lucknow");
		body.put("state", "Uttar Pradesh");
		body.put("pincode", "226018");
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
		return "96310" + String.format("%05d", SEQ.incrementAndGet());
	}
}
