package com.androiddrop.transfer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import com.androiddrop.device.Platform;

/**
 * One AirDrop-style send: files (or a text snippet) from one device to another, which the recipient accepts or
 * declines. Names are snapshotted so history still reads well after a device is removed.
 *
 * @param folder where the host saves the files, if it picked a folder when accepting; null means the storage folder
 */
public record Transfer(String id, String senderId, String senderName, Platform senderPlatform, String recipientId,
		String recipientName, Platform recipientPlatform, Status status, String text, List<TransferFile> files,
		Instant createdAt, Instant respondedAt, String folder) {

	public enum Status {

		/** Waiting for the recipient to accept. Files may still be uploading. */
		PENDING,
		/** The recipient said yes; files are saved (or downloaded) as they finish uploading. */
		ACCEPTED,
		DECLINED,
		/** The sender withdrew it before it was delivered. */
		CANCELLED,
		/** Nobody answered in time. */
		EXPIRED;

		/** Still accepting uploads. */
		public boolean isOpen() {
			return this == PENDING || this == ACCEPTED;
		}

	}

	public boolean involves(String deviceId) {
		return senderId.equals(deviceId) || recipientId.equals(deviceId);
	}

	public Optional<TransferFile> file(String fileId) {
		return files.stream().filter(file -> file.id().equals(fileId)).findFirst();
	}

	/** Accepted and every file has reached the recipient. */
	public boolean isComplete() {
		return status == Status.ACCEPTED && files.stream().allMatch(TransferFile::isDelivered);
	}

	Transfer withStatus(Status newStatus, Instant at) {
		return new Transfer(id, senderId, senderName, senderPlatform, recipientId, recipientName, recipientPlatform,
				newStatus, text, files, createdAt, at, folder);
	}

	Transfer acceptedInto(String saveTo, Instant at) {
		return new Transfer(id, senderId, senderName, senderPlatform, recipientId, recipientName, recipientPlatform,
				Status.ACCEPTED, text, files, createdAt, at, saveTo);
	}

	Transfer withFile(TransferFile updated) {
		return withFiles(file -> file.id().equals(updated.id()) ? updated : file);
	}

	Transfer withFiles(UnaryOperator<TransferFile> change) {
		return new Transfer(id, senderId, senderName, senderPlatform, recipientId, recipientName, recipientPlatform,
				status, text, files.stream().map(change).toList(), createdAt, respondedAt, folder);
	}

}
