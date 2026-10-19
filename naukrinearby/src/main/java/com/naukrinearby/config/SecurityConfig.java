package com.naukrinearby.config;

import java.time.Duration;
import java.util.List;

import jakarta.servlet.DispatcherType;

import com.naukrinearby.security.ApiRateLimitFilter;
import com.naukrinearby.security.JwtAuthFilter;
import com.naukrinearby.security.ManagementPortScrapeMatcher;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthFilter jwtAuthFilter;
	private final ApiRateLimitFilter apiRateLimitFilter;
	private final ManagementPortScrapeMatcher managementPortScrapeMatcher;

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				// CSRF is deliberately off (security-and-api.md §A.5c): every authenticated request carries
				// the JWT in the Authorization header, which a cross-site form or image cannot set, and no
				// cookie authenticates a request (nn_session is only a UI routing hint). Twilio webhooks
				// are authenticated by signature. If auth ever moves to cookies, turn CSRF on.
				.csrf(csrf -> csrf.disable())
				.cors(Customizer.withDefaults())
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						// The entry point / access-denied handler use sendError(401/403), which re-dispatches to
						// /error. Without this, that ERROR dispatch is re-authorized with an empty (stateless)
						// context and every 403 surfaces as 401.
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						// Only the token-issuing endpoints are public; /api/auth/me needs a principal
						// (falls through to authenticated() → 401, instead of an NPE → 500).
						.requestMatchers("/api/auth/send-otp", "/api/auth/verify-otp", "/api/auth/refresh",
								"/api/auth/logout").permitAll()
						.requestMatchers("/api/webhooks/**").permitAll()
						.requestMatchers("/api/internal/**").permitAll() // guarded by X-Eval-Key + @ConditionalOnProperty
						.requestMatchers(HttpMethod.GET, "/api/jobs/**").permitAll()
						// Voice search works signed out, like text search; rate-limited per IP in SpeechService.
						.requestMatchers(HttpMethod.POST, "/api/speech/transcribe").permitAll()
						.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
						// Prometheus scrape: open ONLY on the internal management port (8081); the same
						// path on any other port still falls through to the ADMIN rule below.
						.requestMatchers(managementPortScrapeMatcher).permitAll()
						.requestMatchers("/actuator/**").hasRole("ADMIN")
						.requestMatchers("/api/admin/**").hasRole("ADMIN")
						.requestMatchers("/api/employer/**").hasRole("EMPLOYER")
						.requestMatchers("/api/candidate/**").hasRole("CANDIDATE")
						.anyRequest().authenticated())
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterAfter(apiRateLimitFilter, JwtAuthFilter.class)
				.exceptionHandling(e -> e
						.authenticationEntryPoint((req, res, ex) ->
								res.sendError(HttpStatus.UNAUTHORIZED.value()))
						.accessDeniedHandler((req, res, ex) ->
								res.sendError(HttpStatus.FORBIDDEN.value())));
		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
		var cfg = new CorsConfiguration();
		cfg.setAllowedOrigins(props.allowedOrigins());
		cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Eval-Key"));
		cfg.setMaxAge(Duration.ofHours(1));
		var src = new UrlBasedCorsConfigurationSource();
		src.registerCorsConfiguration("/api/**", cfg);
		return src;
	}
}
