package com.naukrinearby.model.enums;

import java.util.Arrays;

/** ISO-style codes for the regional languages NaukriNearby supports for alerts. */
public enum Language {
	EN("en"),
	HI("hi"),
	TA("ta"),
	TE("te"),
	BN("bn"),
	MR("mr"),
	KN("kn"),
	GU("gu");

	private final String code;

	Language(String code) {
		this.code = code;
	}

	public String getCode() {
		return code;
	}

	public static Language fromCode(String code) {
		if (code == null) {
			return EN;
		}
		return Arrays.stream(values())
				.filter(l -> l.code.equalsIgnoreCase(code))
				.findFirst()
				.orElse(EN);
	}
}
