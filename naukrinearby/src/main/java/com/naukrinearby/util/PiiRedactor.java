package com.naukrinearby.util;

import java.util.regex.Pattern;

/**
 * Masks PII (phone numbers, emails) before it reaches logs (DPDP Act / security-and-api.md §C).
 * Never log raw phone/email — pass values through these helpers, or {@link #redact(String)} for
 * free-text that may embed either.
 */
public final class PiiRedactor {

	private static final Pattern PHONE = Pattern.compile("(\\+?\\d[\\d\\-\\s]{7,}\\d)");
	private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9._%+-]+)@([A-Za-z0-9.-]+)");

	private PiiRedactor() {
	}

	/** Keeps the first two and last two digits: {@code 9876543210 -> 98XXXX3210}. */
	public static String maskPhone(String phone) {
		if (phone == null || phone.length() < 4) {
			return "****";
		}
		return phone.substring(0, 2) + "XXXX" + phone.substring(phone.length() - 2);
	}

	/** Keeps the first local char + domain: {@code ravi.kumar@gmail.com -> r***@gmail.com}. */
	public static String maskEmail(String email) {
		if (email == null) {
			return null;
		}
		int at = email.indexOf('@');
		if (at <= 0) {
			return "***";
		}
		return email.charAt(0) + "***" + email.substring(at);
	}

	/** Masks any phone/email occurrences embedded in a free-text message before logging. */
	public static String redact(String text) {
		if (text == null || text.isBlank()) {
			return text;
		}
		String masked = EMAIL.matcher(text).replaceAll(m -> m.group(1).charAt(0) + "***@" + m.group(2));
		masked = PHONE.matcher(masked).replaceAll(m -> maskPhone(m.group(1).replaceAll("[\\s-]", "")));
		return masked;
	}
}
