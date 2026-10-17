package com.naukrinearby.model.enums;

/** Processing purposes a user consents to (DPDP Act notice and consent). */
public enum ConsentPurpose {
	/** Parsing a resume or chat/IVR answers into a candidate profile. */
	RESUME_PROCESSING,
	/** Sending job alerts over WhatsApp. */
	WHATSAPP_ALERTS
}
