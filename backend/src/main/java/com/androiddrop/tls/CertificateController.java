package com.androiddrop.tls;

import java.net.URI;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.androiddrop.host.ServerAddresses;

@RestController
class CertificateController {

	private final LocalCertificates certificates;
	private final SecurePort securePort;
	private final ServerAddresses addresses;

	CertificateController(LocalCertificates certificates, SecurePort securePort, ServerAddresses addresses) {
		this.certificates = certificates;
		this.securePort = securePort;
		this.addresses = addresses;
	}

	/**
	 * The certificate a phone installs once so it trusts this computer's HTTPS. Public on purpose: a phone fetches
	 * it before it has a secure connection. Sent as a plain download, because Android only installs CA
	 * certificates from Settings, not from the browser.
	 */
	@GetMapping("/androiddrop-ca.crt")
	ResponseEntity<byte[]> authority() {
		if (!securePort.enabled()) {
			return ResponseEntity.notFound().build();
		}
		certificates.ensure(addresses.lanAddresses());
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_OCTET_STREAM)
			.header("Content-Disposition", ContentDisposition.attachment().filename("AndroidDrop.crt").build().toString())
			.cacheControl(CacheControl.noStore())
			.body(certificates.caCertificate());
	}

	/**
	 * Where Android posts things shared into the installed app. The page's service worker normally catches the post
	 * first; if it didn't (it wasn't running yet), land on the app with a note instead of an error page.
	 */
	@PostMapping("/share-target")
	ResponseEntity<Void> shareTarget() {
		return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create("/?share=failed")).build();
	}

}
