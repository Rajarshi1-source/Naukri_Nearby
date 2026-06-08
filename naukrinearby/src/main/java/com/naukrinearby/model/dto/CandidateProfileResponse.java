package com.naukrinearby.model.dto;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.naukrinearby.model.entity.CandidateProfile;

public record CandidateProfileResponse(
		Long id,
		String name,
		String phone,
		String email,
		String city,
		String state,
		List<String> skills,
		Integer totalExperienceMonths,
		Integer profileCompleteness,
		Instant resumeParsedAt) {

	public static CandidateProfileResponse from(CandidateProfile p) {
		return new CandidateProfileResponse(
				p.getId(), p.getName(), p.getPhone(), p.getEmail(), p.getCity(), p.getState(),
				p.getSkills() == null ? List.of() : Arrays.asList(p.getSkills()),
				p.getTotalExperienceMonths(), p.getProfileCompleteness(), p.getResumeParsedAt());
	}
}
