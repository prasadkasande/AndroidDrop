package com.androiddrop.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings under {@code drop.*}. Defaults live in application.properties.
 *
 * @param storageDir         folder that received files land in; server metadata goes in a hidden subfolder
 * @param hostName           name shown to other devices; blank means "detect the computer name"
 * @param pairingCodeTtl     how long a pairing code stays valid before it rotates
 * @param maxPairingAttempts wrong guesses allowed before the code rotates
 * @param maxTextLength      longest text snippet that can be shared
 * @param pendingTransferTtl how long a transfer waits to be accepted before it expires
 * @param deliveredRetention how long a file a phone downloaded stays on this computer, so it can be saved again
 * @param httpsPort          port for HTTPS, served next to plain HTTP on {@code server.port} (see
 *                           {@link com.androiddrop.tls.SecurePort}); 0 turns it off
 */
@ConfigurationProperties("drop")
public record DropProperties(
		Path storageDir,
		@DefaultValue("") String hostName,
		@DefaultValue("10m") Duration pairingCodeTtl,
		@DefaultValue("5") int maxPairingAttempts,
		@DefaultValue("100000") int maxTextLength,
		@DefaultValue("1h") Duration pendingTransferTtl,
		@DefaultValue("1h") Duration deliveredRetention,
		@DefaultValue("443") int httpsPort) {
}
