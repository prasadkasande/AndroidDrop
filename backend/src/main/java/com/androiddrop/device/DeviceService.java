package com.androiddrop.device;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.androiddrop.events.DropEvent;
import com.androiddrop.host.HostInfo;
import com.androiddrop.storage.JsonStore;
import com.androiddrop.storage.StorageLayout;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class DeviceService {

	/** A freshly paired device together with the only copy of its plain-text token. */
	public record IssuedDevice(Device device, String token) {
	}

	/**
	 * A browser of a paired device moving to another address of this server (HTTP to HTTPS), for a short while.
	 * {@code tokenHash} is that browser's current token, which the move replaces.
	 */
	private record Handoff(String deviceId, String tokenHash, Instant expiresAt) {
	}

	private static final Duration HANDOFF_TTL = Duration.ofMinutes(1);

	private final Map<String, Device> paired = new ConcurrentHashMap<>();
	private final Map<String, Handoff> handoffs = new ConcurrentHashMap<>();
	private final SecureRandom random = new SecureRandom();
	/** Paired devices and the host's preferences: the only thing AndroidDrop keeps once it stops. */
	private final JsonStore<List<Device>> store;
	private final ApplicationEventPublisher events;
	private volatile Device host;

	public DeviceService(StorageLayout storage, JsonMapper mapper, HostInfo hostInfo, ApplicationEventPublisher events) {
		this.store = new JsonStore<>(storage.metaDir().resolve("devices.json"), mapper, new TypeReference<>() {
		});
		this.events = events;
		List<Device> saved = store.load(List::of);
		// The host's name and platform come from the machine; only its preferences are saved.
		Device savedHost = saved.stream().filter(Device::host).findFirst().orElse(null);
		this.host = new Device(Device.HOST_ID, hostInfo.name(), hostInfo.platform(), true, Instant.now(),
				savedHost != null && savedHost.autoAccept(), List.of(), savedHost == null ? null : savedHost.saveFolder());
		saved.stream().filter(device -> !device.host()).forEach(device -> paired.put(device.id(), device));
	}

	public Device host() {
		return host;
	}

	public Optional<Device> find(String id) {
		return Device.HOST_ID.equals(id) ? Optional.of(host) : Optional.ofNullable(paired.get(id));
	}

	public Optional<Device> findByToken(String token) {
		if (token == null || token.isBlank()) {
			return Optional.empty();
		}
		byte[] hash = hash(token).getBytes(StandardCharsets.US_ASCII);
		return paired.values()
			.stream()
			.filter(device -> device.tokenHashes()
				.stream()
				.anyMatch(known -> MessageDigest.isEqual(known.getBytes(StandardCharsets.US_ASCII), hash)))
			.findFirst();
	}

	/** Paired devices other than the host, in the order they were paired. */
	public List<Device> paired() {
		return paired.values().stream().sorted(Comparator.comparing(Device::pairedAt)).toList();
	}

	public synchronized IssuedDevice register(String name, Platform platform) {
		String token = newToken();
		Device device = new Device(UUID.randomUUID().toString(), name, platform, false, Instant.now(), false,
				List.of(hash(token)), null);
		paired.put(device.id(), device);
		save();
		events.publishEvent(new DropEvent.DevicesChanged());
		return new IssuedDevice(device, token);
	}

	/** Signs one more browser in as an already paired device, say Firefox on a phone already paired in Chrome. */
	public synchronized Optional<IssuedDevice> addBrowser(String deviceId) {
		Device current = paired.get(deviceId);
		if (current == null) {
			return Optional.empty();
		}
		String token = newToken();
		Device updated = current.withToken(hash(token));
		paired.put(deviceId, updated);
		save();
		return Optional.of(new IssuedDevice(updated, token));
	}

	/**
	 * A one-time code that carries a device's pairing to another address of this server. Cookies are per address,
	 * so a phone moving from HTTP to HTTPS would otherwise look unpaired there.
	 */
	public String startHandoff(String deviceId, String currentToken) {
		Instant now = Instant.now();
		handoffs.values().removeIf(handoff -> handoff.expiresAt().isBefore(now));
		String code = newToken();
		handoffs.put(code, new Handoff(deviceId, currentToken == null ? null : hash(currentToken), now.plus(HANDOFF_TTL)));
		return code;
	}

	/** Redeems a handoff code, once. The browser gets a new token; the one it had before stops working. */
	public synchronized Optional<IssuedDevice> finishHandoff(String code) {
		Handoff handoff = code == null ? null : handoffs.remove(code);
		if (handoff == null || handoff.expiresAt().isBefore(Instant.now())) {
			return Optional.empty();
		}
		Device current = paired.get(handoff.deviceId());
		if (current == null) {
			return Optional.empty();
		}
		String token = newToken();
		Device updated = current.replacingToken(handoff.tokenHash(), hash(token));
		paired.put(updated.id(), updated);
		save();
		return Optional.of(new IssuedDevice(updated, token));
	}

	public synchronized Optional<Device> rename(String id, String name) {
		Device current = paired.get(id);
		if (current == null) {
			return Optional.empty();
		}
		Device renamed = current.withName(name);
		paired.put(id, renamed);
		save();
		events.publishEvent(new DropEvent.DevicesChanged());
		return Optional.of(renamed);
	}

	/** Whether transfers sent to this device skip the Accept/Decline prompt. */
	public synchronized Device setAutoAccept(String id, boolean value) {
		if (Device.HOST_ID.equals(id)) {
			host = host.withAutoAccept(value);
			save();
			return host;
		}
		Device current = paired.get(id);
		if (current == null) {
			throw new IllegalArgumentException("Unknown device " + id);
		}
		Device updated = current.withAutoAccept(value);
		paired.put(id, updated);
		save();
		return updated;
	}

	/** Where the host saves accepted files unless another folder is picked; null means the storage folder. */
	public synchronized void setSaveFolder(String folder) {
		host = host.withSaveFolder(folder);
		save();
	}

	public synchronized boolean revoke(String id) {
		if (paired.remove(id) == null) {
			return false;
		}
		save();
		events.publishEvent(new DropEvent.DeviceRevoked(id));
		events.publishEvent(new DropEvent.DevicesChanged());
		return true;
	}

	/** The host first, then paired devices in the order they were paired. */
	public List<DeviceView> views(Set<String> onlineIds) {
		return Stream.concat(Stream.of(host), paired().stream())
			.map(device -> DeviceView.of(device, device.host() || onlineIds.contains(device.id())))
			.toList();
	}

	private void save() {
		List<Device> all = new ArrayList<>(List.of(host));
		all.addAll(paired());
		store.save(all);
	}

	private String newToken() {
		byte[] secret = new byte[32];
		random.nextBytes(secret);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
	}

	private static String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
