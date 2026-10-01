package com.androiddrop.transfer;

import java.time.Instant;

/**
 * A file in a {@link Transfer}.
 *
 * @param storedPath where a {@link State#SAVED} file lives; staged files are found by id instead
 * @param finishedAt when the file reached its current state (uploaded, saved, delivered...)
 */
public record TransferFile(String id, String name, long size, String mimeType, State state, String storedPath,
		Instant finishedAt) {

	public enum State {

		/** The sender is still uploading it. */
		UPLOADING,
		/** Uploaded and staged on this computer; waiting for acceptance, or for the phone to download it. */
		READY,
		/** Moved into the host's storage folder (the host was the recipient). */
		SAVED,
		/** A phone recipient downloaded it. Stays staged for a while so it can be saved again. */
		DELIVERED,
		/** The upload broke off. The sender can retry. */
		FAILED,
		/** Discarded: declined, cancelled, expired, or cleaned up after delivery. */
		REMOVED

	}

	boolean isDelivered() {
		return state == State.SAVED || state == State.DELIVERED;
	}

	/** Its bytes are on this computer and can be served. */
	boolean isStored() {
		return state == State.READY || state == State.SAVED || state == State.DELIVERED;
	}

	TransferFile with(State newState, Instant at) {
		return new TransferFile(id, name, size, mimeType, newState, storedPath, at);
	}

	TransferFile savedAs(String newName, String relativePath, Instant at) {
		return new TransferFile(id, newName, size, mimeType, State.SAVED, relativePath, at);
	}

}
