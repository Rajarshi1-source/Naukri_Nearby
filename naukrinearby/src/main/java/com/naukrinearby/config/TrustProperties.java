package com.naukrinearby.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Fraud scoring and moderation thresholds (ADR 0007).
 *
 * @param reviewThreshold risk score (0–100) at or above which a job waits for admin review
 * @param reportThreshold distinct user reports that move a job back to review
 * @param gstVerifier {@code checksum} (offline GSTIN check, default) or {@code none}
 * @param newEmployerDays an employer account younger than this adds a risk signal
 */
@ConfigurationProperties(prefix = "naukri.trust")
public record TrustProperties(int reviewThreshold, int reportThreshold, String gstVerifier, int newEmployerDays) {

	public TrustProperties {
		if (reviewThreshold <= 0) {
			reviewThreshold = 50;
		}
		if (reportThreshold <= 0) {
			reportThreshold = 3;
		}
		if (gstVerifier == null || gstVerifier.isBlank()) {
			gstVerifier = "checksum";
		}
		if (newEmployerDays <= 0) {
			newEmployerDays = 7;
		}
	}
}
