package com.hfxconnect.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SlugGeneratorTest {

	@Test
	void generatesSimpleSlugFromTwoWords() {
		assertThat(SlugGenerator.generate("Food Assistance")).contains("food-assistance");
	}

	@Test
	void collapsesRepeatedInternalAndSurroundingWhitespace() {
		assertThat(SlugGenerator.generate("  Study   Spaces  ")).contains("study-spaces");
	}

	@Test
	void convertsPunctuationRunsIntoASingleHyphen() {
		assertThat(SlugGenerator.generate("Employment & Career Support"))
				.contains("employment-career-support");
	}

	@Test
	void stripsAccentedCharactersDeterministically() {
		assertThat(SlugGenerator.generate("Café Meetups")).contains("cafe-meetups");
	}

	@Test
	void lowercasesMixedCaseInput() {
		assertThat(SlugGenerator.generate("FOOD ASSISTANCE")).contains("food-assistance");
	}

	@Test
	void collapsesRepeatedHyphensFromAdjacentSeparators() {
		assertThat(SlugGenerator.generate("Food -- Assistance")).contains("food-assistance");
	}

	@Test
	void isEmptyWhenNameContainsNoAlphanumericCharacters() {
		assertThat(SlugGenerator.generate("&&&")).isEqualTo(Optional.empty());
		assertThat(SlugGenerator.generate("!!!")).isEqualTo(Optional.empty());
	}

	@Test
	void differentNamesCanProduceTheSameSlug() {
		// This is exactly why slug uniqueness is enforced independently of
		// normalized-name uniqueness — see ADR-005.
		Optional<String> first = SlugGenerator.generate("Food Assistance");
		Optional<String> second = SlugGenerator.generate("Food, Assistance!");

		assertThat(first).isEqualTo(second);
	}

	@Test
	void isDeterministicForTheSameInput() {
		String input = "Newcomer & Settlement Support";

		assertThat(SlugGenerator.generate(input)).isEqualTo(SlugGenerator.generate(input));
	}

}
