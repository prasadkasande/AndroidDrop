package com.androiddrop.device;

import static com.androiddrop.security.DeviceAuthFilter.DEVICE;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.androiddrop.events.EventHub;
import com.androiddrop.security.Access;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

	private final DeviceService devices;
	private final EventHub hub;

	public DeviceController(DeviceService devices, EventHub hub) {
		this.devices = devices;
		this.hub = hub;
	}

	@GetMapping
	public List<DeviceView> list() {
		return devices.views(hub.onlineDeviceIds());
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void revoke(@RequestAttribute(DEVICE) Device me, @PathVariable String id) {
		Access.requireHost(me);
		if (!devices.revoke(id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No paired device with that id.");
		}
	}

}
