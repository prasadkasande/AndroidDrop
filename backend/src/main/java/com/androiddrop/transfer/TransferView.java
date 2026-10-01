package com.androiddrop.transfer;

import java.time.Instant;
import java.util.List;

import com.androiddrop.device.Platform;

/**
 * {@link Transfer} as the API exposes it: no paths of individual files, device names resolved to their current
 * value. {@code folder} is the folder the host picked to save into, if any.
 */
public record TransferView(String id, String senderId, String senderName, Platform senderPlatform,
		String recipientId, String recipientName, Platform recipientPlatform, Transfer.Status status, String text,
		List<FileView> files, Instant createdAt, Instant respondedAt, String folder) {

	/**
	 * {@code available} is false once a stored file was moved or deleted outside the app (e.g. in Finder);
	 * {@code thumbnail} says whether {@code /thumbnail} can serve a preview.
	 */
	public record FileView(String id, String name, long size, String mimeType, TransferFile.State state,
			boolean available, boolean thumbnail, Instant finishedAt) {
	}

}
