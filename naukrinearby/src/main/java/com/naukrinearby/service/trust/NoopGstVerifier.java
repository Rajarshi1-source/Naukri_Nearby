package com.naukrinearby.service.trust;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Disables GSTIN verification: no employer reaches {@code BUSINESS_VERIFIED}. */
@Component
@ConditionalOnProperty(name = "naukri.trust.gst-verifier", havingValue = "none")
public class NoopGstVerifier implements GstVerifier {

	@Override
	public boolean isValid(String gstin) {
		return false;
	}

	@Override
	public String verifierId() {
		return "none";
	}
}
