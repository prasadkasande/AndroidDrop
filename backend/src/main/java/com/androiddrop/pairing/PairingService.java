package com.androiddrop.pairing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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

	private final SecureRandom random = new SecureRandom();
	private final DropProperties properties;
	private final ServerAddresses addresses;
	private final ApplicationEventPublisher events;

	private String code;
	private Instant expiresAt = Instant.EPOCH;
	private int failedAttempts;

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
			return true;
		}
		return false;
	}

	/**
	 * Whether {@code candidate} is the current code, without using it up. Wrong guesses count towards rotating
	 * the code, as with {@link #redeem}.
	 */
	public synchronized boolean check(String candidate) {
		PairingCode current = current();
		byte[] expected = current.code().getBytes(StandardCharsets.US_ASCII);
		byte[] given = candidate == null ? new byte[0] : candidate.strip().getBytes(StandardCharsets.US_ASCII);
		if (MessageDigest.isEqual(expected, given)) {
			return true;
		}
		if (++failedAttempts >= properties.maxPairingAttempts()) {
			rotate();
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
