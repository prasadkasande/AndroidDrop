package com.androiddrop.events;

import com.androiddrop.transfer.Transfer;

/** Application events that {@link EventRelay} turns into Server-Sent Events for connected devices. */
public sealed interface DropEvent {

	/** A transfer was created or changed state (accepted, a file finished uploading, ...). */
	record TransferChanged(Transfer transfer) implements DropEvent {
	}

	record TransferRemoved(Transfer transfer) implements DropEvent {
	}

	/** Bytes received so far for a file that is uploading. Throttled by the sender of the event. */
	record TransferProgress(Transfer transfer, String fileId, long received) implements DropEvent {
	}

	/** Bytes sent so far of a file a phone is downloading after accepting it. Throttled like uploads. */
	record DeliveryProgress(Transfer transfer, String fileId, long sent) implements DropEvent {
	}

	/** A device was paired, revoked, renamed, came online or went offline. */
	record DevicesChanged() implements DropEvent {
	}

	record DeviceRevoked(String deviceId) implements DropEvent {
	}

	record PairingCodeChanged() implements DropEvent {
	}

}
