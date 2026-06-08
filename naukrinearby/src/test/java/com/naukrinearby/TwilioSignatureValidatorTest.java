package com.naukrinearby;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.naukrinearby.util.TwilioSignatureValidator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trips the Twilio HMAC-SHA1 signature scheme (full URL + sorted key+value, base64): a
 * correctly-computed signature is accepted, a tampered/null one is rejected.
 */
class TwilioSignatureValidatorTest {

	private static final String TOKEN = "test-auth-token";
	private static final String URL = "https://app.naukrinearby.example/api/webhooks/twilio";
	private static final Map<String, String> PARAMS = new TreeMap<>(Map.of(
			"MessageSid", "SM123",
			"MessageStatus", "delivered",
			"From", "whatsapp:+14155238886",
			"To", "whatsapp:+919876512345"));

	private static String sign(String token, String url, Map<String, String> params) throws Exception {
		StringBuilder data = new StringBuilder(url);
		for (var e : new TreeMap<>(params).entrySet()) {
			data.append(e.getKey()).append(e.getValue());
		}
		Mac mac = Mac.getInstance("HmacSHA1");
		mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
		return Base64.getEncoder().encodeToString(mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	void validSignatureIsAccepted() throws Exception {
		String signature = sign(TOKEN, URL, PARAMS);
		assertThat(TwilioSignatureValidator.isValid(TOKEN, URL, PARAMS, signature)).isTrue();
	}

	@Test
	void tamperedSignatureIsRejected() throws Exception {
		String signature = sign(TOKEN, URL, PARAMS);
		assertThat(TwilioSignatureValidator.isValid("wrong-token", URL, PARAMS, signature)).isFalse();
	}

	@Test
	void nullSignatureIsRejected() {
		assertThat(TwilioSignatureValidator.isValid(TOKEN, URL, PARAMS, null)).isFalse();
	}
}
