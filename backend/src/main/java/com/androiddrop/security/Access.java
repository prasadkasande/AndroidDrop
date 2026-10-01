package com.androiddrop.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.androiddrop.device.Device;

public final class Access {

	private Access() {
	}

	public static void requireHost(Device device) {
		if (!device.host()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the computer running AndroidDrop can do that.");
		}
	}

}
