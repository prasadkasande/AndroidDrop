package com.androiddrop.pairing;

import static com.androiddrop.security.DeviceAuthFilter.DEVICE;
import static com.androiddrop.security.DeviceAuthFilter.TOKEN_COOKIE;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.androiddrop.device.Device;
import com.androiddrop.device.DeviceService;
import com.androiddrop.device.DeviceService.IssuedDevice;
import com.androiddrop.device.DeviceView;
import com.androiddrop.device.Platform;
import com.androiddrop.pairing.PairingService.PairingInfo;
import com.androiddrop.security.Access;
import com.androiddrop.security.DeviceAuthFilter;
import com.androiddrop.storage.StorageLayout;
import com.androiddrop.tls.SecurePort;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
public class SessionController {

	/**
	 * Who the caller is. {@code autoAccept} is this device's own preference; {@code storageDir} and
	 * {@code freeBytes} are only filled in for the host. {@code serverRun} changes every time the server starts, so a
	 * phone knows when everything it received was forgotten and can drop its own copies too.
	 */
	public record SessionView(DeviceView device, boolean host, boolean autoAccept, String serverName, String serverRun,
			String storageDir, Long freeBytes) {
	}

	public record PreferencesRequest(Boolean autoAccept) {
	}

	public record RenameRequest(String name) {
	}

	/** {@code httpsPort}: where this server also speaks HTTPS, or null if it doesn't. */
	public record ServerView(String name, Integer httpsPort) {
	}

	public record HandoffView(String code) {
	}

	public record HandoffRequest(String code) {
	}

	/** {@code deviceId}: sign this browser in as that already paired device instead of pairing a new one. */
	public record PairRequest(String code, String name, String deviceId) {
	}

	public record CodeRequest(String code) {
	}

	/** A paired device a new browser could sign in as. */
	public record PairOption(String id, String name, Platform platform) {
	}

	private static final Duration TOKEN_LIFETIME = Duration.ofDays(365);
	private static final int MAX_NAME_LENGTH = 40;

	private final PairingService pairing;
	private final DeviceService devices;
	private final StorageLayout storage;
	private final SecurePort securePort;
	private final String run = UUID.randomUUID().toString();

	public SessionController(PairingService pairing, DeviceService devices, StorageLayout storage,
			SecurePort securePort) {
		this.pairing = pairing;
		this.devices = devices;
		this.storage = storage;
		this.securePort = securePort;
	}

	/** Public: lets an unpaired phone show which computer it is about to pair with. */
	@GetMapping("/server")
	public ServerView server() {
		return new ServerView(devices.host().name(), securePort.enabled() ? securePort.port() : null);
	}

	@GetMapping("/session")
	public SessionView session(@RequestAttribute(DEVICE) Device me) {
		return sessionOf(me);
	}

	/** Public: exchanges the code shown on the host for a long-lived device token (HttpOnly cookie). */
	@PostMapping("/pair")
	public ResponseEntity<SessionView> pair(@RequestAttribute(name = DEVICE, required = false) Device me,
			@RequestBody PairRequest request,
			@RequestHeader(name = HttpHeaders.USER_AGENT, required = false) String userAgent, HttpServletRequest http) {
		if (me != null && me.host()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "This computer runs the server, so it doesn't need pairing.");
		}
		if (me != null) {
			return ResponseEntity.ok(sessionOf(me));
		}
		// Joining an already paired device keeps that device's name.
		String name = request.deviceId() == null ? requireName(request.name()) : null;
		if (!pairing.redeem(requireCode(request.code()))) {
			throw wrongCode();
		}
		IssuedDevice issued = name != null ? devices.register(name, Platform.fromUserAgent(userAgent))
				: devices.addBrowser(request.deviceId())
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "That device is no longer paired."));
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, tokenCookie(issued.token(), TOKEN_LIFETIME, http.isSecure()).toString())
			.body(sessionOf(issued.device()));
	}

	/**
	 * Public, but needs the current code: the devices already paired, so a second browser on a paired phone can
	 * sign in as that phone instead of showing up as a new device. Doesn't use up the code.
	 */
	@PostMapping("/pair/devices")
	public List<PairOption> pairOptions(@RequestBody CodeRequest request) {
		if (!pairing.check(requireCode(request.code()))) {
			throw wrongCode();
		}
		return devices.paired().stream().map(device -> new PairOption(device.id(), device.name(), device.platform())).toList();
	}

	/** Starts moving this browser's pairing to the server's HTTPS address; see {@link #finishHandoff}. */
	@PostMapping("/session/handoff")
	public HandoffView startHandoff(@RequestAttribute(DEVICE) Device me,
			@RequestAttribute(name = DeviceAuthFilter.TOKEN, required = false) String token) {
		if (me.host()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This computer doesn't need to switch.");
		}
		return new HandoffView(devices.startHandoff(me.id(), token));
	}

	/** Public: redeems a handoff code on the new address, so the phone stays paired there. */
	@PostMapping("/pair/handoff")
	public ResponseEntity<SessionView> finishHandoff(@RequestBody HandoffRequest request, HttpServletRequest http) {
		IssuedDevice issued = devices.finishHandoff(request.code())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "That link has expired. Open AndroidDrop again."));
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, tokenCookie(issued.token(), TOKEN_LIFETIME, http.isSecure()).toString())
			.body(sessionOf(issued.device()));
	}

	/** Renames the calling device. The host's name comes from the computer (or {@code drop.host-name}). */
	@PatchMapping("/session")
	public SessionView rename(@RequestAttribute(DEVICE) Device me, @RequestBody RenameRequest request) {
		if (me.host()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"The server's name comes from this computer's name (or the drop.host-name setting).");
		}
		return devices.rename(me.id(), requireName(request.name()))
			.map(this::sessionOf)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "This device isn't paired."));
	}

	/** Whether transfers sent to this device are accepted without asking. */
	@PatchMapping("/session/preferences")
	public SessionView preferences(@RequestAttribute(DEVICE) Device me, @RequestBody PreferencesRequest request) {
		if (request.autoAccept() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Say whether to accept automatically.");
		}
		return sessionOf(devices.setAutoAccept(me.id(), request.autoAccept()));
	}

	/** Un-pairs the calling device. */
	@PostMapping("/session/forget")
	public ResponseEntity<Void> forget(@RequestAttribute(DEVICE) Device me, HttpServletRequest http) {
		if (me.host()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The host can't un-pair itself.");
		}
		devices.revoke(me.id());
		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, tokenCookie("", Duration.ZERO, http.isSecure()).toString())
			.build();
	}

	@GetMapping("/host/pairing")
	public PairingInfo pairingInfo(@RequestAttribute(DEVICE) Device me) {
		Access.requireHost(me);
		return pairing.info();
	}

	@PostMapping("/host/pairing/rotate")
	public PairingInfo rotatePairingCode(@RequestAttribute(DEVICE) Device me) {
		Access.requireHost(me);
		pairing.rotate();
		return pairing.info();
	}

	/** A pairing code has 6 digits. Anything else is refused before it counts as a wrong guess. */
	private static String requireCode(String code) {
		if (code == null || !code.strip().matches("\\d{6}")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The code is 6 digits.");
		}
		return code.strip();
	}

	private static String requireName(String name) {
		if (name == null || name.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Give this device a name.");
		}
		if (name.strip().length() > MAX_NAME_LENGTH) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Names can be at most " + MAX_NAME_LENGTH + " characters.");
		}
		return name.strip();
	}

	private ResponseStatusException wrongCode() {
		return new ResponseStatusException(HttpStatus.FORBIDDEN,
				"That code is wrong or has expired. Check the code shown on " + devices.host().name() + ".");
	}

	private SessionView sessionOf(Device device) {
		DeviceView view = DeviceView.of(device, true);
		if (!device.host()) {
			return new SessionView(view, false, device.autoAccept(), devices.host().name(), run, null, null);
		}
		try {
			long free = Files.getFileStore(storage.root()).getUsableSpace();
			return new SessionView(view, true, device.autoAccept(), devices.host().name(), run, storage.root().toString(),
					free);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/**
	 * Marked Secure when set over HTTPS, so it's never sent over plain HTTP afterwards. SameSite=Strict keeps other
	 * sites from riding on it.
	 */
	private static ResponseCookie tokenCookie(String value, Duration maxAge, boolean secure) {
		return ResponseCookie.from(TOKEN_COOKIE, value)
			.httpOnly(true)
			.secure(secure)
			.sameSite("Strict")
			.path("/")
			.maxAge(maxAge)
			.build();
	}

}
