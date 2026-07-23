package com.naukrinearby.generation;

import com.naukrinearby.model.dto.ResumeParseResult;
import com.naukrinearby.service.ResumeExtractionService;
import com.naukrinearby.util.PromptLoader;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against the offline stub scanning the whole rendered prompt instead of the resume.
 * Uses the REAL {@code resume_parse_v3} template (which lists every canonical skill plus worked
 * examples with numbers/emails/cities): if the stub leaked the template, these assertions fail.
 */
class StubLlmExtractionProviderTest {

	private static final String RESUME =
			"Naam: Suresh Gupta. Kanpur me rehta hoon. 3 saal Tally aur accounting ka kaam kiya hai "
			+ "ek kirana wholesale me. GST filing bhi aati hai. Mobile 9876543210";

	@Test
	void extractsFromResumeOnly_notFromTheSurroundingPromptTemplate() {
		StubLlmExtractionProvider stub = new StubLlmExtractionProvider();
		String prompt = new PromptLoader().load(ResumeExtractionService.PROMPT_VERSION)
				.replace("{{RESUME_TEXT}}", RESUME);

		ResumeParseResult r = stub.extractStructured(
				prompt, ResumeParseResult.class, ResumeExtractionService.PROMPT_VERSION);

		assertThat(r.getSkills())
				.containsExactlyInAnyOrder("Tally", "Accounting", "GST Filing", "Retail Sales", "Customer Service");
		assertThat(r.getCity()).isEqualTo("Kanpur");
		assertThat(r.getTotalExperienceMonths()).isEqualTo(36);
		assertThat(r.getPhone()).isEqualTo("9876543210");
		assertThat(r.getEmail()).isNull();
	}
}
