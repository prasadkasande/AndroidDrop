package com.androiddrop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.androiddrop.pairing.PairingService;
import com.androiddrop.security.DeviceAuthFilter;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/** Shared context and helpers. Requests without {@link #phone()} come from the host over loopback. */
@SpringBootTest
@AutoConfigureMockMvc
abstract class ApiTestSupport {

	static final Path STORAGE = createStorageDir();

	static final String ANDROID_UA = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 Chrome/139 Mobile";

	@DynamicPropertySource
	static void storage(DynamicPropertyRegistry registry) {
		registry.add("drop.storage-dir", STORAGE::toString);
		registry.add("drop.host-name", () -> "Test Mac");
		registry.add("drop.https-port", () -> "8443");
	}

	@Autowired
	MockMvc mvc;

	@Autowired
	PairingService pairing;

	/** Pairs a new phone and returns its token cookie. */
	Cookie pair(String name) throws Exception {
		MvcResult result = mvc.perform(pairRequest(pairing.current().code(), name))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.host").value(false))
			.andReturn();
		Cookie cookie = result.getResponse().getCookie(DeviceAuthFilter.TOKEN_COOKIE);
		assertThat(cookie).isNotNull();
		assertThat(cookie.isHttpOnly()).isTrue();
		return new Cookie(cookie.getName(), cookie.getValue());
	}

	MockHttpServletRequestBuilder pairRequest(String code, String name) {
		return json(post("/api/pair"), "{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}").with(phone())
			.header(HttpHeaders.USER_AGENT, ANDROID_UA);
	}

	String deviceId(Cookie cookie) throws Exception {
		return read(mvc.perform(get("/api/session").with(phone()).cookie(cookie)).andReturn(), "$.device.id");
	}

	/** Announces a one-file transfer and uploads it. {@code from == null} sends from the host. */
	Sent sendFile(Cookie from, String recipientId, String name, String type, byte[] bytes) throws Exception {
		MvcResult created = mvc.perform(as(from, json(post("/api/transfers"),
				"{\"recipientId\":\"" + recipientId + "\",\"files\":[{\"name\":\"" + name + "\",\"size\":" + bytes.length
						+ ",\"mimeType\":\"" + type + "\"}]}")))
			.andExpect(status().isOk())
			.andReturn();
		Sent sent = new Sent(read(created, "$.id"), read(created, "$.files[0].id"));
		mvc.perform(as(from, upload(sent, bytes))).andExpect(status().isOk());
		return sent;
	}

	MockHttpServletRequestBuilder upload(Sent sent, byte[] bytes) {
		return put("/api/transfers/{id}/files/{fileId}", sent.id(), sent.fileId())
			.header(DeviceAuthFilter.CSRF_HEADER, "1")
			.contentType(MediaType.APPLICATION_OCTET_STREAM)
			.content(bytes);
	}

	/** Runs the request as the phone holding {@code cookie}, or as the host when it's null. */
	static MockHttpServletRequestBuilder as(Cookie cookie, MockHttpServletRequestBuilder request) {
		return cookie == null ? request : request.with(phone()).cookie(cookie);
	}

	static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.header(DeviceAuthFilter.CSRF_HEADER, "1").contentType(MediaType.APPLICATION_JSON).content(body);
	}

	static MockHttpServletRequestBuilder action(MockHttpServletRequestBuilder request) {
		return request.header(DeviceAuthFilter.CSRF_HEADER, "1");
	}

	/** A request arriving from a phone on the Wi-Fi rather than over loopback. */
	static RequestPostProcessor phone() {
		return request -> {
			request.setRemoteAddr("192.168.1.20");
			request.setServerName("192.168.1.5");
			return request;
		};
	}

	static String read(MvcResult result, String path) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), path);
	}

	record Sent(String id, String fileId) {
	}

	private static Path createStorageDir() {
		try {
			return Files.createTempDirectory("androiddrop-test").toRealPath();
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
