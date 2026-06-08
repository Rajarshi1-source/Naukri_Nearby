package com.naukrinearby.model.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * Resume extraction result. The eval harness reads {@code skills}, {@code city}, and
 * {@code total_experience_months} from the JSON, so those keys are emitted exactly (eval connectors §2).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeParseResult {

	private String name;
	private String phone;
	private String email;
	private String city;
	private String state;

	private List<String> skills = new ArrayList<>();

	private List<Experience> experience = new ArrayList<>();
	private List<Education> education = new ArrayList<>();

	@JsonProperty("languages_spoken")
	private List<String> languagesSpoken = new ArrayList<>();

	@JsonProperty("total_experience_months")
	private Integer totalExperienceMonths = 0;

	/** Echoed back for observability; set server-side, not produced by the model. */
	@JsonProperty("prompt_version")
	private String promptVersion;

	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Experience {
		private String title;
		private String company;
		private Integer months = 0;
	}

	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Education {
		private String degree;
		private String institution;
		private Integer year;
	}
}
