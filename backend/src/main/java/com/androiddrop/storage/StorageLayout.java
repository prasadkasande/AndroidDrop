package com.androiddrop.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import com.androiddrop.config.DropProperties;

import jakarta.annotation.PreDestroy;

/**
 * On-disk layout:
 * <pre>
 * ~/AndroidDrop/                 files received from other devices (browsable in Finder)
 * ~/AndroidDrop/.androiddrop/    devices.json (paired devices and the host's preferences) and tls/ (HTTPS)
 *               ├── staging/     files waiting to be accepted or downloaded, one folder per transfer
 *               ├── thumbs/      cached photo thumbnails
 *               └── tmp/         uploads in progress
 * </pre>
 * Only devices.json and tls/ outlive a run. Transfer history lives in memory, and the three scratch folders exist
 * only while the server runs: they're deleted when it stops, and again when it starts in case it was killed.
 */
@Component
public class StorageLayout {

	private static final Logger log = LoggerFactory.getLogger(StorageLayout.class);

	/** Kept by older versions: transfer history, recent folders, and host settings now in devices.json. */
	private static final List<String> OLD_FILES = List.of("transfers.json", "folders.json", "host.json",
			"default-folder.json");

	private final Path root;
	private final Path metaDir;
	private final Path stagingDir;
	private final Path tmpDir;
	private final Path thumbsDir;

	public StorageLayout(DropProperties properties) throws IOException {
		this.root = properties.storageDir().toAbsolutePath().normalize();
		this.metaDir = root.resolve(".androiddrop");
		this.stagingDir = metaDir.resolve("staging");
		this.tmpDir = metaDir.resolve("tmp");
		this.thumbsDir = metaDir.resolve("thumbs");
		Files.createDirectories(metaDir);
		clearScratch();
		for (String name : OLD_FILES) {
			Files.deleteIfExists(metaDir.resolve(name));
		}
	}

	public Path root() {
		return root;
	}

	public Path metaDir() {
		return metaDir;
	}

	/** Files that have been sent but not yet accepted, or that wait on this computer for a phone to download. */
	public Path stagingDir() {
		return stagingDir;
	}

	/** Uploads in progress. Created on demand, like the other scratch folders. */
	public Path tmpDir() {
		return tmpDir;
	}

	public Path thumbsDir() {
		return thumbsDir;
	}

	/** Deletes staging, tmp and thumbs. On shutdown this runs after the web server has stopped serving them. */
	@PreDestroy
	public void clearScratch() {
		for (Path dir : List.of(stagingDir, tmpDir, thumbsDir)) {
			try {
				FileSystemUtils.deleteRecursively(dir);
			}
			catch (IOException ex) {
				log.warn("Could not delete {}", dir, ex);
			}
		}
	}

}
