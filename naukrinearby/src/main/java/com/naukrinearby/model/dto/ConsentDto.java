package com.naukrinearby.model.dto;

import java.time.Instant;

import com.naukrinearby.model.enums.ConsentPurpose;

import jakarta.validation.constraints.NotNull;

/** DPDP consent state for one purpose and the request to change it. */
public final class ConsentDto {

	private ConsentDto() {
	}

	/**
	 * Current state. {@code granted} is null when the user has never recorded a choice for this
	 * purpose; {@code recordedAt} and {@code noticeVersion} are then null too.
	 */
	public record Response(ConsentPurpose purpose, Boolean granted, String noticeVersion, Instant recordedAt) {
	}

	public record UpdateRequest(@NotNull ConsentPurpose purpose, @NotNull Boolean granted) {
	}
}
