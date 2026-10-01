package com.androiddrop.transfer;

import static com.androiddrop.security.DeviceAuthFilter.DEVICE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.androiddrop.device.Device;
import com.androiddrop.host.HostInfo;
import com.androiddrop.host.SaveFolders;
import com.androiddrop.security.Access;
import com.androiddrop.transfer.TransferService.FileSpec;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

	/** Either {@code files} or {@code text}. */
	public record CreateRequest(String recipientId, List<FileRequest> files, String text) {
	}

	/** Sizes are checked by {@link TransferService#create}; a missing name becomes "file". */
	public record FileRequest(String name, long size, String mimeType) {
	}

	/** {@code folder}: where the host saves the files (absolute path); omitted means the storage folder. */
	public record AcceptRequest(String folder) {
	}

	/**
	 * Types that are safe to render in the browser. Anything else (HTML, SVG, ...) is forced to download, because
	 * it would otherwise run with this app's origin and the viewer's privileges.
	 */
	private static final Set<String> INLINE_PREFIXES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp",
			"image/avif", "image/bmp", "image/heic", "video/", "audio/", "text/plain");

	/** A single range from byte N to the end: {@code bytes=N-}, or {@code bytes=N-M} with M the last byte. */
	private static final Pattern RESUME_RANGE = Pattern.compile("bytes=(\\d{1,18})-(\\d{0,18})");

	private final TransferService transfers;
	private final HostInfo hostInfo;
	private final SaveFolders saveFolders;

	public TransferController(TransferService transfers, HostInfo hostInfo, SaveFolders saveFolders) {
		this.transfers = transfers;
		this.hostInfo = hostInfo;
		this.saveFolders = saveFolders;
	}

	@GetMapping
	public List<TransferView> list(@RequestAttribute(DEVICE) Device me) {
		return transfers.visibleTo(me).stream().map(transfers::view).toList();
	}

	/** Step 1: announce what you're sending. The recipient is asked right away, while the files upload. */
	@PostMapping
	public TransferView create(@RequestAttribute(DEVICE) Device me, @RequestBody CreateRequest request) {
		if (request.recipientId() == null || request.recipientId().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Say who to send to.");
		}
		List<FileSpec> files = request.files() == null ? List.of()
				: request.files().stream().map(file -> new FileSpec(file.name(), file.size(), file.mimeType())).toList();
		return transfers.view(transfers.create(me, request.recipientId(), files, request.text()));
	}

	/**
	 * Step 2: upload each announced file as the raw request body, streamed straight to disk. After a broken
	 * connection, {@code offset} continues from what already arrived (see {@link #received}).
	 */
	@PutMapping("/{id}/files/{fileId}")
	public TransferView upload(@RequestAttribute(DEVICE) Device me, @PathVariable String id,
			@PathVariable String fileId, @RequestParam(defaultValue = "0") long offset, HttpServletRequest request)
			throws IOException {
		return transfers.view(transfers.receiveFile(me, id, fileId, offset, request.getContentLengthLong(),
				request.getInputStream()));
	}

	/** How much of an interrupted upload arrived: where the sender continues from. */
	@GetMapping("/{id}/files/{fileId}/received")
	public Map<String, Long> received(@RequestAttribute(DEVICE) Device me, @PathVariable String id,
			@PathVariable String fileId) throws IOException {
		return Map.of("received", transfers.receivedBytes(me, id, fileId));
	}

	@PostMapping("/{id}/accept")
	public TransferView accept(@RequestAttribute(DEVICE) Device me, @PathVariable String id,
			@RequestBody(required = false) AcceptRequest request) {
		Path folder = null;
		if (request != null && request.folder() != null) {
			// Phones save into Downloads; only the computer chooses folders, and only on itself.
			Access.requireHost(me);
			folder = saveFolders.validate(request.folder());
		}
		Transfer accepted = transfers.accept(me, id, folder);
		if (folder != null) {
			saveFolders.remember(folder);
		}
		return transfers.view(accepted);
	}

	@PostMapping("/{id}/decline")
	public TransferView decline(@RequestAttribute(DEVICE) Device me, @PathVariable String id) {
		return transfers.view(transfers.decline(me, id));
	}

	@PostMapping("/{id}/cancel")
	public TransferView cancel(@RequestAttribute(DEVICE) Device me, @PathVariable String id) {
		return transfers.view(transfers.cancel(me, id));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void remove(@RequestAttribute(DEVICE) Device me, @PathVariable String id) {
		transfers.remove(me, id);
	}

	/**
	 * Serves a file. A download by the phone that received it is streamed by hand so we know when it finished and
	 * can tell the sender it was delivered; it can resume from where a dropped connection left off. Everything else
	 * (previews, video seeking) goes through Spring's Resource handling, Range requests included.
	 */
	@GetMapping("/{id}/files/{fileId}/content")
	public ResponseEntity<Resource> content(@RequestAttribute(DEVICE) Device me, @PathVariable String id,
			@PathVariable String fileId, @RequestParam(defaultValue = "false") boolean download,
			@RequestHeader(name = HttpHeaders.RANGE, required = false) String range,
			@RequestHeader(name = HttpHeaders.IF_RANGE, required = false) String ifRange, HttpServletResponse response)
			throws IOException {
		Transfer transfer = transfers.get(me, id);
		TransferFile file = transfers.readableFile(me, transfer, fileId);
		Path path = transfers.contentPath(transfer, file);
		if (!Files.isRegularFile(path)) {
			throw new ResponseStatusException(HttpStatus.GONE, "The file was moved or deleted on the host.");
		}
		boolean inline = !download && INLINE_PREFIXES.stream().anyMatch(file.mimeType()::startsWith);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(inline ? MediaType.parseMediaType(file.mimeType()) : MediaType.APPLICATION_OCTET_STREAM);
		headers.setContentDisposition((inline ? ContentDisposition.inline() : ContentDisposition.attachment())
			.filename(file.name(), StandardCharsets.UTF_8)
			.build());
		headers.set("X-Content-Type-Options", "nosniff");
		headers.set("Content-Security-Policy", "sandbox");

		boolean delivery = download && transfer.recipientId().equals(me.id()) && !me.host();
		if (delivery) {
			long size = Files.size(path);
			// The file never changes while it's here, so its id is a validator the browser can resume against.
			String etag = "\"" + file.id() + "\"";
			long from = ifRange == null || ifRange.equals(etag) ? resumeFrom(range, size) : 0;
			headers.setETag(etag);
			headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
			headers.forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
			if (from > 0) {
				response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
				response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes " + from + "-" + (size - 1) + "/" + size);
			}
			response.setContentLengthLong(size - from);
			transfers.deliver(me, transfer, file, path, from, response.getOutputStream());
			return null; // response already written
		}
		headers.setCacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate());
		return ResponseEntity.ok().headers(headers).body(new FileSystemResource(path));
	}

	/**
	 * Where a download resumes: the start of a {@code bytes=N-} range (what a browser sends to continue one), or 0
	 * for anything else, which then gets the whole file. That's allowed: a server may always ignore Range.
	 */
	static long resumeFrom(String range, long size) {
		Matcher matcher = range == null ? null : RESUME_RANGE.matcher(range.strip());
		if (matcher == null || !matcher.matches()) {
			return 0;
		}
		long from = Long.parseLong(matcher.group(1));
		boolean toTheEnd = matcher.group(2).isEmpty() || Long.parseLong(matcher.group(2)) >= size - 1;
		return toTheEnd && from < size ? from : 0;
	}

	@GetMapping("/{id}/files/{fileId}/thumbnail")
	public ResponseEntity<Resource> thumbnail(@RequestAttribute(DEVICE) Device me, @PathVariable String id,
			@PathVariable String fileId) {
		Path thumbnail = transfers.thumbnail(me, id, fileId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No preview for this file."));
		return ResponseEntity.ok()
			.cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
			.contentType(MediaType.IMAGE_JPEG)
			.header("X-Content-Type-Options", "nosniff")
			.body(new FileSystemResource(thumbnail));
	}

	/** Shows the file in Finder. Host only, since it acts on the host's screen. */
	@PostMapping("/{id}/files/{fileId}/reveal")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reveal(@RequestAttribute(DEVICE) Device me, @PathVariable String id, @PathVariable String fileId)
			throws IOException {
		Access.requireHost(me);
		Transfer transfer = transfers.get(me, id);
		Path path = transfers.contentPath(transfer, transfers.readableFile(me, transfer, fileId));
		if (!Files.exists(path)) {
			throw new ResponseStatusException(HttpStatus.GONE, "The file was moved or deleted.");
		}
		hostInfo.reveal(path);
	}

}
