package com.androiddrop.host;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.androiddrop.device.Platform;

/**
 * Keeps this computer from going to sleep while files are moving. A big video can take half an hour over Wi-Fi,
 * and a Mac left alone sleeps after a few minutes, cutting the phone off mid-transfer. Uses macOS's own
 * {@code caffeinate}, tied to this process so it can't outlive the server. Closing the lid still sleeps.
 */
@Component
public class KeepAwake {

	private static final Logger log = LoggerFactory.getLogger(KeepAwake.class);

	/** Held while one transfer is moving; closing it lets the computer sleep again once none are. */
	public interface Hold extends AutoCloseable {

		@Override
		void close();

	}

	private final boolean supported;
	private int holds;
	private Process caffeinate;

	public KeepAwake(HostInfo hostInfo) {
		this.supported = hostInfo.platform() == Platform.MAC;
	}

	public Hold hold() {
		acquire();
		return this::release;
	}

	private synchronized void acquire() {
		if (holds++ > 0 || !supported) {
			return;
		}
		try {
			// -i: no idle sleep; -w: exit with this process, even if it's killed.
			caffeinate = new ProcessBuilder("caffeinate", "-i", "-w", String.valueOf(ProcessHandle.current().pid()))
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.redirectError(ProcessBuilder.Redirect.DISCARD)
				.start();
		}
		catch (IOException ex) {
			log.debug("Could not keep this computer awake", ex);
		}
	}

	private synchronized void release() {
		if (--holds > 0 || caffeinate == null) {
			return;
		}
		caffeinate.destroy();
		caffeinate = null;
	}

}
