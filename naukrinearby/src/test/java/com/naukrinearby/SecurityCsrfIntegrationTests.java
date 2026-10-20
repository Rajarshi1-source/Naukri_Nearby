package com.naukrinearby;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.naukrinearby.security.AmbientCredentialCsrfMatcher;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CSRF is enabled and scoped to ambient credentials (AmbientCredentialCsrfMatcher, CodeQL alert #9).
 * Header-authenticated clients are unaffected, and a stray session cookie (another app on localhost,
 * a stale cookie) does not break them. The live-session case that must be refused is covered by
 * AmbientCredentialCsrfMatcherTest, since this stateless API never creates a session to test with.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityCsrfIntegrationTests {

	private static final AtomicInteger SEQ = new AtomicInteger(0);

	@LocalServerPort int port;
	@Autowired FilterChainProxy filterChainProxy;

	@Test
	void csrfFilter_isInTheChain_withTheAmbientCredentialMatcher() {
		CsrfFilter csrf = filterChainProxy.getFilterChains().stream()
				.flatMap(chain -> chain.getFilters().stream())
				.filter(CsrfFilter.class::isInstance)
				.map(CsrfFilter.class::cast)
				.findFirst()
				.orElseThrow(() -> new AssertionError("CSRF protection is disabled"));
		assertThat(ReflectionTestUtils.getField(csrf, "requireCsrfProtectionMatcher"))
				.isInstanceOf(AmbientCredentialCsrfMatcher.class);
	}

	@Test
	void bearerAndPublicPosts_workWithoutAToken_evenWithAStraySessionCookie() {
		RestTestClient client = client();
		String phone = "96400" + String.format("%05d", SEQ.incrementAndGet());
		client.post().uri("/api/auth/send-otp")
				.header(HttpHeaders.COOKIE, "JSESSIONID=from-another-localhost-app")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("phone", phone))
				.exchange()
				.expectStatus().isOk();
		Map<?, ?> auth = client.post().uri("/api/auth/verify-otp")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("phone", phone, "code", "123456", "role", "CANDIDATE"))
				.exchange()
				.expectStatus().isOk()
				.expectBody(Map.class).returnResult().getResponseBody();

		client.put().uri("/api/candidate/profile")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.get("accessToken"))
				.header(HttpHeaders.COOKIE, "JSESSIONID=stale")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("name", "Asha"))
				.exchange()
				.expectStatus().isOk();
	}

	private RestTestClient client() {
		return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
	}
}
