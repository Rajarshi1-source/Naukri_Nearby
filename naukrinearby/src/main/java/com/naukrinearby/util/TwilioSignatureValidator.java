package com.naukrinearby.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Validates Twilio webhook signatures (security-and-api.md §A.5): HMAC-SHA1 over the full request
 * URL with sorted form params appended, base64-encoded, compared to the {@code X-Twilio-Signature}
 * header. Implemented directly (no SDK) since Twilio is stubbed in this MVP.
 */
public final class TwilioSignatureValidator {

	private TwilioSignatureValidator() {
	}

	public static boolean isValid(String authToken, String url, Map<String, String> params, String signature) {
		if (signature == null) {
			return false;
		}
		StringBuilder data = new StringBuilder(url);
		// Twilio sorts params by key and concatenates key+value.
		for (var entry : new TreeMap<>(params).entrySet()) {
			data.append(entry.getKey()).append(entry.getValue() == null ? "" : entry.getValue());
		}
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(authToken.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
			byte[] digest = mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8));
			String expected = Base64.getEncoder().encodeToString(digest);
			return constantTimeEquals(expected, signature);
		}
		catch (Exception ex) {
			return false;
		}
	}

	private static boolean constantTimeEquals(String a, String b) {
		if (a.length() != b.length()) {
			return false;
		}
		int result = 0;
		for (int i = 0; i < a.length(); i++) {
			result |= a.charAt(i) ^ b.charAt(i);
		}
		return result == 0;
	}
}
