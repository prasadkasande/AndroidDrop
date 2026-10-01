package com.androiddrop.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** A value persisted as a single JSON file, written atomically. */
public class JsonStore<T> {

	private static final Logger log = LoggerFactory.getLogger(JsonStore.class);

	private final Path file;
	private final JsonMapper mapper;
	private final TypeReference<T> type;

	public JsonStore(Path file, JsonMapper mapper, TypeReference<T> type) {
		this.file = file;
		this.mapper = mapper;
		this.type = type;
	}

	public T load(Supplier<T> fallback) {
		if (!Files.exists(file)) {
			return fallback.get();
		}
		try {
			return mapper.readValue(Files.readAllBytes(file), type);
		}
		catch (IOException | JacksonException ex) {
			// Keep the unreadable file around rather than overwriting it on the next save.
			Path backup = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
			log.warn("Could not read {}; moved it to {} and starting empty", file, backup, ex);
			try {
				Files.move(file, backup);
			}
			catch (IOException moveEx) {
				throw new UncheckedIOException(moveEx);
			}
			return fallback.get();
		}
	}

	public synchronized void save(T value) {
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try {
			Files.write(tmp, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
