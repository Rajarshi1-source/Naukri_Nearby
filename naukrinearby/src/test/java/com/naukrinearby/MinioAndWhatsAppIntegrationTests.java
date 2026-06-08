package com.naukrinearby;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.naukrinearby.model.dto.job.JobCreateRequest;
import com.naukrinearby.model.entity.Employer;
import com.naukrinearby.model.entity.User;
import com.naukrinearby.model.enums.JobCategory;
import com.naukrinearby.model.enums.UserRole;
import com.naukrinearby.model.entity.Job;
import com.naukrinearby.repository.ApplicationRepository;
import com.naukrinearby.repository.EmployerRepository;
import com.naukrinearby.repository.UserRepository;
import com.naukrinearby.service.FileStorageService;
import com.naukrinearby.service.JobService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MinioAndWhatsAppIntegrationTests {

	private static final AtomicInteger SEQ = new AtomicInteger(0);

	@Autowired FileStorageService storage;
	@Autowired JobService jobService;
	@Autowired UserRepository userRepo;
	@Autowired EmployerRepository employerRepo;
	@Autowired ApplicationRepository applicationRepo;

	@LocalServerPort int port;

	@Test
	void minio_uploadDownloadDelete_roundTrip() {
		byte[] content = "dummy resume bytes".getBytes(StandardCharsets.UTF_8);
		String key = storage.upload(content, "resume.txt", "text/plain");

		assertThat(storage.download(key)).isEqualTo(content);
		assertThat(storage.signedUrl(key)).contains(key);

		storage.delete(key);
		// Second delete on a missing object is a no-op (must not throw).
		storage.delete(key);
	}

	@Test
	void whatsAppFirstApply_buildsProfile_andCreatesApplication() {
		User employer = newEmployer();
		Job job = jobService.createJob(sampleJob(), employer.getId());
		String from = "whatsapp:+9198765" + String.format("%05d", SEQ.incrementAndGet());
		String phone = from.substring(from.length() - 10);

		RestTestClient client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
		inbound(client, from, "apply " + job.getId());
		inbound(client, from, "Ramesh Kumar");
		inbound(client, from, "Plumbing, pipe repair");
		inbound(client, from, "3 saal");

		User candidate = userRepo.findByPhone(phone).orElseThrow();
		assertThat(applicationRepo.findByJobIdAndCandidateId(job.getId(), candidate.getId())).isPresent();
	}

	private void inbound(RestTestClient client, String from, String body) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("From", from);
		form.add("Body", body);
		client.post().uri("/api/webhooks/twilio/inbound")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.exchange()
				.expectStatus().isOk();
	}

	private JobCreateRequest sampleJob() {
		return new JobCreateRequest(
				"Plumber Needed", "Pipe repair and fitting work",
				JobCategory.PLUMBING, List.of("Plumbing"),
				12000, 20000, "MONTHLY", 0,
				26.8467, 80.9462, "Hazratganj", "Lucknow", "Uttar Pradesh", "226001", 10);
	}

	private User newEmployer() {
		User u = new User("99911" + String.format("%05d", SEQ.incrementAndGet()), UserRole.EMPLOYER);
		u.setVerified(true);
		u = userRepo.save(u);
		Employer e = new Employer();
		e.setUserId(u.getId());
		e.setCompanyName("Acme Services");
		employerRepo.save(e);
		return u;
	}
}
