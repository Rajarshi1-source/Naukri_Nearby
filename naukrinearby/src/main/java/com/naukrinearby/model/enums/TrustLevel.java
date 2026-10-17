package com.naukrinearby.model.enums;

import com.naukrinearby.model.entity.Employer;

/**
 * Employer trust shown to candidates (ADR 0007). Every employer signs in with a phone OTP, so an
 * existing employer is at least {@code PHONE_VERIFIED}; a GSTIN that passes the configured
 * {@code GstVerifier} lifts it to {@code BUSINESS_VERIFIED}.
 */
public enum TrustLevel {
	UNVERIFIED,
	PHONE_VERIFIED,
	BUSINESS_VERIFIED;

	public static TrustLevel of(Employer employer) {
		if (employer == null) {
			return UNVERIFIED;
		}
		return employer.isGstVerified() ? BUSINESS_VERIFIED : PHONE_VERIFIED;
	}
}
