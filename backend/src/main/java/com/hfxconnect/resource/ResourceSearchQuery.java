package com.hfxconnect.resource;

import com.hfxconnect.common.error.InvalidSearchQueryException;
import java.util.Locale;

/**
 * Normalizes the public {@code q} (keyword search) parameter and builds the
 * safe, parameter-bound {@code LIKE} pattern {@link ResourceRepository#search}
 * uses. Deliberately not a reuse of {@code com.hfxconnect.common.text.SlugGenerator}
 * — a slug is a lossy, URL-safe identifier; a search query needs to preserve
 * the caller's actual words for both matching and for safely echoing back in
 * a "no results for '...'" message. See ADR-010.
 */
final class ResourceSearchQuery {

	static final int MAX_LENGTH = 100;

	private ResourceSearchQuery() {
	}

	/**
	 * Trims, collapses internal whitespace to single spaces, and strips
	 * non-whitespace control characters. A blank or whitespace-only query
	 * normalizes to {@code null} ("no keyword filter") rather than an error —
	 * an accidental space bar press is not a validation failure. A normalized
	 * query longer than {@link #MAX_LENGTH} throws
	 * {@link InvalidSearchQueryException} ({@code 400 INVALID_SEARCH_QUERY}).
	 */
	static String normalize(String raw) {
		if (raw == null) {
			return null;
		}
		String collapsed = raw.trim().replaceAll("\\s+", " ").replaceAll("\\p{Cntrl}", "").trim();
		if (collapsed.isEmpty()) {
			return null;
		}
		if (collapsed.length() > MAX_LENGTH) {
			throw new InvalidSearchQueryException("q must be at most " + MAX_LENGTH + " characters.");
		}
		return collapsed;
	}

	/**
	 * Builds a lowercase, {@code "%"}-wrapped {@code LIKE} pattern from an
	 * already-{@link #normalize}d query, escaping {@code \}, {@code %}, and
	 * {@code _} so a literal percent sign or underscore in a search phrase
	 * matches literally instead of acting as an unintended wildcard. The
	 * escape character itself is escaped first — otherwise a user-typed
	 * {@code \} would silently turn into a functioning escape sequence for
	 * whatever character follows it. Paired with the query's own
	 * {@code ESCAPE '\'} clause (a fixed literal in the query text, not
	 * user-controlled). See ADR-010's "Safety" section.
	 */
	static String toLikePattern(String normalizedQuery) {
		String escaped = normalizedQuery.toLowerCase(Locale.ROOT)
				.replace("\\", "\\\\")
				.replace("%", "\\%")
				.replace("_", "\\_");
		return "%" + escaped + "%";
	}

}
