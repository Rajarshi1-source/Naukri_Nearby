package com.naukrinearby.service.otp;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.naukrinearby.config.TwilioProperties;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Real OTP via Twilio Verify v2 (issuance + check happen on Twilio — no OTP is stored server-side).
 * Selected by {@code naukri.otp.provider=twilio}; the {@link StubOtpProvider} stays the default.
 * Uses {@code naukri.twilio.account-sid/auth-token} and {@code naukri.twilio.verify-service-sid}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "naukri.otp.provider", havingValue = "twilio")
public class TwilioVerifyOtpProvider implements OtpProvider {

	private final TwilioProperties props;
	private final RestClient client;

	public TwilioVerifyOtpProvider(TwilioProperties props) {
		this.props = props;
		var factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(5_000);
		factory.setReadTimeout(10_000);
		String basic = Base64.getEncoder().encodeToString(
				(props.accountSid() + ":" + props.authToken()).getBytes(StandardCharsets.UTF_8));
		this.client = RestClient.builder()
				.baseUrl("https://verify.twilio.com/v2/Services/" + props.verifyServiceSid())
				.requestFactory(factory)
				.defaultHeader("Authorization", "Basic " + basic)
				.build();
	}

	@Override
	public void send(String phone) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("To", phone);
		form.add("Channel", "sms");
		client.post()
				.uri("/Verifications")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.toBodilessEntity();
	}

	@Override
	public boolean verify(String phone, String code) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("To", phone);
		form.add("Code", code);
		try {
			VerificationCheck result = client.post()
					.uri("/VerificationChecks")
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(form)
					.retrieve()
					.body(VerificationCheck.class);
			return result != null && "approved".equals(result.status());
		}
		catch (RuntimeException ex) {
			log.warn("Twilio Verify check failed: {}", ex.getMessage());
			return false;
		}
	}

	@Override
	public String providerId() {
		return "twilio";
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record VerificationCheck(String status) {
	}
}
