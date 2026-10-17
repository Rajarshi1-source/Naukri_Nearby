package com.naukrinearby.service.trust;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Offline GSTIN verification: 15 characters — 2-digit state code, 10-character PAN, entity number,
 * the literal {@code Z}, and a mod-36 check character over the first 14 characters. It proves the
 * number is well-formed and not a typo, not that the business is registered (that needs a GSP API).
 */
@Component
@ConditionalOnProperty(name = "naukri.trust.gst-verifier", havingValue = "checksum", matchIfMissing = true)
public class ChecksumGstVerifier implements GstVerifier {

	private static final String CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	private static final Pattern FORMAT = Pattern.compile("[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]");

	@Override
	public boolean isValid(String gstin) {
		if (gstin == null) {
			return false;
		}
		String g = gstin.strip().toUpperCase(Locale.ROOT);
		if (g.length() != 15 || !FORMAT.matcher(g).matches()) {
			return false;
		}
		int state = Integer.parseInt(g.substring(0, 2));
		if (state < 1 || (state > 38 && state != 97 && state != 99)) {
			return false;
		}
		return g.charAt(14) == checkCharacter(g);
	}

	static char checkCharacter(String gstin) {
		int sum = 0;
		for (int i = 0; i < 14; i++) {
			int product = CHARSET.indexOf(gstin.charAt(i)) * (i % 2 == 0 ? 1 : 2);
			sum += product / 36 + product % 36;
		}
		return CHARSET.charAt((36 - sum % 36) % 36);
	}

	@Override
	public String verifierId() {
		return "checksum";
	}
}
