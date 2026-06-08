package com.naukrinearby.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naukri.twilio")
public record TwilioProperties(
		String provider,
		String accountSid,
		String authToken,
		String whatsappFrom,
		String verifyServiceSid,
		String publicWebhookUrl) {

	public TwilioProperties {
		if (provider == null || provider.isBlank()) {
			provider = "stub";
		}
	}
}
