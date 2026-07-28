package com.hfxconnect.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hfxconnect.common.error.InvalidSearchQueryException;
import org.junit.jupiter.api.Test;

/** Pure unit tests for {@link ResourceSearchQuery} — see ADR-010. */
class ResourceSearchQueryTest {

	/** BEL — a real, non-whitespace control character, for testing stripping behavior. */
	private static final char CONTROL_CHAR = 0x07;

	@Test
	void normalizeReturnsNullForNull() {
		assertThat(ResourceSearchQuery.normalize(null)).isNull();
	}

	@Test
	void normalizeReturnsNullForBlank() {
		assertThat(ResourceSearchQuery.normalize("")).isNull();
		assertThat(ResourceSearchQuery.normalize("   ")).isNull();
		assertThat(ResourceSearchQuery.normalize("\t\n")).isNull();
	}

	@Test
	void normalizeTrimsLeadingAndTrailingWhitespace() {
		assertThat(ResourceSearchQuery.normalize("  library  ")).isEqualTo("library");
	}

	@Test
	void normalizeCollapsesRepeatedInternalWhitespace() {
		assertThat(ResourceSearchQuery.normalize("food   bank")).isEqualTo("food bank");
		assertThat(ResourceSearchQuery.normalize("food\t\tbank\n\nhelp")).isEqualTo("food bank help");
	}

	@Test
	void normalizePreservesMeaningfulPunctuation() {
		assertThat(ResourceSearchQuery.normalize("St. Mary's")).isEqualTo("St. Mary's");
	}

	@Test
	void normalizeAcceptsExactlyTheMaximumLength() {
		String maxLength = "a".repeat(ResourceSearchQuery.MAX_LENGTH);
		assertThat(ResourceSearchQuery.normalize(maxLength)).isEqualTo(maxLength);
	}

	@Test
	void normalizeRejectsAQueryOverTheMaximumLength() {
		String tooLong = "a".repeat(ResourceSearchQuery.MAX_LENGTH + 1);
		assertThatThrownBy(() -> ResourceSearchQuery.normalize(tooLong))
				.isInstanceOf(InvalidSearchQueryException.class);
	}

	@Test
	void normalizeStripsControlCharactersWithoutError() {
		String withControlChar = "library" + CONTROL_CHAR + "name";
		assertThat(ResourceSearchQuery.normalize(withControlChar)).isEqualTo("libraryname");
	}

	@Test
	void normalizeReturnsNullWhenOnlyControlCharactersRemainAfterStripping() {
		String onlyControlChars = "" + CONTROL_CHAR + CONTROL_CHAR;
		assertThat(ResourceSearchQuery.normalize(onlyControlChars)).isNull();
	}

	@Test
	void toLikePatternLowercasesAndWrapsWithWildcards() {
		assertThat(ResourceSearchQuery.toLikePattern("Library")).isEqualTo("%library%");
	}

	@Test
	void toLikePatternEscapesPercentAsALiteralCharacter() {
		assertThat(ResourceSearchQuery.toLikePattern("50%")).isEqualTo("%50\\%%");
	}

	@Test
	void toLikePatternEscapesUnderscoreAsALiteralCharacter() {
		assertThat(ResourceSearchQuery.toLikePattern("user_name")).isEqualTo("%user\\_name%");
	}

	@Test
	void toLikePatternEscapesTheEscapeCharacterItselfFirst() {
		// A literal backslash must become an escaped backslash, not accidentally
		// escape whatever character follows it once wrapped in wildcards.
		assertThat(ResourceSearchQuery.toLikePattern("a\\b")).isEqualTo("%a\\\\b%");
	}

	@Test
	void toLikePatternHandlesMixedWildcardAndEscapeCharacters() {
		assertThat(ResourceSearchQuery.toLikePattern("100%_off\\now")).isEqualTo("%100\\%\\_off\\\\now%");
	}

}
