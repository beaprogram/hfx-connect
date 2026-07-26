package com.hfxconnect.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RefreshTokenGeneratorTest {

	private static final Pattern URL_SAFE_BASE64 = Pattern.compile("^[A-Za-z0-9_-]+$");

	@Test
	void generatedTokensHaveSufficientEntropy() {
		// 32 raw bytes, Base64URL-encoded without padding, is 43 characters.
		String token = RefreshTokenGenerator.generateRawToken();

		assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
	}

	@Test
	void generatedTokensAreUrlSafe() {
		String token = RefreshTokenGenerator.generateRawToken();

		assertThat(URL_SAFE_BASE64.matcher(token).matches()).isTrue();
	}

	@Test
	void repeatedGenerationProducesNoDuplicatesAcrossManyCalls() {
		Set<String> tokens = new HashSet<>();
		for (int i = 0; i < 10_000; i++) {
			tokens.add(RefreshTokenGenerator.generateRawToken());
		}

		assertThat(tokens).hasSize(10_000);
	}

	@Test
	void hashingIsDeterministic() {
		String token = RefreshTokenGenerator.generateRawToken();

		assertThat(RefreshTokenGenerator.hash(token)).isEqualTo(RefreshTokenGenerator.hash(token));
	}

	@Test
	void distinctRawTokensProduceDistinctHashes() {
		String first = RefreshTokenGenerator.generateRawToken();
		String second = RefreshTokenGenerator.generateRawToken();

		assertThat(RefreshTokenGenerator.hash(first)).isNotEqualTo(RefreshTokenGenerator.hash(second));
	}

	@Test
	void hashIsA64CharacterHexDigest() {
		String hash = RefreshTokenGenerator.hash(RefreshTokenGenerator.generateRawToken());

		assertThat(hash).hasSize(64).matches("^[0-9a-f]{64}$");
	}

	@Test
	void hashNeverEqualsTheRawTokenItself() {
		String token = RefreshTokenGenerator.generateRawToken();

		assertThat(RefreshTokenGenerator.hash(token)).isNotEqualTo(token);
	}

}
