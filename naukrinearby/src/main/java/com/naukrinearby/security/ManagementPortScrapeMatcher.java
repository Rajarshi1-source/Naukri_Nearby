package com.naukrinearby.security;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

/**
 * Matches a Prometheus scrape ({@code GET /actuator/prometheus}) that arrived on the separate
 * management port ({@code management.server.port}, 8081 by default). That port is reachable only
 * on the internal network: it is not published by Compose, not exposed by the k8s Service, and
 * guarded by a NetworkPolicy.
 *
 * <p>The port is learned from the management server's own {@link WebServerInitializedEvent}, so it
 * is the real bound port (random in tests). The check uses {@link HttpServletRequest#getLocalPort()},
 * the socket's local port, which a client cannot spoof (unlike {@code getServerPort()}, which comes
 * from the {@code Host} header). Fail-closed: if actuator shares the public port, no "management"
 * server starts and this never matches, so the endpoint stays ADMIN-only.
 */
@Component
public class ManagementPortScrapeMatcher implements RequestMatcher, ApplicationListener<WebServerInitializedEvent> {

	static final String MANAGEMENT_NAMESPACE = "management";
	static final String PROMETHEUS_PATH = "/actuator/prometheus";

	private volatile int managementPort = -1;

	@Override
	public void onApplicationEvent(WebServerInitializedEvent event) {
		if (MANAGEMENT_NAMESPACE.equals(event.getApplicationContext().getServerNamespace())) {
			this.managementPort = event.getWebServer().getPort();
		}
	}

	@Override
	public boolean matches(HttpServletRequest request) {
		int port = this.managementPort;
		return port > 0
				&& request.getLocalPort() == port
				&& "GET".equals(request.getMethod())
				&& PROMETHEUS_PATH.equals(request.getRequestURI());
	}
}
