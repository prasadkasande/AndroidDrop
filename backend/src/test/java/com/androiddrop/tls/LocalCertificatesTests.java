package com.androiddrop.tls;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalCertificatesTests {

	@TempDir
	Path dir;

	@Test
	void issuesAServerCertificateForTheLanAddressSignedByAnInstallableAuthority() throws Exception {
		LocalCertificates certificates = new LocalCertificates(dir.resolve("tls"), "Prasad’s MacBook Air");
		certificates.ensure(List.of("192.168.1.20"));

		X509Certificate authority = (X509Certificate) CertificateFactory.getInstance("X.509")
			.generateCertificate(new ByteArrayInputStream(certificates.caCertificate()));
		assertThat(authority.getBasicConstraints()).as("phones only install CA certificates").isGreaterThanOrEqualTo(0);
		assertThat(authority.getSubjectX500Principal().getName()).contains("AndroidDrop CA (Prasad’s MacBook Air)");

		KeyStore served = KeyStore.getInstance("PKCS12");
		try (InputStream in = Files.newInputStream(certificates.serverKeyStore())) {
			served.load(in, certificates.password().toCharArray());
		}
		X509Certificate server = (X509Certificate) served.getCertificate("server");
		server.verify(authority.getPublicKey());
		assertThat(served.getCertificateChain("server")).hasSize(2);
		assertThat(server.getSubjectAlternativeNames().stream().map(name -> String.valueOf(name.get(1))))
			.contains("localhost", "127.0.0.1", "192.168.1.20");
		assertThat(server.getExtendedKeyUsage()).contains("1.3.6.1.5.5.7.3.1");
	}

	@Test
	void reissuesTheServerCertificateOnlyWhenTheAddressChanges() {
		LocalCertificates certificates = new LocalCertificates(dir.resolve("tls"), "Mac");
		certificates.ensure(List.of("192.168.1.20"));
		X509Certificate first = certificates.serverCertificate();
		byte[] authority = certificates.caCertificate();

		certificates.ensure(List.of("192.168.1.20"));
		assertThat(certificates.serverCertificate().getSerialNumber()).isEqualTo(first.getSerialNumber());

		certificates.ensure(List.of("10.0.0.7"));
		assertThat(certificates.serverCertificate().getSerialNumber()).isNotEqualTo(first.getSerialNumber());
		assertThat(certificates.caCertificate()).as("phones keep the CA they installed").isEqualTo(authority);
	}

}
