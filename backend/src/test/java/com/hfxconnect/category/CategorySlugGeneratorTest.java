package com.hfxconnect.category;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class CategorySlugGeneratorTest {

	@Test
	void generatesSimpleSlugFromTwoWords() {
		assertThat(CategorySlugGenerator.generate("Food Assistance")).contains("food-assistance");
	}

	@Test
	void collapsesRepeatedInternalAndSurroundingWhitespace() {
		assertThat(CategorySlugGenerator.generate("  Study   Spaces  ")).contains("study-spaces");
	}

	@Test
	void convertsPunctuationRunsIntoASingleHyphen() {
		assertThat(CategorySlugGenerator.generate("Employment & Career Support"))
				.contains("employment-career-support");
	}

	@Test
	void stripsAccentedCharactersDeterministically() {
		assertThat(CategorySlugGenerator.generate("Café Meetups")).contains("cafe-meetups");
	}

	@Test
	void lowercasesMixedCaseInput() {
		assertThat(CategorySlugGenerator.generate("FOOD ASSISTANCE")).contains("food-assistance");
	}

	@Test
	void collapsesRepeatedHyphensFromAdjacentSeparators() {
		assertThat(CategorySlugGenerator.generate("Food -- Assistance")).contains("food-assistance");
	}

	@Test
	void isEmptyWhenNameContainsNoAlphanumericCharacters() {
		assertThat(CategorySlugGenerator.generate("&&&")).isEqualTo(Optional.empty());
		assertThat(CategorySlugGenerator.generate("!!!")).isEqualTo(Optional.empty());
	}

	@Test
	void differentNamesCanProduceTheSameSlug() {
		// This is exactly why slug uniqueness is enforced independently of
		// normalized-name uniqueness — see ADR-005.
		Optional<String> first = CategorySlugGenerator.generate("Food Assistance");
		Optional<String> second = CategorySlugGenerator.generate("Food, Assistance!");

		assertThat(first).isEqualTo(second);
	}

	@Test
	void isDeterministicForTheSameInput() {
		String input = "Newcomer & Settlement Support";

		assertThat(CategorySlugGenerator.generate(input)).isEqualTo(CategorySlugGenerator.generate(input));
	}

}
