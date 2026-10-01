package com.androiddrop.device;

import java.time.Instant;

public record DeviceView(String id, String name, Platform platform, boolean host, boolean online, Instant pairedAt) {

	public static DeviceView of(Device device, boolean online) {
		return new DeviceView(device.id(), device.name(), device.platform(), device.host(), online, device.pairedAt());
	}

}
