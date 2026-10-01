package com.androiddrop.host;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.androiddrop.config.DropProperties;
import com.androiddrop.device.Platform;

/** Facts about, and actions on, the computer running the server. */
@Component
public class HostInfo {

	private static final Duration FOLDER_PICKER_TIMEOUT = Duration.ofMinutes(10);

	private final Platform platform = Platform.ofThisMachine();
	private final String name;

	public HostInfo(DropProperties properties) {
		this.name = properties.hostName().isBlank() ? detectName() : properties.hostName().strip();
	}

	public String name() {
		return name;
	}

	public Platform platform() {
		return platform;
	}

	/** Opens the file manager with {@code file} selected. */
	public void reveal(Path file) throws IOException {
		switch (platform) {
			case MAC -> run(List.of("open", "-R", file.toString()));
			case WINDOWS -> run(List.of("explorer", "/select," + file));
			default -> run(List.of("xdg-open", file.getParent().toString()));
		}
	}

	/** Opens {@code folder} in the file manager. */
	public void open(Path folder) throws IOException {
		switch (platform) {
			case MAC -> run(List.of("open", folder.toString()));
			case WINDOWS -> run(List.of("explorer", folder.toString()));
			default -> run(List.of("xdg-open", folder.toString()));
		}
	}

	/** Whether {@link #chooseFolder} can show a folder picker on this computer. */
	public boolean canChooseFolder() {
		return platform == Platform.MAC;
	}

	/**
	 * Shows the system's folder picker on this computer's screen and waits for an answer. Empty if it was cancelled
	 * (or this computer has no picker AndroidDrop can open).
	 */
	public Optional<Path> chooseFolder(String prompt, Path startIn) {
		if (!canChooseFolder()) {
			return Optional.empty();
		}
		// The prompt and folder are passed as arguments, never pasted into the script. "activate" brings the dialog
		// in front of the browser.
		String output = capture(FOLDER_PICKER_TIMEOUT, "osascript", "-e", "on run argv", "-e", "activate", "-e",
				"return POSIX path of (choose folder with prompt (item 1 of argv) default location (POSIX file (item 2 of argv)))",
				"-e", "end run", prompt, startIn.toString());
		return Optional.ofNullable(output).map(Path::of);
	}

	private static void run(List<String> command) throws IOException {
		new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
	}

	private String detectName() {
		if (platform == Platform.MAC) {
			String computerName = capture(Duration.ofSeconds(2), "scutil", "--get", "ComputerName");
			if (computerName != null) {
				return computerName;
			}
		}
		try {
			return InetAddress.getLocalHost().getHostName().replaceFirst("\\.local$", "");
		}
		catch (IOException ex) {
			return "This computer";
		}
	}

	/** A command's output, or null if it failed (a cancelled picker exits with an error) or took too long. */
	private static String capture(Duration timeout, String... command) {
		try {
			Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
			if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS) || process.exitValue() != 0) {
				process.destroy();
				return null;
			}
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
			return output.isEmpty() ? null : output;
		}
		catch (IOException ex) {
			return null;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

}
