package com.androiddrop.pairing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.androiddrop.config.DropProperties;
import com.androiddrop.events.DropEvent;
import com.androiddrop.host.ServerAddresses;

/**
 * Owns the short-lived 6-digit code shown on the host. A code is single use: it rotates after a successful
 * pairing, after too many wrong guesses, and when it expires. Every rotation is pushed to the host's screen.
 */
@Service
public class PairingService {

	public record PairingCode(String code, Instant expiresAt) {
	}

	/** What the host needs to show a QR code: the code, its lifetime, and the base URLs a phone can reach. */
	public record PairingInfo(String code, Instant issuedAt, Instant expiresAt, List<String> urls) {
	}

	/** The first pause after a code is used up by wrong guesses; it doubles up to 8 minutes while that keeps happening. */
	private static final Duration LOCKOUT = Duration.ofSeconds(30);

	private final SecureRandom random = new SecureRandom();
	private final DropProperties properties;
	private final ServerAddresses addresses;
	private final ApplicationEventPublisher events;

	private String code;
	private Instant expiresAt = Instant.EPOCH;
	private int failedAttempts;
	/** Pairing is refused until then: set when a code is used up by wrong guesses, and longer each time in a row. */
	private Instant lockedUntil = Instant.EPOCH;
	private int lockouts;

	public PairingService(DropProperties properties, ServerAddresses addresses, ApplicationEventPublisher events) {
		this.properties = properties;
		this.addresses = addresses;
		this.events = events;
	}

	public synchronized PairingCode current() {
		if (code == null || isExpired()) {
			rotate();
		}
		return new PairingCode(code, expiresAt);
	}

	public PairingInfo info() {
		PairingCode current = current();
		return new PairingInfo(current.code(), current.expiresAt().minus(properties.pairingCodeTtl()),
				current.expiresAt(), addresses.lanUrls());
	}

	public synchronized PairingCode rotate() {
		code = "%06d".formatted(random.nextInt(1_000_000));
		expiresAt = Instant.now().plus(properties.pairingCodeTtl());
		failedAttempts = 0;
		events.publishEvent(new DropEvent.PairingCodeChanged());
		return new PairingCode(code, expiresAt);
	}

	/** Returns true, and burns the code, if {@code candidate} is the current code. */
	public synchronized boolean redeem(String candidate) {
		if (check(candidate)) {
			rotate();
			lockouts = 0;
			return true;
		}
		return false;
	}

	/**
	 * Whether {@code candidate} is the current code, without using it up. Wrong guesses count towards rotating
	 * the code, as with {@link #redeem}. Once a code is used up that way, pairing pauses for a while: a new code alone
	 * wouldn't slow down something on the network guessing its way through all million of them.
	 */
	public synchronized boolean check(String candidate) {
		if (Instant.now().isBefore(lockedUntil)) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
					"Too many wrong codes. Wait a minute, then use the new code shown on the computer.");
		}
		PairingCode current = current();
		byte[] expected = current.code().getBytes(StandardCharsets.US_ASCII);
		byte[] given = candidate == null ? new byte[0] : candidate.strip().getBytes(StandardCharsets.US_ASCII);
		if (MessageDigest.isEqual(expected, given)) {
			return true;
		}
		if (++failedAttempts >= properties.maxPairingAttempts()) {
			rotate();
			lockedUntil = Instant.now().plus(LOCKOUT.multipliedBy(1L << Math.min(lockouts++, 4)));
		}
		return false;
	}

	@Scheduled(fixedDelay = 15, timeUnit = TimeUnit.SECONDS)
	public synchronized void rotateIfExpired() {
		if (code != null && isExpired()) {
			rotate();
		}
	}

	private boolean isExpired() {
		return !Instant.now().isBefore(expiresAt);
	}

}
