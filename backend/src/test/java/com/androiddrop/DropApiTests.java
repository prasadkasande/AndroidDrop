package com.androiddrop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.androiddrop.host.ServerAddresses;
import com.androiddrop.security.DeviceAuthFilter;

import jakarta.servlet.http.Cookie;

/** Who is calling, pairing, and device management. */
class DropApiTests extends ApiTestSupport {

	@Autowired
	ServerAddresses addresses;

	@Test
	void loopbackRequestsAreTheHost() throws Exception {
		mvc.perform(get("/api/session"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.host").value(true))
			.andExpect(jsonPath("$.device.name").value("Test Mac"))
			.andExpect(jsonPath("$.storageDir").value(STORAGE.toString()));
	}

	@Test
	void loopbackWithForeignHostHeaderIsNotTrusted() throws Exception {
		// A DNS-rebinding page reaches 127.0.0.1 but its Host header is its own domain.
		mvc.perform(get("/api/session").with(req -> {
			req.setServerName("evil.example");
			return req;
		})).andExpect(status().isUnauthorized());
	}

	@Test
	void theMacOpeningItsOwnWifiAddressIsTheHost() throws Exception {
		List<String> own = addresses.lanAddresses();
		Assumptions.assumeFalse(own.isEmpty(), "no Wi-Fi/LAN address on this machine");
		String address = own.getFirst();
		mvc.perform(get("/api/session").with(req -> {
			req.setRemoteAddr(address);
			req.setServerName(address);
			return req;
		})).andExpect(jsonPath("$.host").value(true));
		// Same machine, but a DNS-rebinding page's own domain in the Host header: not trusted.
		mvc.perform(get("/api/session").with(req -> {
			req.setRemoteAddr(address);
			req.setServerName("evil.example");
			return req;
		})).andExpect(status().isUnauthorized());
	}

	@Test
	void aSecondBrowserOnAPhoneSignsInAsThatPhoneInsteadOfANewDevice() throws Exception {
		Cookie chrome = pair("Prasad's Android");
		String phoneId = deviceId(chrome);

		String code = pairing.current().code();
		mvc.perform(json(post("/api/pair/devices"), "{\"code\":\"000000\"}").with(phone())).andExpect(status().isForbidden());
		mvc.perform(json(post("/api/pair/devices"), "{\"code\":\"" + code + "\"}").with(phone()))
			.andExpect(jsonPath("$[?(@.id == '" + phoneId + "')].name").value("Prasad's Android"));

		MvcResult joined = mvc.perform(json(post("/api/pair"),
				"{\"code\":\"" + code + "\",\"name\":\"\",\"deviceId\":\"" + phoneId + "\"}").with(phone()))
			.andExpect(jsonPath("$.device.id").value(phoneId))
			.andReturn();
		Cookie firefox = joined.getResponse().getCookie(DeviceAuthFilter.TOKEN_COOKIE);

		mvc.perform(get("/api/session").with(phone()).cookie(chrome)).andExpect(jsonPath("$.device.id").value(phoneId));
		mvc.perform(get("/api/session").with(phone()).cookie(firefox)).andExpect(jsonPath("$.device.id").value(phoneId));
		mvc.perform(get("/api/devices")).andExpect(jsonPath("$[?(@.name == \"Prasad's Android\")]", Matchers.hasSize(1)));

		// Moving one browser to HTTPS replaces only that browser's sign-in.
		String handoff = read(mvc.perform(as(firefox, action(post("/api/session/handoff")))).andReturn(), "$.code");
		mvc.perform(json(post("/api/pair/handoff"), "{\"code\":\"" + handoff + "\"}").with(phone())).andExpect(status().isOk());
		mvc.perform(get("/api/session").with(phone()).cookie(firefox)).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/session").with(phone()).cookie(chrome)).andExpect(status().isOk());
	}

	@Test
	void devServerProxyForwardsThePhoneAddress() throws Exception {
		mvc.perform(get("/api/session").header("X-Forwarded-For", "127.0.0.1, 192.168.1.20"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void unpairedPhoneIsRejectedButCanSeeServerName() throws Exception {
		mvc.perform(get("/api/transfers").with(phone())).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/server").with(phone())).andExpect(jsonPath("$.name").value("Test Mac"));
	}

	@Test
	void malformedPairingRequestsAreRefusedWithoutCostingAGuess() throws Exception {
		String code = pairing.current().code();
		for (int i = 0; i < 10; i++) {
			mvc.perform(pairRequest("12", "Pixel")).andExpect(status().isBadRequest());
		}
		mvc.perform(pairRequest(code, " ")).andExpect(status().isBadRequest());
		mvc.perform(pairRequest(code, "x".repeat(41))).andExpect(status().isBadRequest());
		// None of that used up or rotated the code.
		assertThat(pairing.current().code()).isEqualTo(code);
	}

	@Test
	void pairingWithWrongCodeFailsAndCodeIsSingleUse() throws Exception {
		String code = pairing.current().code();
		String wrong = code.equals("000000") ? "111111" : "000000";
		mvc.perform(pairRequest(wrong, "Pixel")).andExpect(status().isForbidden());

		pair("Pixel");
		mvc.perform(pairRequest(code, "Another phone")).andExpect(status().isForbidden());
	}

	@Test
	void mutationsNeedTheCsrfHeaderAndSameSiteOrigin() throws Exception {
		mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"hi\"}"))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/transfers").header("Sec-Fetch-Site", "cross-site")).andExpect(status().isForbidden());
	}

	@Test
	void devicesCanTurnOnAutoAccept() throws Exception {
		Cookie phone = pair("Pixel");
		mvc.perform(as(phone, json(patch("/api/session/preferences"), "{\"autoAccept\":true}")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.autoAccept").value(true));
		mvc.perform(get("/api/session").with(phone()).cookie(phone)).andExpect(jsonPath("$.autoAccept").value(true));
		mvc.perform(get("/api/session")).andExpect(jsonPath("$.autoAccept").value(false));
	}

	@Test
	void phoneCanRenameItself() throws Exception {
		Cookie phone = pair("Android phone");
		mvc.perform(as(phone, json(patch("/api/session"), "{\"name\":\"Galaxy S25\"}")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.device.name").value("Galaxy S25"));
		mvc.perform(json(patch("/api/session"), "{\"name\":\"x\"}")).andExpect(status().isBadRequest());
	}

	@Test
	void aPhoneCarriesItsPairingToTheHttpsAddressOnce() throws Exception {
		Cookie overHttp = pair("Pixel");
		String code = read(mvc.perform(as(overHttp, action(post("/api/session/handoff")))).andExpect(status().isOk()).andReturn(),
				"$.code");

		// The HTTPS page redeems the code without any cookie, and gets a Secure one.
		MvcResult redeemed = mvc.perform(json(post("/api/pair/handoff"), "{\"code\":\"" + code + "\"}").with(phone()).secure(true))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.device.name").value("Pixel"))
			.andReturn();
		Cookie secure = redeemed.getResponse().getCookie(DeviceAuthFilter.TOKEN_COOKIE);
		assertThat(secure).isNotNull();
		assertThat(secure.getSecure()).isTrue();

		mvc.perform(get("/api/session").with(phone()).cookie(secure)).andExpect(status().isOk());
		mvc.perform(get("/api/session").with(phone()).cookie(overHttp)).andExpect(status().isUnauthorized());
		mvc.perform(json(post("/api/pair/handoff"), "{\"code\":\"" + code + "\"}").with(phone()))
			.andExpect(status().isForbidden());
	}

	@Test
	void anUnpairedPhoneCanFetchTheCertificateAndLearnTheHttpsPort() throws Exception {
		mvc.perform(get("/api/server").with(phone())).andExpect(jsonPath("$.httpsPort").value(8443));
		mvc.perform(get("/androiddrop-ca.crt").with(phone()))
			.andExpect(status().isOk())
			.andExpect(result -> assertThat(result.getResponse().getContentAsString()).startsWith("-----BEGIN CERTIFICATE-----"));
	}

	@Test
	void hostCanRevokeAPhone() throws Exception {
		Cookie phone = pair("Old phone");
		String id = deviceId(phone);
		mvc.perform(as(phone, action(delete("/api/devices/{id}", id)))).andExpect(status().isForbidden());
		mvc.perform(delete("/api/devices/{id}", id).header(DeviceAuthFilter.CSRF_HEADER, "1"))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/session").with(phone()).cookie(phone)).andExpect(status().isUnauthorized());
	}

}
