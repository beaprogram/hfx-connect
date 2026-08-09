package com.hfxconnect.moderation;

import com.hfxconnect.common.error.ValidationException;
import java.util.Map;

/**
 * Pure validation for the one field every moderation decision shares: the
 * review reason. Required for both approval and rejection (see ADR-016's
 * "Review Reason" section) — visible to the contribution owner, so it is
 * never a place for hidden moderator-only notes, and control characters are
 * stripped so a copy-pasted reason can never inject unexpected formatting
 * into a plain-text display.
 *
 * <p><strong>{@code public}, and {@link #validateReason} is {@code public
 * static}, as of Milestone 10A</strong>: organization verification/
 * suspension decisions and resource-ownership-claim approve/reject decisions
 * (see {@code com.hfxconnect.organization}) need byte-for-byte the same
 * required/bounded/control-character-stripped review-reason rule a
 * moderation decision already has — the same "shared pure utilities are
 * extracted to be reusable on their second use, not preemptively" pattern
 * {@link com.hfxconnect.resource.ResourceValidation} already established
 * (Milestone 8B).
 */
public final class ModerationValidation {

	static final int REASON_MIN_LENGTH = 5;
	static final int REASON_MAX_LENGTH = 1000;

	private ModerationValidation() {
	}

	public static String validateReason(String reason) {
		String normalized = reason == null ? "" : stripControlCharacters(reason.trim());
		if (normalized.isEmpty()) {
			throw new ValidationException("The submitted review contains invalid information.",
					Map.of("reason", "A review reason is required."));
		}
		if (normalized.length() < REASON_MIN_LENGTH) {
			throw new ValidationException("The submitted review contains invalid information.",
					Map.of("reason", "The review reason must be at least " + REASON_MIN_LENGTH + " characters."));
		}
		if (normalized.length() > REASON_MAX_LENGTH) {
			throw new ValidationException("The submitted review contains invalid information.",
					Map.of("reason", "The review reason must be at most " + REASON_MAX_LENGTH + " characters."));
		}
		return normalized;
	}

	private static String stripControlCharacters(String input) {
		StringBuilder builder = new StringBuilder(input.length());
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (!Character.isISOControl(c) || c == '\n' || c == '\t') {
				builder.append(c);
			}
		}
		return builder.toString();
	}

}
