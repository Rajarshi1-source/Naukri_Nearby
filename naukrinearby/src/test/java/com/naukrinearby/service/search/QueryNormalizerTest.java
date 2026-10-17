package com.naukrinearby.service.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueryNormalizerTest {

	private final QueryNormalizer normalizer = new QueryNormalizer();

	@Test
	void hinglishPhrases_mapToCanonicalTerms_andFillersAreDropped() {
		assertThat(normalizer.normalize("bijli mistri ka kaam chahiye")).isEqualTo("electrician");
		assertThat(normalizer.normalize("Gaadi chalana naukri")).isEqualTo("driving");
		assertThat(normalizer.normalize("dukaan pe kaam")).isEqualTo("retail sales assistant");
	}

	@Test
	void devanagari_mapsToCanonicalTerms() {
		assertThat(normalizer.normalize("बिजली मिस्त्री की नौकरी")).isEqualTo("electrician");
		assertThat(normalizer.normalize("ड्राइवर चाहिए")).isEqualTo("driver");
		assertThat(normalizer.normalize("मज़दूर")).isEqualTo("construction");
	}

	@Test
	void unknownWords_passThrough_andDuplicatesCollapse() {
		assertThat(normalizer.normalize("plumber Lucknow")).isEqualTo("plumber lucknow");
		assertThat(normalizer.normalize("nal mistri plumber")).isEqualTo("plumber");
		assertThat(normalizer.normalize("welder, chahiye!")).isEqualTo("welder");
	}

	@Test
	void blankAndAllFiller_queries() {
		assertThat(normalizer.normalize(null)).isNull();
		assertThat(normalizer.normalize("   ")).isNull();
		// Everything was filler: keep what the user typed rather than searching for nothing.
		assertThat(normalizer.normalize("naukri chahiye")).isEqualTo("naukri chahiye");
	}

	@Test
	void longQueries_areTruncated() {
		String q = "plumber ".repeat(500);
		assertThat(normalizer.normalize(q)).isEqualTo("plumber");
	}

	@Test
	void canonicalSkill_usesLexiconOrLowercases() {
		assertThat(normalizer.canonicalSkill("Gaadi Chalana")).isEqualTo("driving");
		assertThat(normalizer.canonicalSkill("Tally")).isEqualTo("tally");
		assertThat(normalizer.canonicalSkill("टैली")).isEqualTo("tally");
	}
}
