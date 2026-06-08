package com.naukrinearby.generation;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.naukrinearby.config.TwilioProperties;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Real Twilio WhatsApp sender (Messages API). Selected by {@code naukri.twilio.provider=twilio};
 * the {@link StubWhatsAppSender} stays the default. POSTs a form-encoded message with HTTP Basic auth
 * (AccountSid:AuthToken) and returns the Twilio {@code MessageSid} used for delivery dedup/webhooks.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "naukri.twilio.provider", havingValue = "twilio")
public class TwilioWhatsAppSender implements WhatsAppSender {

	private final TwilioProperties props;
	private final RestClient client;

	public TwilioWhatsAppSender(TwilioProperties props) {
		this.props = props;
		var factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(5_000);
		factory.setReadTimeout(10_000);
		String basic = Base64.getEncoder().encodeToString(
				(props.accountSid() + ":" + props.authToken()).getBytes(StandardCharsets.UTF_8));
		this.client = RestClient.builder()
				.baseUrl("https://api.twilio.com/2010-04-01/Accounts/" + props.accountSid())
				.requestFactory(factory)
				.defaultHeader("Authorization", "Basic " + basic)
				.build();
	}

	@Override
	@Retry(name = "twilio")
	@CircuitBreaker(name = "twilio")
	public String send(String toPhone, String body) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("From", props.whatsappFrom());
		form.add("To", toPhone.startsWith("whatsapp:") ? toPhone : "whatsapp:" + toPhone);
		form.add("Body", body);
		MessageResponse response = client.post()
				.uri("/Messages.json")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(MessageResponse.class);
		if (response == null || response.sid() == null) {
			throw new IllegalStateException("Twilio returned no MessageSid");
		}
		return response.sid();
	}

	@Override
	public String providerId() {
		return "twilio";
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record MessageResponse(@JsonProperty("sid") String sid) {
	}
}
