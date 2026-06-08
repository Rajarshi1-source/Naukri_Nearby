package com.naukrinearby.service;

import java.util.List;

import com.naukrinearby.config.EvalProperties;
import com.naukrinearby.generation.LlmExtractionProvider;
import com.naukrinearby.model.dto.ResumeParseResult;
import com.naukrinearby.util.PromptLoader;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit test of the deterministic post-processing (no Spring context, no containers). */
class ResumeExtractionServiceTest {

	@Test
	void postProcessNormalizesPhoneDedupesSkillsAndBackfillsExperience() {
		LlmExtractionProvider fakeLlm = new LlmExtractionProvider() {
			@Override
			@SuppressWarnings("unchecked")
			public <T> T extractStructured(String prompt, Class<T> schema, String promptVersion) {
				ResumeParseResult r = new ResumeParseResult();
				r.setPhone("+91 98765 43210");
				r.setSkills(List.of("Tally", "tally", " Accounting ", ""));
				r.setTotalExperienceMonths(0);
				ResumeParseResult.Experience e = new ResumeParseResult.Experience();
				e.setTitle("Accountant");
				e.setMonths(36);
				r.setExperience(List.of(e));
				return (T) r;
			}

			@Override
			public String providerId() {
				return "fake";
			}
		};

		ResumeExtractionService service = new ResumeExtractionService(fakeLlm, new PromptLoader(),
				new EvalProperties(false, null, false));
		ResumeParseResult result = service.extract("Suresh, Tally accountant, 3 saal");

		assertThat(result.getPhone()).isEqualTo("9876543210");
		assertThat(result.getSkills()).containsExactly("Tally", "Accounting");
		assertThat(result.getTotalExperienceMonths()).isEqualTo(36);
		assertThat(result.getPromptVersion()).isEqualTo("resume_parse_v3");
	}
}
