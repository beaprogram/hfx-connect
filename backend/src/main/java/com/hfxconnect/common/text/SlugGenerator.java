package com.hfxconnect.common.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Deterministic, pure slug generation shared by every domain that needs a
 * URL-safe identifier derived from a display name (categories, resources,
 * ...). See ADR-005 for the full rationale — it was originally written for,
 * and is still authoritative for, the category domain; this class was
 * extracted out of {@code category} in Milestone 3B once a second domain
 * (resource) needed byte-for-byte the same algorithm.
 *
 * <p>Rules, applied in order: strip accents/diacritics, lowercase, collapse
 * every run of non-alphanumeric characters (whitespace, punctuation, symbols)
 * into a single hyphen, then trim leading/trailing hyphens. Returns
 * {@link Optional#empty()} if nothing alphanumeric survives (for example, a
 * name made entirely of symbols) — callers must decide how to handle that
 * case rather than silently persisting an empty slug.
 */
public final class SlugGenerator {

	private static final Pattern DIACRITICAL_MARKS = Pattern.compile("\\p{M}+");
	private static final Pattern NON_ALPHANUMERIC_RUN = Pattern.compile("[^a-z0-9]+");
	private static final Pattern LEADING_OR_TRAILING_HYPHENS = Pattern.compile("^-+|-+$");

	private SlugGenerator() {
	}

	public static Optional<String> generate(String input) {
		String withoutDiacritics = DIACRITICAL_MARKS
				.matcher(Normalizer.normalize(input, Normalizer.Form.NFD))
				.replaceAll("");

		String lowercased = withoutDiacritics.toLowerCase(Locale.ROOT);

		String hyphenated = NON_ALPHANUMERIC_RUN.matcher(lowercased).replaceAll("-");

		String slug = LEADING_OR_TRAILING_HYPHENS.matcher(hyphenated).replaceAll("");

		return slug.isEmpty() ? Optional.empty() : Optional.of(slug);
	}

}
