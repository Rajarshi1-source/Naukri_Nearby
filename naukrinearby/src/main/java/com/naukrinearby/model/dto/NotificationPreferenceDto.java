package com.naukrinearby.model.dto;

import java.util.Arrays;
import java.util.List;

import com.naukrinearby.model.entity.NotificationPreference;

public record NotificationPreferenceDto(
		String channel,
		String language,
		Integer radiusKm,
		List<String> categories,
		String frequency,
		Boolean active) {

	public static NotificationPreferenceDto from(NotificationPreference p) {
		return new NotificationPreferenceDto(
				p.getChannel() == null ? null : p.getChannel().name(),
				p.getLanguage(),
				p.getRadiusKm(),
				p.getCategories() == null ? List.of() : Arrays.asList(p.getCategories()),
				p.getFrequency(),
				p.isActive());
	}
}
