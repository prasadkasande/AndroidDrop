package com.androiddrop.device;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A device allowed to use this server: a paired phone or browser, or the host (the computer running the server,
 * recognised by where its requests come from, so only its preferences are saved). Paired devices authenticate with
 * a token per browser (Chrome and Firefox on one phone are one device), of which only the SHA-256 hashes are kept.
 *
 * @param autoAccept  accept incoming transfers without asking (never null; files from older versions may say null)
 * @param tokenHashes one per browser this device is signed in with, oldest first
 * @param saveFolder  host only: where accepted files go unless another folder is picked; null means the storage
 *                    folder
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Device(String id, String name, Platform platform, boolean host, Instant pairedAt, Boolean autoAccept,
		List<String> tokenHashes, String saveFolder) {

	public static final String HOST_ID = "host";

	/** Browsers a device can be signed in with at once; signing in another drops the oldest. */
	static final int MAX_BROWSERS = 8;

	public Device {
		autoAccept = Boolean.TRUE.equals(autoAccept);
		tokenHashes = tokenHashes == null ? List.of() : List.copyOf(tokenHashes);
	}

	Device withName(String newName) {
		return new Device(id, newName, platform, host, pairedAt, autoAccept, tokenHashes, saveFolder);
	}

	/** Signs in one more browser. */
	Device withToken(String hash) {
		List<String> hashes = new ArrayList<>(tokenHashes);
		hashes.add(hash);
		while (hashes.size() > MAX_BROWSERS) {
			hashes.removeFirst();
		}
		return new Device(id, name, platform, host, pairedAt, autoAccept, hashes, saveFolder);
	}

	/** One browser's token swapped for a new one (moving that browser from HTTP to HTTPS). */
	Device replacingToken(String oldHash, String newHash) {
		List<String> hashes = new ArrayList<>(tokenHashes);
		hashes.remove(oldHash);
		return new Device(id, name, platform, host, pairedAt, autoAccept, hashes, saveFolder).withToken(newHash);
	}

	Device withAutoAccept(boolean value) {
		return new Device(id, name, platform, host, pairedAt, value, tokenHashes, saveFolder);
	}

	Device withSaveFolder(String folder) {
		return new Device(id, name, platform, host, pairedAt, autoAccept, tokenHashes, folder);
	}

}
