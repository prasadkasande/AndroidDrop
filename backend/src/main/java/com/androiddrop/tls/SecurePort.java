package com.androiddrop.tls;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.androiddrop.config.DropProperties;

/**
 * The port HTTPS is served on, decided once at startup. The standard 443 is preferred: Chrome on Android only
 * installs a site as a real app (one that shows up in the share menu) when it's on the default port; on any other
 * port it only adds a home-screen shortcut. If 443 is taken, falls back to 8443 rather than failing to start.
 */
@Component
public class SecurePort {

	private static final Logger log = LoggerFactory.getLogger(SecurePort.class);

	static final int FALLBACK = 8443;

	private final int port;

	public SecurePort(DropProperties properties) {
		this.port = properties.httpsPort() == 0 ? 0 : pick(properties.httpsPort());
	}

	/** 0 when HTTPS is turned off. */
	public int port() {
		return port;
	}

	public boolean enabled() {
		return port != 0;
	}

	/** {@code https://192.168.1.20}, with the port only when it isn't the default. */
	public String urlFor(String host) {
		return port == 443 ? "https://" + host : "https://" + host + ":" + port;
	}

	private static int pick(int preferred) {
		if (preferred == FALLBACK || isFree(preferred) || !isFree(FALLBACK)) {
			return preferred;
		}
		log.warn("Port {} is in use, so HTTPS runs on {}. Phones can then only add AndroidDrop as a shortcut, "
				+ "not install it for the share menu; free up port {} and restart to fix that.", preferred, FALLBACK, preferred);
		return FALLBACK;
	}

	private static boolean isFree(int port) {
		try (ServerSocket socket = new ServerSocket()) {
			socket.setReuseAddress(true);
			socket.bind(new InetSocketAddress(port));
			return true;
		}
		catch (IOException ex) {
			return false;
		}
	}

}
