package com.naukrinearby;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.naukrinearby.exception.DuplicateApplicationException;
import com.naukrinearby.model.dto.job.JobCreateRequest;
import com.naukrinearby.model.entity.Employer;
import com.naukrinearby.model.entity.User;
import com.naukrinearby.model.enums.JobCategory;
import com.naukrinearby.model.enums.UserRole;
import com.naukrinearby.model.entity.Job;
import com.naukrinearby.repository.EmployerRepository;
import com.naukrinearby.repository.NotificationLogRepository;
import com.naukrinearby.repository.UserRepository;
import com.naukrinearby.service.ApplicationService;
import com.naukrinearby.service.JobService;
import com.naukrinearby.service.NotificationService;
import com.naukrinearby.worker.OutboxSyncWorker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendIntegrationTests {

	private static final AtomicInteger PHONE_SEQ = new AtomicInteger(0);

	@Autowired UserRepository userRepo;
	@Autowired EmployerRepository employerRepo;
	@Autowired JobService jobService;
	@Autowired OutboxSyncWorker outboxSyncWorker;
	@Autowired ElasticsearchClient es;
	@Autowired ApplicationService applicationService;
	@Autowired NotificationService notificationService;
	@Autowired NotificationLogRepository notificationLogRepo;

	@LocalServerPort int port;

	@Test
	@SuppressWarnings("unchecked")
	void createJob_writesOutbox_andSyncsToElasticsearch() throws Exception {
		User employer = newEmployer();
		Job job = jobService.createJob(sampleJob(), employer.getId());

		outboxSyncWorker.syncBatch();

		var resp = es.get(g -> g.index("jobs").id(String.valueOf(job.getId())), Map.class);
		assertThat(resp.found()).isTrue();
		assertThat(resp.source()).containsEntry("title", "Electrician Needed");
	}

	@Test
	void apply_isIdempotent_secondApplyConflicts() {
		User employer = newEmployer();
		Job job = jobService.createJob(sampleJob(), employer.getId());
		User candidate = newCandidate();

		applicationService.apply(job.getId(), candidate.getId(), "Keen to join");

		assertThatThrownBy(() -> applicationService.apply(job.getId(), candidate.getId(), "again"))
				.isInstanceOf(DuplicateApplicationException.class);
	}

	@Test
	void notification_process_isDedupedPerCandidateJob() {
		User employer = newEmployer();
		Job job = jobService.createJob(sampleJob(), employer.getId());
		User candidate = newCandidate();

		Map<String, String> fields = new HashMap<>();
		fields.put("jobId", String.valueOf(job.getId()));
		fields.put("userId", String.valueOf(candidate.getId()));
		fields.put("phone", "9876500000");
		fields.put("lang", "en");

		assertThat(notificationService.process(new HashMap<>(fields))).isTrue();
		assertThat(notificationService.process(new HashMap<>(fields))).isTrue();

		long logs = notificationLogRepo.findAll().stream()
				.filter(l -> l.getCandidateId().equals(candidate.getId()) && l.getJobId().equals(job.getId()))
				.count();
		assertThat(logs).isEqualTo(1);
	}

	@Test
	void authFlow_otpToJwt_andMe() {
		RestTestClient client = RestTestClient.bindToServer()
				.baseUrl("http://localhost:" + port)
				.build();
		String phone = "98700" + String.format("%05d", PHONE_SEQ.incrementAndGet());

		client.post().uri("/api/auth/send-otp")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("phone", phone))
				.exchange()
				.expectStatus().isOk();

		client.post().uri("/api/auth/verify-otp")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("phone", phone, "code", "123456", "role", "CANDIDATE"))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.accessToken").exists()
				.jsonPath("$.role").isEqualTo("CANDIDATE");
	}

	private JobCreateRequest sampleJob() {
		return new JobCreateRequest(
				"Electrician Needed", "Wiring and motor repair work",
				JobCategory.ELECTRICAL, List.of("Electrical Wiring", "Motor Repair"),
				12000, 20000, "MONTHLY", 0,
				26.8467, 80.9462, "Hazratganj", "Lucknow", "Uttar Pradesh", "226001", 10);
	}

	private User newEmployer() {
		User u = userRepo.save(verified(new User(nextPhone(), UserRole.EMPLOYER)));
		Employer e = new Employer();
		e.setUserId(u.getId());
		e.setCompanyName("Acme Services");
		employerRepo.save(e);
		return u;
	}

	private User newCandidate() {
		return userRepo.save(verified(new User(nextPhone(), UserRole.CANDIDATE)));
	}

	private static User verified(User u) {
		u.setVerified(true);
		return u;
	}

	private static String nextPhone() {
		return "99900" + String.format("%05d", PHONE_SEQ.incrementAndGet());
	}
}
