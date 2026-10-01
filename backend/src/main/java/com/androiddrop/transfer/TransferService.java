package com.androiddrop.transfer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.server.ResponseStatusException;

import com.androiddrop.config.DropProperties;
import com.androiddrop.device.Device;
import com.androiddrop.device.DeviceService;
import com.androiddrop.events.DropEvent;
import com.androiddrop.host.KeepAwake;
import com.androiddrop.host.SaveFolders;
import com.androiddrop.storage.StorageLayout;
import com.androiddrop.transfer.Transfer.Status;
import com.androiddrop.transfer.TransferFile.State;


/**
 * AirDrop-style transfers. The sender announces the files, then uploads them into staging straight away, so the
 * phone can lock its screen as soon as it's done. Nothing reaches the recipient until they accept:
 * <ul>
 * <li>host recipient: accepted files are moved into the storage folder (and show up in Finder);</li>
 * <li>phone recipient: accepted files are downloaded by its browser, and marked delivered once fully served.</li>
 * </ul>
 * Declined, cancelled and expired transfers have their staged files deleted. Transfers live in memory only: once the
 * server stops they are forgotten, and {@link StorageLayout} empties staging.
 */
@Service
public class TransferService {

	private static final Logger log = LoggerFactory.getLogger(TransferService.class);

	private static final Pattern MIME_TYPE = Pattern.compile("^[\\w.+-]+/[\\w.+-]+$");
	private static final long FREE_SPACE_MARGIN = 50L * 1024 * 1024;
	private static final int MAX_FILES = 500;
	private static final long PROGRESS_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(250);
	/** How long files accepted by a phone wait on this computer for it to download them. */
	private static final Duration UNCLAIMED_RETENTION = Duration.ofDays(1);

	/** What the sender announces for each file before uploading it. */
	public record FileSpec(String name, long size, String mimeType) {
	}

	private final Map<String, Transfer> transfers = new ConcurrentHashMap<>();
	/** Files with an upload in progress; a second upload of the same file would write into the same part file. */
	private final Set<String> receiving = ConcurrentHashMap.newKeySet();
	private final Object fileNameLock = new Object();
	private final StorageLayout storage;
	private final DeviceService devices;
	private final Thumbnails thumbnails;
	private final DropProperties properties;
	private final ApplicationEventPublisher events;
	private final SaveFolders saveFolders;
	private final KeepAwake keepAwake;

	public TransferService(StorageLayout storage, DeviceService devices, Thumbnails thumbnails,
			DropProperties properties, ApplicationEventPublisher events, SaveFolders saveFolders, KeepAwake keepAwake) {
		this.storage = storage;
		this.devices = devices;
		this.thumbnails = thumbnails;
		this.properties = properties;
		this.events = events;
		this.saveFolders = saveFolders;
		this.keepAwake = keepAwake;
	}

	/** Transfers this device sent or received, newest first. */
	public List<Transfer> visibleTo(Device me) {
		return transfers.values()
			.stream()
			.filter(transfer -> transfer.involves(me.id()))
			.sorted(Comparator.comparing(Transfer::createdAt).reversed())
			.toList();
	}

	public Transfer get(Device me, String id) {
		return Optional.ofNullable(transfers.get(id))
			.filter(transfer -> transfer.involves(me.id()))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such transfer."));
	}

	/** Announces a transfer. Files then arrive one by one through {@link #receiveFile}. */
	public Transfer create(Device sender, String recipientId, List<FileSpec> files, String text) {
		Device recipient = devices.find(recipientId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "That device is no longer paired."));
		if (recipient.id().equals(sender.id())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can't send to yourself.");
		}
		boolean hasText = text != null && !text.isBlank();
		boolean hasFiles = files != null && !files.isEmpty();
		if (hasText == hasFiles) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Send either files or text.");
		}
		if (hasText && text.length() > properties.maxTextLength()) {
			throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE,
					"Text is longer than " + properties.maxTextLength() + " characters. Send it as a file instead.");
		}
		if (hasFiles && files.size() > MAX_FILES) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Send at most " + MAX_FILES + " files at once.");
		}
		List<FileSpec> specs = hasFiles ? files : List.of();
		if (specs.stream().anyMatch(spec -> spec.size() < 0)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File sizes can't be negative.");
		}
		requireFreeSpace(specs.stream().mapToLong(FileSpec::size).sum());

		String id = UUID.randomUUID().toString();
		Instant now = Instant.now();
		List<TransferFile> announced = new ArrayList<>();
		for (FileSpec spec : specs) {
			String name = FileNames.sanitize(spec.name());
			TransferFile file = new TransferFile(UUID.randomUUID().toString(), name, spec.size(),
					mimeType(spec.mimeType(), name), spec.size() == 0 ? State.READY : State.UPLOADING, null,
					spec.size() == 0 ? now : null);
			if (spec.size() == 0) {
				stageEmptyFile(id, file.id());
			}
			announced.add(file);
		}
		boolean autoAccept = recipient.autoAccept();
		Transfer transfer = new Transfer(id, sender.id(), sender.name(), sender.platform(), recipient.id(),
				recipient.name(), recipient.platform(), autoAccept ? Status.ACCEPTED : Status.PENDING,
				hasText ? text : null, List.copyOf(announced), now, autoAccept ? now : null,
				autoAccept && recipient.host() ? hostFolder(null) : null);
		if (autoAccept) {
			transfer = deliverReadyFiles(transfer);
		}
		synchronized (this) {
			transfers.put(id, transfer);
		}
		events.publishEvent(new DropEvent.TransferChanged(transfer));
		return transfer;
	}

	/**
	 * Streams one announced file into staging, reporting progress and stopping if the transfer is withdrawn. An
	 * upload that breaks off keeps what arrived, so the sender can continue it: {@code offset} is where this request's
	 * body starts, which must be what {@link #receivedBytes} says has arrived (0 starts over).
	 */
	public Transfer receiveFile(Device sender, String transferId, String fileId, long offset, long contentLength,
			InputStream body) throws IOException {
		Transfer transfer = get(sender, transferId);
		TransferFile file = uploadableFile(sender, transfer, fileId);
		if (offset < 0 || offset > file.size()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That position is outside the file.");
		}
		if (contentLength >= 0 && contentLength != file.size() - offset) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file size doesn't match what was announced.");
		}
		if (!receiving.add(fileId)) {
			// Usually the connection that just broke, which the server hasn't noticed yet.
			throw new ResponseStatusException(HttpStatus.LOCKED, "That file is still uploading. Try again in a moment.");
		}
		Path part = partPath(fileId);
		try {
			long arrived = Files.exists(part) ? Files.size(part) : 0;
			if (offset != 0 && offset != arrived) {
				throw new ResponseStatusException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
						"Continue this upload from byte " + arrived + ".");
			}
			if (file.state() == State.FAILED) {
				update(transferId, current -> current.withFile(file.with(State.UPLOADING, null)));
			}
			requireFreeSpace(file.size() - offset);
			Files.createDirectories(part.getParent());
			long written;
			try (KeepAwake.Hold awake = keepAwake.hold()) {
				written = copyWithProgress(transferId, file, body, part, offset);
			}
			if (written != file.size()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The upload was cut off. Try again.");
			}
			Path staged = stagedPath(transferId, fileId);
			Files.createDirectories(staged.getParent());
			Files.move(part, staged, StandardCopyOption.REPLACE_EXISTING);
			return update(transferId, current -> {
				if (!current.status().isOpen()) {
					deleteQuietly(staged);
					return current;
				}
				Transfer next = current.withFile(file.with(State.READY, Instant.now()));
				return next.status() == Status.ACCEPTED ? deliverReadyFiles(next) : next;
			});
		}
		catch (IOException | RuntimeException ex) {
			markFailed(transferId, fileId);
			// What arrived stays for the sender to continue, unless the transfer was withdrawn or removed meanwhile.
			Transfer current = transfers.get(transferId);
			if (current == null || !current.status().isOpen()) {
				deleteQuietly(part);
			}
			if (current == null) {
				deleteQuietly(stagedPath(transferId, fileId));
			}
			throw ex;
		}
		finally {
			receiving.remove(fileId);
		}
	}

	/** How much of a file's interrupted upload has arrived, for the sender to continue from. */
	public long receivedBytes(Device sender, String transferId, String fileId) throws IOException {
		uploadableFile(sender, get(sender, transferId), fileId);
		Path part = partPath(fileId);
		return Files.exists(part) ? Files.size(part) : 0;
	}

	private TransferFile uploadableFile(Device sender, Transfer transfer, String fileId) {
		if (!transfer.senderId().equals(sender.id())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the sender can upload files.");
		}
		TransferFile file = transfer.file(fileId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such file in this transfer."));
		if (!transfer.status().isOpen()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "This transfer was " + describe(transfer.status()) + ".");
		}
		if (file.state() != State.UPLOADING && file.state() != State.FAILED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "That file was already uploaded.");
		}
		return file;
	}

	/** Accepts a transfer. {@code folder} is where the host saves the files; null means the storage folder. */
	public Transfer accept(Device me, String id, Path folder) {
		requireRecipient(me, get(me, id));
		String saveTo = me.host() ? hostFolder(folder) : null;
		return update(id, current -> switch (current.status()) {
			case ACCEPTED -> current;
			case PENDING -> deliverReadyFiles(current.acceptedInto(saveTo, Instant.now()));
			default -> throw new ResponseStatusException(HttpStatus.CONFLICT,
					"This transfer was already " + describe(current.status()) + ".");
		});
	}

	public Transfer decline(Device me, String id) {
		requireRecipient(me, get(me, id));
		return update(id, current -> {
			if (current.status() == Status.DECLINED) {
				return current;
			}
			if (current.status() != Status.PENDING) {
				throw new ResponseStatusException(HttpStatus.CONFLICT,
						"This transfer was already " + describe(current.status()) + ".");
			}
			return discardStaged(current).withStatus(Status.DECLINED, Instant.now());
		});
	}

	/** The sender withdraws a transfer that hasn't been fully delivered yet. */
	public Transfer cancel(Device me, String id) {
		Transfer transfer = get(me, id);
		if (!transfer.senderId().equals(me.id())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the sender can cancel a transfer.");
		}
		return update(id, current -> {
			if (!current.status().isOpen() || current.isComplete()) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "There's nothing left to cancel.");
			}
			return discardStaged(current).withStatus(Status.CANCELLED, Instant.now());
		});
	}

	/**
	 * Removes a transfer from history for both sides. Staged copies are deleted; files already saved into the
	 * storage folder are the user's now and are left alone.
	 */
	public void remove(Device me, String id) {
		Transfer transfer = get(me, id);
		synchronized (this) {
			if (transfers.remove(id) == null) {
				return;
			}
		}
		discardStaged(transfer);
		events.publishEvent(new DropEvent.TransferRemoved(transfer));
	}

	/**
	 * A file this device may download: the sender always, the recipient once accepted. Thumbnails are more
	 * lenient (see {@link #thumbnail}).
	 */
	public TransferFile readableFile(Device me, Transfer transfer, String fileId) {
		TransferFile file = transfer.file(fileId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such file in this transfer."));
		if (transfer.recipientId().equals(me.id()) && transfer.status() != Status.ACCEPTED) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accept the transfer first.");
		}
		if (file.state() == State.UPLOADING) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "That file is still uploading.");
		}
		if (!file.isStored()) {
			throw new ResponseStatusException(HttpStatus.GONE, "That file is no longer available.");
		}
		return file;
	}

	public Path contentPath(Transfer transfer, TransferFile file) {
		return file.state() == State.SAVED ? Path.of(file.storedPath()) : stagedPath(transfer.id(), file.id());
	}

	/** Recipients may see photo previews before accepting, like AirDrop's request prompt. */
	public Optional<Path> thumbnail(Device me, String transferId, String fileId) {
		Transfer transfer = get(me, transferId);
		return transfer.file(fileId)
			.filter(TransferFile::isStored)
			.filter(file -> Thumbnails.supports(file.mimeType()))
			.flatMap(file -> {
				Path path = contentPath(transfer, file);
				return Files.isRegularFile(path) ? thumbnails.thumbnailFor(file.id(), path, file.mimeType())
						: Optional.empty();
			});
	}

	/**
	 * Streams a file, from byte {@code from} on (a download resuming after Wi-Fi dropped), to the phone that received
	 * it. Reports progress to both ends as it goes, since a page can't see the progress of a download its browser
	 * makes, and marks the file delivered once the last byte is out.
	 */
	public void deliver(Device me, Transfer transfer, TransferFile file, Path path, long from, OutputStream out)
			throws IOException {
		byte[] buffer = new byte[128 * 1024];
		long sent = from;
		long lastReport = System.nanoTime();
		try (KeepAwake.Hold awake = keepAwake.hold(); InputStream in = Files.newInputStream(path)) {
			in.skipNBytes(from);
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
				sent += read;
				long now = System.nanoTime();
				if (now - lastReport >= PROGRESS_INTERVAL_NANOS) {
					lastReport = now;
					events.publishEvent(new DropEvent.DeliveryProgress(transfer, file.id(), sent));
				}
			}
		}
		out.flush();
		markDelivered(me, transfer.id(), file.id());
	}

	/** Called once a phone recipient has downloaded the whole file. */
	private void markDelivered(Device me, String transferId, String fileId) {
		update(transferId, current -> current.recipientId().equals(me.id()) && current.status() == Status.ACCEPTED
				? current.withFiles(file -> file.id().equals(fileId) && file.state() == State.READY
						? file.with(State.DELIVERED, Instant.now()) : file)
				: current);
	}

	public TransferView view(Transfer transfer) {
		List<TransferView.FileView> files = transfer.files().stream().map(file -> {
			boolean available = file.isStored() && Files.isRegularFile(contentPath(transfer, file));
			return new TransferView.FileView(file.id(), file.name(), file.size(), file.mimeType(), file.state(),
					available, available && thumbnails.offers(file.id(), file.mimeType()), file.finishedAt());
		}).toList();
		return new TransferView(transfer.id(), transfer.senderId(), nameOf(transfer.senderId(), transfer.senderName()),
				transfer.senderPlatform(), transfer.recipientId(),
				nameOf(transfer.recipientId(), transfer.recipientName()), transfer.recipientPlatform(),
				transfer.status(), transfer.text(), files, transfer.createdAt(), transfer.respondedAt(), transfer.folder());
	}

	/**
	 * Expires unanswered transfers, and deletes the copies of files a phone has downloaded (after a short while, in
	 * case it saves them again) or hasn't come back for.
	 */
	@Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
	public void expireAndClean() {
		Instant now = Instant.now();
		Instant pendingCutoff = now.minus(properties.pendingTransferTtl());
		for (Transfer transfer : List.copyOf(transfers.values())) {
			if (transfer.status() == Status.PENDING && transfer.createdAt().isBefore(pendingCutoff)) {
				update(transfer.id(), current -> current.status() == Status.PENDING
						? discardStaged(current).withStatus(Status.EXPIRED, now) : current);
			}
			else if (transfer.status() == Status.ACCEPTED && transfer.files().stream().anyMatch(file -> isStale(file, now))) {
				update(transfer.id(), current -> current.withFiles(file -> {
					if (!isStale(file, now)) {
						return file;
					}
					deleteQuietly(stagedPath(current.id(), file.id()));
					deleteQuietly(partPath(file.id()));
					deleteThumbnail(file.id());
					return file.with(State.REMOVED, now);
				}));
			}
		}
	}

	/**
	 * A staged copy a phone downloaded a while ago or never came to download, or the part of an upload its sender
	 * never came back to finish.
	 */
	private boolean isStale(TransferFile file, Instant now) {
		Instant since = file.finishedAt();
		return since != null && switch (file.state()) {
			case DELIVERED -> since.isBefore(now.minus(properties.deliveredRetention()));
			case READY, FAILED -> since.isBefore(now.minus(UNCLAIMED_RETENTION));
			default -> false;
		};
	}

	/** For a host recipient, moves every staged file into the storage folder. Phones download theirs instead. */
	private Transfer deliverReadyFiles(Transfer transfer) {
		boolean toHost = devices.find(transfer.recipientId()).map(Device::host).orElse(false);
		if (!toHost) {
			return transfer;
		}
		Transfer saved = transfer.withFiles(file -> file.state() == State.READY ? saveToFolder(transfer, file) : file);
		if (saved.files().stream().allMatch(file -> file.state() != State.UPLOADING && file.state() != State.READY)) {
			// Everything has left staging; don't leave an empty folder behind.
			deleteQuietly(storage.stagingDir().resolve(transfer.id()));
		}
		return saved;
	}

	/** Where a host recipient saves: the folder picked, else the default one; null when that's the storage folder. */
	private String hostFolder(Path picked) {
		Path folder = picked != null ? picked : saveFolders.defaultFolder();
		return folder.equals(storage.root()) ? null : folder.toString();
	}

	/**
	 * Moves a staged file into the folder picked when accepting (or the storage folder). If that fails, the file is
	 * marked failed so the sender can resend it.
	 */
	private TransferFile saveToFolder(Transfer transfer, TransferFile file) {
		Path folder = transfer.folder() == null ? storage.root() : Path.of(transfer.folder());
		if (!Files.isDirectory(folder)) {
			log.warn("{} no longer exists; saving {} into {} instead", folder, file.name(), storage.root());
			folder = storage.root();
		}
		try {
			Path target;
			synchronized (fileNameLock) {
				target = FileNames.uniqueIn(folder, file.name());
				Files.move(stagedPath(transfer.id(), file.id()), target);
			}
			return file.savedAs(target.getFileName().toString(), target.toString(), Instant.now());
		}
		catch (IOException ex) {
			log.warn("Could not save {} into {}", file.name(), folder, ex);
			return file.with(State.FAILED, Instant.now());
		}
	}

	/** Writes the body into {@code part}, after the {@code offset} bytes already there (0 starts it afresh). */
	private long copyWithProgress(String transferId, TransferFile file, InputStream in, Path part, long offset)
			throws IOException {
		byte[] buffer = new byte[128 * 1024];
		long written = offset;
		long lastReport = System.nanoTime();
		try (OutputStream out = offset == 0 ? Files.newOutputStream(part)
				: Files.newOutputStream(part, StandardOpenOption.APPEND)) {
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
				written += read;
				if (written > file.size()) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "More data arrived than was announced.");
				}
				long now = System.nanoTime();
				if (now - lastReport >= PROGRESS_INTERVAL_NANOS) {
					lastReport = now;
					Transfer current = transfers.get(transferId);
					if (current == null || !current.status().isOpen()) {
						// Declined or cancelled mid-upload: stop reading, the sender will be told why.
						throw new ResponseStatusException(HttpStatus.CONFLICT, "This transfer was "
								+ (current == null ? "removed" : describe(current.status())) + ".");
					}
					events.publishEvent(new DropEvent.TransferProgress(current, file.id(), written));
				}
			}
		}
		return written;
	}

	private void markFailed(String transferId, String fileId) {
		try {
			update(transferId, current -> current.status().isOpen()
					? current.withFiles(file -> file.id().equals(fileId) && file.state() == State.UPLOADING
							? file.with(State.FAILED, Instant.now()) : file)
					: current);
		}
		catch (ResponseStatusException ex) {
			// The transfer was removed meanwhile; nothing to mark.
		}
	}

	/** Deletes staged copies and unfinished uploads, and marks those files removed. Saved files stay where they are. */
	private Transfer discardStaged(Transfer transfer) {
		try {
			FileSystemUtils.deleteRecursively(storage.stagingDir().resolve(transfer.id()));
		}
		catch (IOException ex) {
			log.warn("Could not delete staged files for transfer {}", transfer.id(), ex);
		}
		transfer.files().forEach(file -> deleteQuietly(partPath(file.id())));
		transfer.files().stream().filter(file -> file.state() != State.SAVED).forEach(file -> deleteThumbnail(file.id()));
		return transfer.withFiles(file -> file.state() == State.SAVED ? file : file.with(State.REMOVED, Instant.now()));
	}

	private synchronized Transfer update(String id, UnaryOperator<Transfer> change) {
		Transfer current = transfers.get(id);
		if (current == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such transfer.");
		}
		Transfer next = change.apply(current);
		if (!next.equals(current)) {
			transfers.put(id, next);
			events.publishEvent(new DropEvent.TransferChanged(next));
		}
		return next;
	}

	/** An upload in progress, or one that broke off and can be continued. */
	private Path partPath(String fileId) {
		return storage.tmpDir().resolve(fileId + ".part");
	}

	private Path stagedPath(String transferId, String fileId) {
		return storage.stagingDir().resolve(transferId).resolve(fileId);
	}

	private void stageEmptyFile(String transferId, String fileId) {
		try {
			Path path = stagedPath(transferId, fileId);
			Files.createDirectories(path.getParent());
			Files.write(path, new byte[0]);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private void requireRecipient(Device me, Transfer transfer) {
		if (!transfer.recipientId().equals(me.id())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the recipient can do that.");
		}
	}

	private void requireFreeSpace(long bytes) {
		try {
			if (bytes > 0 && Files.getFileStore(storage.root()).getUsableSpace() < bytes + FREE_SPACE_MARGIN) {
				throw new ResponseStatusException(HttpStatus.INSUFFICIENT_STORAGE, "Not enough free disk space.");
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private String nameOf(String deviceId, String fallback) {
		return devices.find(deviceId).map(Device::name).orElse(fallback);
	}

	private void deleteThumbnail(String fileId) {
		try {
			thumbnails.delete(fileId);
		}
		catch (IOException ex) {
			log.debug("Could not delete thumbnail {}", fileId, ex);
		}
	}

	private static void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		}
		catch (IOException ex) {
			log.debug("Could not delete {}", path, ex);
		}
	}

	private static String describe(Status status) {
		return status.name().toLowerCase(Locale.ROOT);
	}

	private static String mimeType(String declared, String fileName) {
		if (declared != null && MIME_TYPE.matcher(declared).matches()
				&& !declared.equals(MediaType.APPLICATION_OCTET_STREAM_VALUE)) {
			return declared.toLowerCase(Locale.ROOT);
		}
		return MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM).toString();
	}

}
