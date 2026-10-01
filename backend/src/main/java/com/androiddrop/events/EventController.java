package com.androiddrop.events;

import static com.androiddrop.security.DeviceAuthFilter.DEVICE;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.androiddrop.device.Device;

@RestController
public class EventController {

	private final EventHub hub;

	public EventController(EventHub hub) {
		this.hub = hub;
	}

	/**
	 * Live updates: {@code ready}, {@code transfer}, {@code transfer-removed}, {@code transfer-progress},
	 * {@code transfer-delivery}, {@code devices}, and {@code pairing} (host only).
	 */
	@GetMapping(path = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter events(@RequestAttribute(DEVICE) Device me) {
		return hub.connect(me);
	}

}
