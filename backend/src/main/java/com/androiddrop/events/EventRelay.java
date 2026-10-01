package com.androiddrop.events;

import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.androiddrop.device.DeviceService;
import com.androiddrop.pairing.PairingService;
import com.androiddrop.transfer.Transfer;
import com.androiddrop.transfer.TransferService;

/** Pushes domain events to the devices allowed to see them. A transfer is only ever seen by its two ends. */
@Component
class EventRelay {

	private final EventHub hub;
	private final DeviceService devices;
	private final TransferService transfers;
	private final PairingService pairing;

	EventRelay(EventHub hub, DeviceService devices, TransferService transfers, PairingService pairing) {
		this.hub = hub;
		this.devices = devices;
		this.transfers = transfers;
		this.pairing = pairing;
	}

	@EventListener
	void on(DropEvent.TransferChanged event) {
		Transfer transfer = event.transfer();
		hub.broadcast("transfer", transfers.view(transfer), client -> transfer.involves(client.deviceId()));
	}

	@EventListener
	void on(DropEvent.TransferRemoved event) {
		Transfer transfer = event.transfer();
		hub.broadcast("transfer-removed", Map.of("id", transfer.id()), client -> transfer.involves(client.deviceId()));
	}

	@EventListener
	void on(DropEvent.TransferProgress event) {
		Transfer transfer = event.transfer();
		hub.broadcast("transfer-progress",
				Map.of("id", transfer.id(), "fileId", event.fileId(), "received", event.received()),
				client -> transfer.involves(client.deviceId()));
	}

	@EventListener
	void on(DropEvent.DeliveryProgress event) {
		Transfer transfer = event.transfer();
		hub.broadcast("transfer-delivery", Map.of("id", transfer.id(), "fileId", event.fileId(), "sent", event.sent()),
				client -> transfer.involves(client.deviceId()));
	}

	@EventListener
	void on(DropEvent.DevicesChanged event) {
		hub.broadcast("devices", devices.views(hub.onlineDeviceIds()), client -> true);
	}

	@EventListener
	void on(DropEvent.DeviceRevoked event) {
		hub.disconnect(event.deviceId());
	}

	@EventListener
	void on(DropEvent.PairingCodeChanged event) {
		hub.broadcast("pairing", pairing.info(), EventHub.Client::host);
	}

}
