package com.naukrinearby;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.naukrinearby.repository.ApplicationRepository;
import com.naukrinearby.repository.UserRepository;
import com.naukrinearby.worker.OutboxSyncWorker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage of the Bharat differentiators (ADRs 0007–0009) over HTTP: fraud scoring and
 * moderation, reports, admin review, trust levels, Hinglish search, skill gap, DPDP consent and export,
 * language preference, and the IVR call flow. Real Postgres, Elasticsearch and Redis via Testcontainers.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DifferentiatorsIntegrationTests {

	private static final AtomicInteger SEQ = new AtomicInteger(0);
	/** Listed in application-test.properties as naukri.admin.phones. */
	private static final String ADMIN_PHONE = "9630099999";
	private static String adminToken;

	@Autowired OutboxSyncWorker outboxSyncWorker;
	@Autowired ElasticsearchClient es;
	@Autowired UserRepository userRepo;
	@Autowired ApplicationRepository applicationRepo;

	@LocalServerPort int port;

	// ---------------------------------------------------------------- fraud scoring + moderation

	@Test
	@SuppressWarnings("unchecked")
	void scamPosting_isHeldForReview_hiddenFromPublic_andApprovedByAdmin() throws Exception {
		RestTestClient client = client();
		String employer = token(client, nextPhone(), "EMPLOYER");
		Map<String, Object> body = jobBody("Data Entry Work",
				"Ghar baithe earn daily. Pay registration fee Rs 500 to join.");
		Map<String, Object> created = postJson(client, employer, "/api/jobs", body);

		assertThat(created.get("moderationStatus")).isEqualTo("PENDING_REVIEW");
		assertThat((List<String>) created.get("riskReasons")).contains("PAYMENT_REQUEST", "GET_RICH_QUICK");
		Number jobId = (Number) created.get("id");
		String slug = (String) created.get("slug");

		// Hidden from the public, from candidates applying, and from the index.
		client.get().uri("/api/jobs/" + slug).exchange().expectStatus().isNotFound();
		String candidate = token(client, nextPhone(), "CANDIDATE");
		client.post().uri("/api/jobs/" + jobId + "/apply")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + candidate)
				.contentType(MediaType.APPLICATION_JSON).body(Map.of())
				.exchange().expectStatus().isNotFound();
		outboxSyncWorker.syncBatch();
		assertThat(indexed(jobId)).isFalse();

		// The owner still sees it, with the reasons.
		client.get().uri("/api/jobs/" + slug)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + employer)
				.exchange().expectStatus().isOk()
				.expectBody().jsonPath("$.moderationStatus").isEqualTo("PENDING_REVIEW");

		// Candidates and employers cannot reach the admin queue.
		client.get().uri("/api/admin/moderation/jobs")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + candidate)
				.exchange().expectStatus().isForbidden();

		String admin = adminToken(client);
		client.get().uri("/api/admin/moderation/jobs?size=100")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.exchange().expectStatus().isOk()
				.expectBody().jsonPath("$.content[?(@.job.id == " + jobId + ")].job.riskScore").exists();

		postJson(client, admin, "/api/admin/moderation/jobs/" + jobId + "/decision",
				Map.of("decision", "APPROVED", "note", "Checked with employer"));

		client.get().uri("/api/jobs/" + slug).exchange().expectStatus().isOk()
				.expectBody()
				.jsonPath("$.moderationStatus").doesNotExist()
				.jsonPath("$.riskScore").doesNotExist();
		outboxSyncWorker.syncBatch();
		assertThat(indexed(jobId)).isTrue();
	}

	@Test
	void ordinaryPosting_isApproved_andCarriesTrustSignals() {
		RestTestClient client = client();
		String employer = token(client, nextPhone(), "EMPLOYER");
		Map<String, Object> created = postJson(client, employer, "/api/jobs",
				jobBody("Delivery Partner", "Deliver groceries in Lucknow on your own bike."));

		assertThat(created.get("moderationStatus")).isEqualTo("APPROVED");
		assertThat(created.get("trustLevel")).isEqualTo("PHONE_VERIFIED");
		assertThat(created.get("employerSince")).isNotNull();

		client.get().uri("/api/jobs/" + created.get("slug")).exchange().expectStatus().isOk()
				.expectBody()
				.jsonPath("$.trustLevel").isEqualTo("PHONE_VERIFIED")
				.jsonPath("$.riskReasons").doesNotExist();
	}

	@Test
	void reports_areOncePerUser_andEscalateAtThreshold() {
		RestTestClient client = client();
		String employer = token(client, nextPhone(), "EMPLOYER");
		Map<String, Object> created = postJson(client, employer, "/api/jobs",
				jobBody("Shop Helper", "Help at a kirana store in Aminabad."));
		Number jobId = (Number) created.get("id");

		// Employers cannot report their own job.
		client.post().uri("/api/jobs/" + jobId + "/report")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + employer)
				.contentType(MediaType.APPLICATION_JSON).body(Map.of("reason", "FAKE_JOB"))
				.exchange().expectStatus().isBadRequest();
		// Anonymous users cannot report.
		client.post().uri("/api/jobs/" + jobId + "/report")
				.contentType(MediaType.APPLICATION_JSON).body(Map.of("reason", "FAKE_JOB"))
				.exchange().expectStatus().isUnauthorized();

		String first = token(client, nextPhone(), "CANDIDATE");
		assertThat(report(client, first, jobId).get("underReview")).isEqualTo(false);
		client.post().uri("/api/jobs/" + jobId + "/report")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + first)
				.contentType(MediaType.APPLICATION_JSON).body(Map.of("reason", "FAKE_JOB"))
				.exchange().expectStatus().isEqualTo(409);

		assertThat(report(client, token(client, nextPhone(), "CANDIDATE"), jobId).get("underReview")).isEqualTo(false);
		assertThat(report(client, token(client, nextPhone(), "CANDIDATE"), jobId).get("underReview")).isEqualTo(true);

		client.get().uri("/api/jobs/" + created.get("slug")).exchange().expectStatus().isNotFound();
	}

	@Test
	void employerProfile_validGstin_makesJobsBusinessVerified() {
		RestTestClient client = client();
		String employer = token(client, nextPhone(), "EMPLOYER");
		Map<String, Object> created = postJson(client, employer, "/api/jobs",
				jobBody("Accountant", "Maintain books in Tally for a textile shop."));

		client.put().uri("/api/employer/profile")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + employer)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("companyName", "Sharma Textiles", "gstNumber", "27AAPFU0939F1ZW"))
				.exchange().expectStatus().isBadRequest();

		client.put().uri("/api/employer/profile")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + employer)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("companyName", "Sharma Textiles", "gstNumber", "27aapfu0939f1zv", "city", "Lucknow"))
				.exchange().expectStatus().isOk()
				.expectBody()
				.jsonPath("$.gstVerified").isEqualTo(true)
				.jsonPath("$.gstNumber").isEqualTo("27AAPFU0939F1ZV")
				.jsonPath("$.trustLevel").isEqualTo("BUSINESS_VERIFIED");

		client.get().uri("/api/jobs/" + created.get("slug")).exchange().expectStatus().isOk()
				.expectBody()
				.jsonPath("$.trustLevel").isEqualTo("BUSINESS_VERIFIED")
				.jsonPath("$.companyName").isEqualTo("Sharma Textiles");
	}

	// ---------------------------------------------------------------- vernacular search + skill gap

	@Test
	void hinglishQuery_findsEnglishJob() throws Exception {
		RestTestClient client = client();
		String employer = token(client, nextPhone(), "EMPLOYER");
		Map<String, Object> body = jobBody("Electrician", "House wiring and repair work.");
		body.put("lat", 25.4358);
		body.put("lng", 81.8463);
		body.put("city", "Prayagraj");
		Number jobId = (Number) postJson(client, employer, "/api/jobs", body).get("id");
		outboxSyncWorker.syncBatch();
		es.indices().refresh(r -> r.index("jobs"));

		client.get().uri("/api/jobs/search?q=bijli mistri ka kaam chahiye&lat=25.4358&lng=81.8463&radius=5")
				.exchange().expectStatus().isOk()
				.expectBody()
				.jsonPath("$.results[?(@.id == " + jobId + ")].trustLevel").isEqualTo(List.of("PHONE_VERIFIED"));
	}

	@Test
	void skillGap_listsMissingSkills_withOfficialPortals() {
		RestTestClient client = client();
		String employer = token(client, nextPhone(), "EMPLOYER");
		Map<String, Object> body = jobBody("Driver cum Accountant", "Drive the van and keep accounts.");
		body.put("skillsRequired", List.of("Driving", "Tally"));
		Number jobId = (Number) postJson(client, employer, "/api/jobs", body).get("id");

		String candidate = token(client, nextPhone(), "CANDIDATE");
		client.get().uri("/api/candidate/jobs/" + jobId + "/skill-gap")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + candidate)
				.exchange().expectStatus().isOk()
				.expectBody()
				.jsonPath("$.missing.length()").isEqualTo(2)
				.jsonPath("$.matchPercent").isEqualTo(0)
				.jsonPath("$.suggestions[1].portals[0].id").isEqualTo("swayam");
	}

	// ---------------------------------------------------------------- DPDP consent + export, language

	@Test
	void consentLedger_andDataExport() {
		RestTestClient client = client();
		String phone = nextPhone();
		String candidate = token(client, phone, "CANDIDATE");

		client.get().uri("/api/candidate/consents")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + candidate)
				.exchange().expectStatus().isOk()
				.expectBody()
				.jsonPath("$.length()").isEqualTo(2)
				.jsonPath("$[1].purpose").isEqualTo("WHATSAPP_ALERTS")
				.jsonPath("$[1].granted").doesNotExist();

		client.put().uri("/api/candidate/consents")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + candidate)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("purpose", "WHATSAPP_ALERTS", "granted", false))
				.exchange().expectStatus().isOk()
				.expectBody().jsonPath("$.granted").isEqualTo(false);

		client.get().uri("/api/candidate/account/export")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + candidate)
				.exchange().expectStatus().isOk()
				.expectHeader().valueMatches(HttpHeaders.CONTENT_DISPOSITION, "attachment;.*\\.json\"")
				.expectBody()
				.jsonPath("$.account.phone").isEqualTo(phone)
				.jsonPath("$.consents[0].purpose").isEqualTo("WHATSAPP_ALERTS")
				.jsonPath("$.consents[0].granted").isEqualTo(false)
				.jsonPath("$.applications").isArray();
	}

	@Test
	void languagePreference_isStoredOnTheUser() {
		RestTestClient client = client();
		String candidate = token(client, nextPhone(), "CANDIDATE");

		client.put().uri("/api/auth/me/language")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + candidate)
				.contentType(MediaType.APPLICATION_JSON).body(Map.of("language", "hi"))
				.exchange().expectStatus().isNoContent();
		client.put().uri("/api/auth/me/language")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + candidate)
				.contentType(MediaType.APPLICATION_JSON).body(Map.of("language", "xx"))
				.exchange().expectStatus().isBadRequest();

		client.get().uri("/api/auth/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + candidate)
				.exchange().expectStatus().isOk()
				.expectBody().jsonPath("$.preferredLanguage").isEqualTo("hi");
	}

	// ---------------------------------------------------------------- IVR

	@Test
	void ivrCall_appliesByJobNumber_andIsIdempotent() {
		RestTestClient client = client();
		String employer = token(client, nextPhone(), "EMPLOYER");
		Map<String, Object> created = postJson(client, employer, "/api/jobs",
				jobBody("Security Guard", "Night shift at a warehouse."));
		Number jobId = (Number) created.get("id");
		String callerPhone = nextPhone();
		String callSid = "CA" + System.nanoTime();

		String greeting = voice(client, "/api/webhooks/twilio/voice", callSid, callerPhone, null);
		assertThat(greeting).contains("<Gather").contains("नौकरी नियरबाय").contains("Welcome to Naukri Nearby");

		assertThat(voice(client, "/api/webhooks/twilio/voice/gather/menu", callSid, callerPhone, "1"))
				.contains("action=\"/api/webhooks/twilio/voice/gather/job\"");
		assertThat(voice(client, "/api/webhooks/twilio/voice/gather/job", callSid, callerPhone, jobId + "#"))
				.contains("Security Guard").contains("Press 1 to apply");
		assertThat(voice(client, "/api/webhooks/twilio/voice/gather/confirm", callSid, callerPhone, "1"))
				.contains("Your application has been sent").contains("<Hangup/>");

		Long candidateId = userRepo.findByPhone(callerPhone).orElseThrow().getId();
		assertThat(applicationRepo.findByJobIdAndCandidateId(jobId.longValue(), candidateId)).isPresent();

		// A second call for the same job is answered politely, not with a second application.
		String second = "CA" + System.nanoTime();
		voice(client, "/api/webhooks/twilio/voice", second, callerPhone, null);
		voice(client, "/api/webhooks/twilio/voice/gather/menu", second, callerPhone, "1");
		voice(client, "/api/webhooks/twilio/voice/gather/job", second, callerPhone, String.valueOf(jobId));
		assertThat(voice(client, "/api/webhooks/twilio/voice/gather/confirm", second, callerPhone, "1"))
				.contains("already applied");
	}

	@Test
	void ivr_unknownJobNumber_andExpiredCall_areHandled() {
		RestTestClient client = client();
		String callSid = "CA" + System.nanoTime();
		String phone = nextPhone();
		voice(client, "/api/webhooks/twilio/voice", callSid, phone, null);
		assertThat(voice(client, "/api/webhooks/twilio/voice/gather/job", callSid, phone, "999999999"))
				.contains("could not find that job number");
		// A gather for a call we never saw restarts the call instead of failing.
		assertThat(voice(client, "/api/webhooks/twilio/voice/gather/menu", "CA-unknown", phone, "1"))
				.contains("<Redirect method=\"POST\">/api/webhooks/twilio/voice</Redirect>");
	}

	// ---------------------------------------------------------------- helpers

	private boolean indexed(Number jobId) throws Exception {
		return es.get(g -> g.index("jobs").id(String.valueOf(jobId)), Map.class).found();
	}

	private Map<String, Object> report(RestTestClient client, String token, Number jobId) {
		return postJson(client, token, "/api/jobs/" + jobId + "/report",
				Map.of("reason", "ASKS_FOR_PAYMENT", "details", "Asked me to pay"));
	}

	private String voice(RestTestClient client, String uri, String callSid, String phone, String digits) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("CallSid", callSid);
		form.add("From", "+91" + phone);
		if (digits != null) {
			form.add("Digits", digits);
		}
		return client.post().uri(uri)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.exchange()
				.expectStatus().isOk()
				.expectBody(String.class)
				.returnResult().getResponseBody();
	}

	private RestTestClient client() {
		return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
	}

	private synchronized String adminToken(RestTestClient client) {
		if (adminToken == null) {
			// OTP sends are rate-limited per phone, so the admin logs in once per test run.
			adminToken = token(client, ADMIN_PHONE, "CANDIDATE");
		}
		return adminToken;
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
		return "96300" + String.format("%05d", SEQ.incrementAndGet());
	}
}
