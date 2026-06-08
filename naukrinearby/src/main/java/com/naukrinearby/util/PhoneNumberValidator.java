package com.naukrinearby.util;

import com.naukrinearby.exception.ValidationException;

/** Normalizes Indian mobile numbers to a bare 10-digit form (strip +91 / leading 0 / separators). */
public final class PhoneNumberValidator {

	private PhoneNumberValidator() {
	}

	public static String normalize(String raw) {
		if (raw == null) {
			throw new ValidationException("Phone number is required");
		}
		String digits = raw.replaceAll("\\D", "");
		if (digits.startsWith("91") && digits.length() == 12) {
			digits = digits.substring(2);
		}
		if (digits.startsWith("0") && digits.length() == 11) {
			digits = digits.substring(1);
		}
		if (digits.length() != 10 || digits.charAt(0) < '6') {
			throw new ValidationException("Invalid Indian mobile number");
		}
		return digits;
	}
}
