package com.androiddrop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class AndroidDropApplication {

	private static final String LOG_FORMAT = "java.util.logging.SimpleFormatter.format";

	public static void main(String[] args) {
		// Logging goes through the JDK's java.util.logging (there's no Logback in the jar): one line per message.
		if (System.getProperty(LOG_FORMAT) == null) {
			System.setProperty(LOG_FORMAT, "%1$tT %4$-7s %5$s%6$s%n");
		}
		SpringApplication.run(AndroidDropApplication.class, args);
	}

}
