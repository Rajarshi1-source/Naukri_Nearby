package com.naukrinearby.service.trust;

/**
 * Verifies an employer's GSTIN (ADR 0007). Selected by {@code naukri.trust.gst-verifier}:
 * {@code checksum} (offline format + check digit, default) or {@code none}. A GSP/government API
 * adapter can implement this later without touching callers.
 */
public interface GstVerifier {

	boolean isValid(String gstin);

	String verifierId();
}
