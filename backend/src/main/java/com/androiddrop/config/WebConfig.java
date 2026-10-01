package com.androiddrop.config;

import java.time.Duration;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class WebConfig implements WebMvcConfigurer {

	/**
	 * The UI build puts a content hash in every file name under {@code /assets}, so a phone can keep them for good
	 * instead of asking again on every visit. {@code index.html} keeps the default, so a new build still shows up.
	 */
	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/assets/**")
			.addResourceLocations("classpath:/static/assets/")
			.setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
	}

}
