package com.naukrinearby.service;

import com.naukrinearby.model.dto.SkillGapResponse;
import com.naukrinearby.service.search.QueryNormalizer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillGapServiceTest {

	private final SkillGapService service = new SkillGapService(null, null, new QueryNormalizer());

	@Test
	void matchesThroughTheHinglishLexicon_andSuggestsOfficialPortals() {
		SkillGapResponse gap = service.compare(7L, new String[] { "Driving", "Tally", "Accounting" },
				new String[] { "gaadi chalana", "hisab kitab" });

		assertThat(gap.matched()).containsExactly("Driving", "Accounting");
		assertThat(gap.missing()).containsExactly("Tally");
		assertThat(gap.matchPercent()).isEqualTo(67);
		assertThat(gap.suggestions()).singleElement().satisfies(s -> {
			assertThat(s.skill()).isEqualTo("Tally");
			assertThat(s.searchTerm()).isEqualTo("tally");
			// SWAYAM lists tally explicitly, so it comes before the general portals.
			assertThat(s.portals()).extracting(SkillGapResponse.Portal::id).first().isEqualTo("swayam");
			assertThat(s.portals()).allSatisfy(p -> assertThat(p.url()).startsWith("https://"));
			assertThat(s.portals()).hasSizeLessThanOrEqualTo(3);
		});
	}

	@Test
	void noRequiredSkills_isAFullMatch() {
		SkillGapResponse gap = service.compare(1L, new String[0], new String[] { "Plumbing" });
		assertThat(gap.matchPercent()).isEqualTo(100);
		assertThat(gap.missing()).isEmpty();
	}

	@Test
	void drivingSkill_pointsToSarathi() {
		SkillGapResponse gap = service.compare(1L, new String[] { "Driving licence" }, new String[0]);
		assertThat(gap.suggestions().get(0).portals()).extracting(SkillGapResponse.Portal::id).contains("sarathi");
	}
}
