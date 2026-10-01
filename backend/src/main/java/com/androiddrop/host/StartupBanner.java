package com.androiddrop.host;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.androiddrop.pairing.PairingService;
import com.androiddrop.storage.StorageLayout;
import com.androiddrop.tls.SecurePort;

@Component
class StartupBanner {

	private static final Logger log = LoggerFactory.getLogger(StartupBanner.class);

	private final ServerAddresses addresses;
	private final PairingService pairing;
	private final StorageLayout storage;
	private final SecurePort securePort;

	StartupBanner(ServerAddresses addresses, PairingService pairing, StorageLayout storage, SecurePort securePort) {
		this.addresses = addresses;
		this.pairing = pairing;
		this.storage = storage;
		this.securePort = securePort;
	}

	@EventListener(ApplicationReadyEvent.class)
	void printHowToConnect() {
		String phoneUrls = addresses.lanUrls().isEmpty() ? "(no Wi-Fi/LAN address found)"
				: String.join("  or  ", addresses.lanUrls());
		String secureUrls = !securePort.enabled() || addresses.lanAddresses().isEmpty() ? "off"
				: String.join("  or  ", addresses.lanAddresses().stream().map(securePort::urlFor).toList())
						+ "  (the phone sets this up from its menu)";
		log.info("""

				  AndroidDrop is running
				    On this computer : http://localhost:{}
				    On your phone    : {}
				    Secure (HTTPS)   : {}
				    Pairing code     : {}
				    Files folder     : {}
				""", addresses.port(), phoneUrls, secureUrls, pairing.current().code(), storage.root());
	}

}
