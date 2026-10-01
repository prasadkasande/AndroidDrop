package com.androiddrop.tls;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.springframework.boot.tomcat.CompressionConnectorCustomizer;
import org.springframework.boot.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.util.LambdaSafe;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import com.androiddrop.host.HostInfo;
import com.androiddrop.host.ServerAddresses;
import com.androiddrop.storage.StorageLayout;

/**
 * Serves HTTPS on {@code drop.https-port} alongside plain HTTP. HTTP stays for this computer (a browser already
 * trusts {@code http://localhost}) and for phones that haven't installed the certificate yet; HTTPS is what lets a
 * phone install AndroidDrop as an app and share into it.
 */
@Configuration
class HttpsConfig {

	@Bean
	LocalCertificates localCertificates(StorageLayout storage, HostInfo hostInfo) {
		return new LocalCertificates(storage.metaDir().resolve("tls"), hostInfo.name());
	}

	/** Runs last, so the HTTPS connector gets the same settings other customizers gave the HTTP one. */
	@Bean
	@Order(Ordered.LOWEST_PRECEDENCE)
	WebServerFactoryCustomizer<TomcatServletWebServerFactory> httpsConnector(SecurePort securePort,
			LocalCertificates certificates, ServerAddresses addresses) {
		return factory -> {
			if (!securePort.enabled()) {
				return;
			}
			certificates.ensure(addresses.lanAddresses());
			Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
			connector.setPort(securePort.port());
			connector.setScheme("https");
			connector.setSecure(true);
			AbstractHttp11Protocol<?> protocol = (AbstractHttp11Protocol<?>) connector.getProtocolHandler();
			protocol.setSSLEnabled(true);
			SSLHostConfig ssl = new SSLHostConfig();
			SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(ssl, SSLHostConfigCertificate.Type.UNDEFINED);
			certificate.setCertificateKeystoreFile(certificates.serverKeyStore().toString());
			certificate.setCertificateKeystorePassword(certificates.password());
			certificate.setCertificateKeystoreType("PKCS12");
			certificate.setCertificateKeyAlias("server");
			ssl.addCertificate(certificate);
			protocol.addSslHostConfig(ssl);
			// What Spring does for its own connector: virtual threads, compression, then other customizers.
			LambdaSafe.callbacks(TomcatProtocolHandlerCustomizer.class, factory.getProtocolHandlerCustomizers(), protocol)
				.invoke(customizer -> customizer.customize(protocol));
			new CompressionConnectorCustomizer(factory.getCompression()).customize(connector);
			factory.getConnectorCustomizers().forEach(customizer -> customizer.customize(connector));
			factory.addAdditionalConnectors(connector);
		};
	}

}
