package com.androiddrop.transfer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Turns names supplied by other devices into safe, non-colliding file names. */
final class FileNames {

	/** macOS allows 255 UTF-8 bytes; leave room for " (12)" suffixes. */
	private static final int MAX_BYTES = 200;
	private static final int MAX_EXTENSION_BYTES = 16;

	private FileNames() {
	}

	/**
	 * Keeps only the last path segment, drops control characters and leading dots (so no hidden files or
	 * {@code ..}), replaces {@code :} (which Finder shows as {@code /}), and caps the length.
	 */
	static String sanitize(String raw) {
		String name = raw == null ? "" : raw;
		name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
		name = name.replaceAll("\\p{Cntrl}", "").replace(':', '-').strip();
		name = name.replaceFirst("^\\.+", "").strip();
		if (name.isEmpty()) {
			return "file";
		}
		if (utf8Length(name) <= MAX_BYTES) {
			return name;
		}
		String extension = extension(name);
		if (utf8Length(extension) > MAX_EXTENSION_BYTES) {
			extension = "";
		}
		String stem = name.substring(0, name.length() - extension.length());
		return truncate(stem, MAX_BYTES - utf8Length(extension)).strip() + extension;
	}

	/** {@code dir/name}, or {@code dir/name (1).ext}, {@code (2)}... if that is taken. Caller must hold a lock. */
	static Path uniqueIn(Path dir, String name) {
		String extension = extension(name);
		String stem = name.substring(0, name.length() - extension.length());
		Path candidate = dir.resolve(name);
		for (int i = 1; Files.exists(candidate); i++) {
			candidate = dir.resolve(stem + " (" + i + ")" + extension);
		}
		return candidate;
	}

	/** ".jpg" for "photo.jpg"; empty for "README" or ".profile". */
	static String extension(String name) {
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(dot) : "";
	}

	private static String truncate(String text, int maxBytes) {
		StringBuilder out = new StringBuilder();
		int bytes = 0;
		for (int codePoint : text.codePoints().toArray()) {
			int size = utf8Length(Character.toString(codePoint));
			if (bytes + size > maxBytes) {
				break;
			}
			out.appendCodePoint(codePoint);
			bytes += size;
		}
		return out.toString();
	}

	private static int utf8Length(String text) {
		return text.getBytes(StandardCharsets.UTF_8).length;
	}

}
