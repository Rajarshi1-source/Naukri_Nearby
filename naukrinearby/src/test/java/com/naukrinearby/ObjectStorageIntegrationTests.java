package com.naukrinearby;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import com.naukrinearby.config.StorageProperties;
import com.naukrinearby.service.FileStorageService;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract tests for the S3-compatible resume store (RustFS, ADR 0006) through the app's real
 * {@link FileStorageService} + MinIO SDK path: erasure, signed-URL access, the 10 MB upload ceiling,
 * and SSE-S3 encryption at rest (DPDP).
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ObjectStorageIntegrationTests {

	private final HttpClient http = HttpClient.newHttpClient();

	@Autowired FileStorageService storage;
	@Autowired MinioClient minio;
	@Autowired StorageProperties props;

	@Test
	void delete_makesResumeUnreadable() {
		String key = storage.upload("resume".getBytes(StandardCharsets.UTF_8), "cv.pdf", "application/pdf");

		storage.delete(key);

		assertThatThrownBy(() -> storage.download(key)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void signedUrl_servesTheObject_andRejectsATamperedSignature() throws Exception {
		byte[] content = "signed resume".getBytes(StandardCharsets.UTF_8);
		String key = storage.upload(content, "cv.pdf", "application/pdf");
		String url = storage.signedUrl(key);

		HttpResponse<byte[]> ok = http.send(HttpRequest.newBuilder(URI.create(url)).build(),
				HttpResponse.BodyHandlers.ofByteArray());
		HttpResponse<byte[]> tampered = http.send(
				HttpRequest.newBuilder(URI.create(url.replace("X-Amz-Signature=", "X-Amz-Signature=00"))).build(),
				HttpResponse.BodyHandlers.ofByteArray());

		assertThat(ok.statusCode()).isEqualTo(200);
		assertThat(ok.body()).isEqualTo(content);
		assertThat(tampered.statusCode()).isBetween(400, 403);
	}

	@Test
	void nearLimitResume_roundTrips() {
		byte[] content = new byte[9 * 1024 * 1024]; // just under spring.servlet.multipart.max-file-size
		new Random(42).nextBytes(content);

		String key = storage.upload(content, "big.pdf", "application/pdf");

		assertThat(storage.download(key)).isEqualTo(content);
	}

	@Test
	void sseMode_encryptsAtRest_andReadsBackTransparently() throws Exception {
		var sseStorage = new FileStorageService(minio, new StorageProperties(props.endpoint(), props.accessKey(),
				props.secretKey(), props.bucket(), "sse", props.signedUrlTtl()));
		byte[] content = "encrypted resume".getBytes(StandardCharsets.UTF_8);

		String key = sseStorage.upload(content, "cv.pdf", "application/pdf");
		var stat = minio.statObject(StatObjectArgs.builder().bucket(props.bucket()).object(key).build());

		assertThat(stat.headers().get("x-amz-server-side-encryption")).isEqualTo("AES256");
		assertThat(sseStorage.download(key)).isEqualTo(content);
	}
}
