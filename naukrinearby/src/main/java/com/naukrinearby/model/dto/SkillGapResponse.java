package com.naukrinearby.model.dto;

import java.util.List;

/**
 * Which of a job's required skills the candidate already has, which are missing, and where to learn
 * the missing ones for free (official portals only, plan D10).
 */
public record SkillGapResponse(
		Long jobId,
		List<String> matched,
		List<String> missing,
		int matchPercent,
		List<Suggestion> suggestions) {

	/** Learning options for one missing skill. {@code searchTerm} is what to type into the portal. */
	public record Suggestion(String skill, String searchTerm, List<Portal> portals) {
	}

	public record Portal(String id, String name, String url) {
	}
}
