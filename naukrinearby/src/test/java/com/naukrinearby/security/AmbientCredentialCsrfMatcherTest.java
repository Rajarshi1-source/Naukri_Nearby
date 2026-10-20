package com.naukrinearby.security;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

class AmbientCredentialCsrfMatcherTest {

	private final AmbientCredentialCsrfMatcher matcher = new AmbientCredentialCsrfMatcher();

	@Test
	void bearerOnlyRequests_needNoToken() {
		MockHttpServletRequest post = new MockHttpServletRequest("POST", "/api/jobs");
		post.addHeader("Authorization", "Bearer abc");
		// UI hint cookies are not credentials.
		post.setCookies(new Cookie("nn_session", "1"), new Cookie("nn_lang", "hi"));
		// MockHttpServletRequest defaults this to true; a real container says false when no id was sent.
		post.setRequestedSessionIdValid(false);
		assertThat(matcher.matches(post)).isFalse();
	}

	@Test
	void stateChangingRequests_withALiveSession_needAToken() {
		for (String method : new String[] { "POST", "PUT", "PATCH", "DELETE" }) {
			MockHttpServletRequest req = new MockHttpServletRequest(method, "/api/jobs/1");
			req.setSession(new MockHttpSession());
			assertThat(matcher.matches(req)).as(method).isTrue();
		}
		MockHttpServletRequest validId = new MockHttpServletRequest("POST", "/api/jobs");
		validId.setRequestedSessionId("abc");
		validId.setRequestedSessionIdValid(true);
		assertThat(matcher.matches(validId)).isTrue();
	}

	@Test
	void staleOrForeignSessionCookie_isNotACredential() {
		// e.g. a JSESSIONID set on localhost by another local app: no live session behind it.
		MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/send-otp");
		req.setCookies(new Cookie("JSESSIONID", "foreign"));
		req.setRequestedSessionId("foreign");
		req.setRequestedSessionIdValid(false);
		assertThat(matcher.matches(req)).isFalse();
	}

	@Test
	void safeMethods_neverNeedAToken() {
		for (String method : new String[] { "GET", "HEAD", "OPTIONS", "TRACE" }) {
			MockHttpServletRequest req = new MockHttpServletRequest(method, "/api/jobs/search");
			req.setSession(new MockHttpSession());
			assertThat(matcher.matches(req)).as(method).isFalse();
		}
	}
}
