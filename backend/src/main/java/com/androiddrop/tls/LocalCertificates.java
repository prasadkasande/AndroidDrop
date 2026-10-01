package com.androiddrop.tls;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The certificates behind HTTPS. AndroidDrop is its own small certificate authority: its CA certificate is
 * installed once on each phone, and the server certificate it signs names {@code localhost} and this computer's
 * Wi-Fi/LAN addresses. The server certificate is reissued when those addresses change or it nears expiry; the CA
 * stays the same, so phones never need to install anything again.
 * <p>
 * Everything is made with the JDK's {@code keytool}, so there's no extra dependency. Files live in one folder:
 * {@code ca.p12} (the CA's key, which never leaves this computer), {@code ca.crt} (what phones install),
 * {@code server.p12} (what Tomcat serves) and {@code password}.
 */
public class LocalCertificates {

	private static final Logger log = LoggerFactory.getLogger(LocalCertificates.class);

	private static final String CA_ALIAS = "ca";
	private static final String SERVER_ALIAS = "server";
	private static final int CA_DAYS = 3650;
	/** Browsers reject server certificates valid for more than 398 days. */
	private static final int SERVER_DAYS = 397;
	private static final Duration RENEW_BEFORE = Duration.ofDays(30);
	private static final Set<String> TAKES_KEY_PASSWORD = Set.of("-genkeypair", "-certreq", "-gencert", "-importcert");

	private final Path dir;
	private final String caName;

	/** @param hostName shown in the CA's name, so it's recognisable in the phone's list of certificates */
	public LocalCertificates(Path dir, String hostName) {
		this.dir = dir;
		this.caName = "AndroidDrop CA (" + hostName.replaceAll("[^\\p{L}\\p{N} ._'’-]", "").strip() + ")";
	}

	/** The keystore Tomcat serves: the server key with its certificate chain. */
	public Path serverKeyStore() {
		return dir.resolve("server.p12");
	}

	public String password() {
		return read(dir.resolve("password"));
	}

	/** The CA certificate (PEM) that phones install. */
	public byte[] caCertificate() {
		try {
			return Files.readAllBytes(dir.resolve("ca.crt"));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/**
	 * Makes sure there's a CA and a current server certificate for {@code localhost} and {@code addresses}. Cheap
	 * when nothing changed.
	 */
	public synchronized void ensure(Collection<String> addresses) {
		try {
			Files.createDirectories(dir);
			if (!Files.exists(dir.resolve("ca.p12"))) {
				createAuthority();
			}
			X509Certificate current = serverCertificate();
			if (current == null || !covers(current, addresses) || expiresSoon(current) || !signedByAuthority(current)) {
				issueServerCertificate(addresses);
				log.info("Issued an HTTPS certificate for localhost, {}", String.join(", ", addresses));
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private void createAuthority() throws IOException {
		byte[] secret = new byte[24];
		new SecureRandom().nextBytes(secret);
		Path password = dir.resolve("password");
		Files.writeString(password, Base64.getUrlEncoder().withoutPadding().encodeToString(secret));
		restrictToOwner(password);
		keytool("-genkeypair", "-alias", CA_ALIAS, "-keyalg", "EC", "-groupname", "secp256r1", "-dname",
				"CN=" + caName + ", O=AndroidDrop", "-validity", String.valueOf(CA_DAYS), "-ext", "bc:c=ca:true",
				"-ext", "ku:c=keyCertSign,cRLSign", "-keystore", file("ca.p12"), "-storetype", "PKCS12");
		restrictToOwner(dir.resolve("ca.p12"));
		keytool("-exportcert", "-rfc", "-alias", CA_ALIAS, "-keystore", file("ca.p12"), "-file", file("ca.crt"));
		// A new CA invalidates any older server certificate.
		Files.deleteIfExists(serverKeyStore());
	}

	private void issueServerCertificate(Collection<String> addresses) throws IOException {
		Path next = dir.resolve("server.p12.next");
		Path request = dir.resolve("server.csr");
		Path signed = dir.resolve("server.crt");
		try {
			Files.deleteIfExists(next);
			List<String> names = new ArrayList<>(List.of("dns:localhost", "ip:127.0.0.1"));
			addresses.forEach(address -> names.add("ip:" + address));
			keytool("-genkeypair", "-alias", SERVER_ALIAS, "-keyalg", "EC", "-groupname", "secp256r1", "-dname",
					"CN=AndroidDrop", "-validity", String.valueOf(SERVER_DAYS), "-keystore", next.toString(),
					"-storetype", "PKCS12");
			keytool("-certreq", "-alias", SERVER_ALIAS, "-keystore", next.toString(), "-file", request.toString());
			keytool("-gencert", "-rfc", "-alias", CA_ALIAS, "-keystore", file("ca.p12"), "-infile", request.toString(),
					"-outfile", signed.toString(), "-validity", String.valueOf(SERVER_DAYS), "-ext",
					"SAN=" + String.join(",", names), "-ext", "EKU=serverAuth", "-ext", "KU=digitalSignature");
			keytool("-importcert", "-noprompt", "-alias", CA_ALIAS, "-keystore", next.toString(), "-file", file("ca.crt"));
			keytool("-importcert", "-alias", SERVER_ALIAS, "-keystore", next.toString(), "-file", signed.toString());
			restrictToOwner(next);
			Files.move(next, serverKeyStore(), StandardCopyOption.REPLACE_EXISTING);
		}
		finally {
			Files.deleteIfExists(request);
			Files.deleteIfExists(signed);
			Files.deleteIfExists(next);
		}
	}

	X509Certificate serverCertificate() {
		return (X509Certificate) load(serverKeyStore(), SERVER_ALIAS);
	}

	X509Certificate authorityCertificate() {
		return (X509Certificate) load(dir.resolve("ca.p12"), CA_ALIAS);
	}

	private java.security.cert.Certificate load(Path keyStoreFile, String alias) {
		if (!Files.exists(keyStoreFile)) {
			return null;
		}
		try (InputStream in = Files.newInputStream(keyStoreFile)) {
			KeyStore store = KeyStore.getInstance("PKCS12");
			store.load(in, password().toCharArray());
			return store.getCertificate(alias);
		}
		catch (IOException | GeneralSecurityException ex) {
			log.warn("Could not read {}; making a new one", keyStoreFile, ex);
			return null;
		}
	}

	private static boolean covers(X509Certificate certificate, Collection<String> addresses) {
		try {
			Set<String> ips = new HashSet<>();
			Collection<List<?>> names = certificate.getSubjectAlternativeNames();
			if (names != null) {
				names.stream().filter(name -> Integer.valueOf(7).equals(name.get(0))).forEach(name -> ips.add((String) name.get(1)));
			}
			return ips.containsAll(addresses);
		}
		catch (GeneralSecurityException ex) {
			return false;
		}
	}

	private static boolean expiresSoon(X509Certificate certificate) {
		return certificate.getNotAfter().toInstant().isBefore(Instant.now().plus(RENEW_BEFORE));
	}

	private boolean signedByAuthority(X509Certificate certificate) {
		X509Certificate authority = authorityCertificate();
		try {
			certificate.verify(authority.getPublicKey());
			return true;
		}
		catch (GeneralSecurityException | RuntimeException ex) {
			return false;
		}
	}

	private void keytool(String... args) {
		List<String> command = new ArrayList<>();
		command.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
		command.addAll(List.of(args));
		command.addAll(List.of("-storepass", password()));
		if (TAKES_KEY_PASSWORD.contains(args[0])) {
			command.addAll(List.of("-keypass", password()));
		}
		try {
			Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
				throw new IllegalStateException("keytool " + args[0] + " failed: " + output.strip());
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	private String file(String name) {
		return dir.resolve(name).toString();
	}

	private static String read(Path path) {
		try {
			return Files.readString(path).strip();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static void restrictToOwner(Path path) {
		try {
			Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
		}
		catch (UnsupportedOperationException | IOException ex) {
			// Not a POSIX file system (Windows): the folder's own permissions apply.
		}
	}

}
