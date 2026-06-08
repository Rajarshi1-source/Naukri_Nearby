package com.naukrinearby.config;

import java.time.Duration;
import java.util.List;

import com.naukrinearby.security.ApiRateLimitFilter;
import com.naukrinearby.security.JwtAuthFilter;

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

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				// Safe because the API is stateless and the JWT travels in the Authorization
				// header (no cookie-based session to forge).
				.csrf(csrf -> csrf.disable())
				.cors(Customizer.withDefaults())
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/auth/**", "/api/webhooks/**").permitAll()
						.requestMatchers("/api/internal/**").permitAll() // guarded by X-Eval-Key + @ConditionalOnProperty
						.requestMatchers(HttpMethod.GET, "/api/jobs/**").permitAll()
						.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
						.requestMatchers("/actuator/**").hasRole("ADMIN")
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
