package com.androiddrop.host;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.androiddrop.device.DeviceService;
import com.androiddrop.storage.StorageLayout;

/**
 * Folders the host can save accepted files into: the default one (the storage folder unless changed in Settings,
 * kept with the host's other preferences), or any folder on this computer picked when accepting. The last few picks
 * are one click away until the server stops.
 */
@Service
public class SaveFolders {

	private static final int MAX_RECENT = 5;

	/** A folder offered when accepting. {@code isDefault}: where files go when nothing else is picked. */
	public record Folder(String path, String name, boolean isDefault) {
	}

	private final StorageLayout storage;
	private final DeviceService devices;
	private final List<String> recent = new ArrayList<>();

	public SaveFolders(StorageLayout storage, DeviceService devices) {
		this.storage = storage;
		this.devices = devices;
	}

	/** Where accepted files go unless another folder is picked. Back to the storage folder if it was deleted. */
	public Path defaultFolder() {
		String chosen = devices.host().saveFolder();
		return chosen != null && Files.isDirectory(Path.of(chosen)) ? Path.of(chosen) : storage.root();
	}

	public void setDefault(Path folder) {
		devices.setSaveFolder(folder.equals(storage.root()) ? null : folder.toString());
	}

	/** The default folder first, then the storage folder (if that isn't the default), then recent ones. */
	public synchronized List<Folder> choices() {
		Path chosen = defaultFolder();
		List<Folder> folders = new ArrayList<>(List.of(folder(chosen, true)));
		if (!chosen.equals(storage.root())) {
			folders.add(folder(storage.root(), false));
		}
		recent.stream()
			.map(Path::of)
			.filter(path -> Files.isDirectory(path) && !path.equals(chosen) && !path.equals(storage.root()))
			.forEach(path -> folders.add(folder(path, false)));
		return folders;
	}

	/** Checks a folder to save into: an existing, writable folder that isn't AndroidDrop's own hidden one. */
	public Path validate(String folder) {
		Path path;
		try {
			path = folder == null ? null : Path.of(folder);
		}
		catch (InvalidPathException ex) {
			path = null;
		}
		if (path == null || !path.isAbsolute()) {
			throw invalid("That isn't a folder path.");
		}
		if (!Files.isDirectory(path)) {
			throw invalid("That folder doesn't exist any more. Pick another one.");
		}
		if (!Files.isWritable(path)) {
			throw invalid("AndroidDrop isn't allowed to save into that folder.");
		}
		try {
			Path real = path.toRealPath();
			if (real.startsWith(storage.metaDir().toRealPath())) {
				throw invalid("That folder is where AndroidDrop keeps its own data. Pick another one.");
			}
			return real;
		}
		catch (IOException ex) {
			throw invalid("That folder can't be used.");
		}
	}

	public Folder describe(Path path) {
		return folder(path, path.equals(defaultFolder()));
	}

	/** Puts a folder at the top of the recent list. The storage folder is always offered, so it isn't listed. */
	public synchronized void remember(Path folder) {
		if (folder.equals(storage.root())) {
			return;
		}
		recent.remove(folder.toString());
		recent.addFirst(folder.toString());
		while (recent.size() > MAX_RECENT) {
			recent.removeLast();
		}
	}

	private static Folder folder(Path path, boolean isDefault) {
		Path name = path.getFileName();
		return new Folder(path.toString(), name == null ? path.toString() : name.toString(), isDefault);
	}

	private static ResponseStatusException invalid(String detail) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
	}

}
