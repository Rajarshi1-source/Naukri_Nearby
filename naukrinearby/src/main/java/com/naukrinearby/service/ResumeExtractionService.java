package com.naukrinearby.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.naukrinearby.generation.LlmExtractionProvider;
import com.naukrinearby.model.dto.ResumeParseResult;
import com.naukrinearby.util.PromptLoader;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Injects the resume into the versioned prompt, calls the provider-agnostic LLM adapter at
 * temperature 0, then post-processes deterministically (eval connectors §4). This is what the
 * eval harness exercises end-to-end.
 */
@Service
@RequiredArgsConstructor
public class ResumeExtractionService {

	/** The single source of truth for which prompt version is in production. */
	public static final String PROMPT_VERSION = "resume_parse_v3";

	private final LlmExtractionProvider llm;
	private final PromptLoader promptLoader;

	public ResumeParseResult extract(String resumeText) {
		String prompt = promptLoader.load(PROMPT_VERSION)
				.replace("{{RESUME_TEXT}}", resumeText == null ? "" : resumeText.trim());

		ResumeParseResult result = llm.extractStructured(prompt, ResumeParseResult.class, PROMPT_VERSION);
		postProcess(result);
		result.setPromptVersion(PROMPT_VERSION);
		return result;
	}

	/** Deterministic server-side cleanup so the eval (and prod) get consistent output. */
	private void postProcess(ResumeParseResult r) {
		if (r == null) {
			return;
		}
		// 1. Normalize phone: keep digits, drop +91 / leading 0, keep last 10.
		if (r.getPhone() != null) {
			String digits = r.getPhone().replaceAll("\\D", "");
			if (digits.startsWith("91") && digits.length() == 12) {
				digits = digits.substring(2);
			}
			if (digits.startsWith("0") && digits.length() == 11) {
				digits = digits.substring(1);
			}
			r.setPhone(digits.length() == 10 ? digits : (digits.isBlank() ? null : digits));
		}
		// 2. Dedupe + trim skills, preserving order (case-insensitive de-dup).
		if (r.getSkills() != null) {
			Set<String> seen = new LinkedHashSet<>();
			List<String> cleaned = new ArrayList<>();
			for (String s : r.getSkills()) {
				if (s == null) {
					continue;
				}
				String t = s.trim();
				if (t.isEmpty()) {
					continue;
				}
				if (seen.add(t.toLowerCase())) {
					cleaned.add(t);
				}
			}
			r.setSkills(cleaned);
		}
		// 3. Backstop total_experience_months from experience[] if the model left it 0.
		if ((r.getTotalExperienceMonths() == null || r.getTotalExperienceMonths() == 0)
				&& r.getExperience() != null && !r.getExperience().isEmpty()) {
			int sum = r.getExperience().stream()
					.mapToInt(e -> e.getMonths() == null ? 0 : e.getMonths())
					.sum();
			if (sum > 0) {
				r.setTotalExperienceMonths(sum);
			}
		}
		if (r.getTotalExperienceMonths() == null) {
			r.setTotalExperienceMonths(0);
		}
	}
}
