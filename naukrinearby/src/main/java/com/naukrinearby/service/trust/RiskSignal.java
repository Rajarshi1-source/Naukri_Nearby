package com.naukrinearby.service.trust;

/**
 * One explainable reason a job posting looks risky, with its weight in the 0–100 score (ADR 0007).
 * The enum name is the stable code stored in {@code jobs.risk_reasons} and shown (translated) to the
 * employer and to admins.
 */
public enum RiskSignal {
	/** Asks candidates to pay a fee or deposit — the classic Indian job scam. */
	PAYMENT_REQUEST(45),
	/** Uses get-rich-quick wording ("earn daily", "ghar baithe"). */
	GET_RICH_QUICK(15),
	/** Salary far above the usual range for the category. */
	UNREALISTIC_SALARY(25),
	/** Pushes the conversation off the platform (phone number or WhatsApp link in the text). */
	OFF_PLATFORM_CONTACT(20),
	/** The employer's business (GSTIN) is not verified. */
	UNVERIFIED_BUSINESS(10),
	/** The employer account was created very recently. */
	NEW_EMPLOYER(10),
	/** Mostly capital letters or repeated exclamation marks. */
	SHOUTING(5);

	private final int weight;

	RiskSignal(int weight) {
		this.weight = weight;
	}

	public int weight() {
		return weight;
	}
}
