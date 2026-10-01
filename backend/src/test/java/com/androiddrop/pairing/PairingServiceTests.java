package com.androiddrop.pairing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.androiddrop.config.DropProperties;
import com.androiddrop.host.ServerAddresses;

class PairingServiceTests {

	private final PairingService pairing = new PairingService(
			new DropProperties(Path.of("unused"), "", Duration.ofMinutes(10), 5, 1000, Duration.ofHours(1),
					Duration.ofHours(1), 0),
			new ServerAddresses(new StandardEnvironment()), event -> {
			});

	@Test
	void guessingThroughACodePausesPairingSoTheNextCodeCannotBeGuessedRightAway() {
		String first = pairing.current().code();
		String wrong = first.equals("000000") ? "111111" : "000000";
		for (int i = 0; i < 5; i++) {
			assertThat(pairing.redeem(wrong)).isFalse();
		}
		String second = pairing.current().code();
		assertThat(second).as("the guessed-at code was replaced").isNotEqualTo(first);

		// Even the right code is refused for a while, or a guesser would simply carry on with the new one.
		assertThatThrownBy(() -> pairing.redeem(second)).isInstanceOfSatisfying(ResponseStatusException.class,
				ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
		assertThat(pairing.current().code()).as("refused attempts don't use up the code").isEqualTo(second);
	}

	@Test
	void aFewWrongGuessesDoNotLockAnyoneOut() {
		String code = pairing.current().code();
		String wrong = code.equals("000000") ? "111111" : "000000";
		for (int i = 0; i < 4; i++) {
			assertThat(pairing.redeem(wrong)).isFalse();
		}
		assertThat(pairing.redeem(code)).isTrue();
	}

}
