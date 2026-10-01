package com.androiddrop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.androiddrop.device.Device;
import com.androiddrop.device.DeviceService;
import com.androiddrop.storage.StorageLayout;
import com.androiddrop.transfer.Transfer;
import com.androiddrop.transfer.TransferFile.State;
import com.androiddrop.transfer.TransferService;

import jakarta.servlet.http.Cookie;

/** The AirDrop-style handshake: announce, upload, accept or decline, deliver. */
class TransferApiTests extends ApiTestSupport {

	@Autowired
	TransferService transferService;

	@Autowired
	DeviceService deviceService;

	@Autowired
	StorageLayout storage;

	@Test
	void phoneToMacLandsInTheFolderOnlyAfterAccepting() throws Exception {
		Cookie phone = pair("Pixel 8");
		byte[] photo = "fake-jpeg-bytes-0123456789".getBytes(StandardCharsets.UTF_8);

		MvcResult created = mvc.perform(as(phone, json(post("/api/transfers"),
				"{\"recipientId\":\"host\",\"files\":[{\"name\":\"../../IMG 2024:01.jpg\",\"size\":" + photo.length
						+ ",\"mimeType\":\"image/jpeg\"}]}")))
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.files[0].state").value("UPLOADING"))
			.andExpect(jsonPath("$.files[0].name").value("IMG 2024-01.jpg"))
			.andExpect(jsonPath("$.senderPlatform").value("ANDROID"))
			.andReturn();
		Sent sent = new Sent(read(created, "$.id"), read(created, "$.files[0].id"));
		mvc.perform(as(phone, upload(sent, photo))).andExpect(jsonPath("$.files[0].state").value("READY"));

		// Staged, not delivered: nothing in the folder and the Mac can't open it yet.
		assertThat(STORAGE.resolve("IMG 2024-01.jpg")).doesNotExist();
		mvc.perform(get("/api/transfers/{id}/files/{f}/content", sent.id(), sent.fileId())).andExpect(status().isForbidden());

		mvc.perform(action(post("/api/transfers/{id}/accept", sent.id())))
			.andExpect(jsonPath("$.status").value("ACCEPTED"))
			.andExpect(jsonPath("$.files[0].state").value("SAVED"));
		assertThat(STORAGE.resolve("IMG 2024-01.jpg")).hasBinaryContent(photo);
		assertThat(STORAGE.resolve(".androiddrop/staging").resolve(sent.id())).doesNotExist();

		mvc.perform(get("/api/transfers/{id}/files/{f}/content", sent.id(), sent.fileId()).header(HttpHeaders.RANGE, "bytes=0-3"))
			.andExpect(status().isPartialContent())
			.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo("fake"));
	}

	@Test
	void acceptingBeforeTheUploadFinishesSavesItWhenItArrives() throws Exception {
		Cookie phone = pair("Pixel");
		byte[] bytes = "late arrival".getBytes(StandardCharsets.UTF_8);
		MvcResult created = mvc.perform(as(phone, json(post("/api/transfers"),
				"{\"recipientId\":\"host\",\"files\":[{\"name\":\"late.txt\",\"size\":" + bytes.length + "}]}")))
			.andReturn();
		Sent sent = new Sent(read(created, "$.id"), read(created, "$.files[0].id"));

		mvc.perform(action(post("/api/transfers/{id}/accept", sent.id())))
			.andExpect(jsonPath("$.files[0].state").value("UPLOADING"));
		mvc.perform(as(phone, upload(sent, bytes))).andExpect(jsonPath("$.files[0].state").value("SAVED"));
		assertThat(STORAGE.resolve("late.txt")).hasBinaryContent(bytes);
	}

	@Test
	void decliningDeletesTheStagedFile() throws Exception {
		Cookie phone = pair("Pixel");
		Sent sent = sendFile(phone, "host", "unwanted.pdf", "application/pdf", new byte[2048]);

		mvc.perform(action(post("/api/transfers/{id}/decline", sent.id())))
			.andExpect(jsonPath("$.status").value("DECLINED"))
			.andExpect(jsonPath("$.files[0].state").value("REMOVED"));
		assertThat(STORAGE.resolve(".androiddrop/staging").resolve(sent.id())).doesNotExist();
		assertThat(STORAGE.resolve("unwanted.pdf")).doesNotExist();
		mvc.perform(action(post("/api/transfers/{id}/accept", sent.id()))).andExpect(status().isConflict());
	}

	@Test
	void macToPhoneIsDeliveredWhenThePhoneDownloadsIt() throws Exception {
		Cookie phone = pair("Pixel");
		String phoneId = deviceId(phone);
		byte[] png = png();
		Sent sent = sendFile(null, phoneId, "screenshot.png", "image/png", png);

		// Before accepting, the phone may see a preview (like AirDrop's prompt) but not the file itself.
		mvc.perform(as(phone, get("/api/transfers/{id}/files/{f}/thumbnail", sent.id(), sent.fileId())))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"));
		mvc.perform(as(phone, get("/api/transfers/{id}/files/{f}/content", sent.id(), sent.fileId())))
			.andExpect(status().isForbidden());

		mvc.perform(as(phone, action(post("/api/transfers/{id}/accept", sent.id()))))
			.andExpect(jsonPath("$.files[0].state").value("READY"));
		mvc.perform(as(phone, get("/api/transfers/{id}/files/{f}/content", sent.id(), sent.fileId()).param("download", "true")))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, Matchers.startsWith("attachment")))
			.andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(png));

		mvc.perform(get("/api/transfers"))
			.andExpect(jsonPath("$[?(@.id == '" + sent.id() + "')].files[0].state").value("DELIVERED"));
	}

	@Test
	void autoAcceptSkipsThePrompt() throws Exception {
		Cookie phone = pair("Trusted phone");
		mvc.perform(as(phone, json(patch("/api/session/preferences"), "{\"autoAccept\":true}")));
		mvc.perform(json(post("/api/transfers"), "{\"recipientId\":\"" + deviceId(phone) + "\",\"text\":\"hello\"}"))
			.andExpect(jsonPath("$.status").value("ACCEPTED"));
	}

	@Test
	void senderCanCancelAndLaterUploadsAreRefused() throws Exception {
		Cookie phone = pair("Pixel");
		byte[] bytes = new byte[100];
		MvcResult created = mvc.perform(as(phone, json(post("/api/transfers"),
				"{\"recipientId\":\"host\",\"files\":[{\"name\":\"a.bin\",\"size\":100}]}")))
			.andReturn();
		Sent sent = new Sent(read(created, "$.id"), read(created, "$.files[0].id"));

		mvc.perform(action(post("/api/transfers/{id}/cancel", sent.id()))).andExpect(status().isForbidden());
		mvc.perform(as(phone, action(post("/api/transfers/{id}/cancel", sent.id()))))
			.andExpect(jsonPath("$.status").value("CANCELLED"));
		mvc.perform(as(phone, upload(sent, bytes))).andExpect(status().isConflict());
		mvc.perform(action(post("/api/transfers/{id}/accept", sent.id()))).andExpect(status().isConflict());
	}

	@Test
	void onlyTheTwoEndsCanSeeATransfer() throws Exception {
		Cookie alice = pair("Alice phone");
		Cookie bob = pair("Bob phone");
		MvcResult sent = mvc.perform(json(post("/api/transfers"),
				"{\"recipientId\":\"" + deviceId(alice) + "\",\"text\":\"for alice only\"}"))
			.andReturn();
		String id = read(sent, "$.id");

		mvc.perform(as(bob, get("/api/transfers"))).andExpect(jsonPath("$[?(@.id == '" + id + "')]").isEmpty());
		mvc.perform(as(bob, action(post("/api/transfers/{id}/accept", id)))).andExpect(status().isNotFound());
		mvc.perform(as(alice, action(post("/api/transfers/{id}/accept", id))))
			.andExpect(jsonPath("$.status").value("ACCEPTED"))
			.andExpect(jsonPath("$.text").value("for alice only"));
	}

	@Test
	void invalidRequestsAreRejected() throws Exception {
		Cookie phone = pair("Pixel");
		mvc.perform(as(phone, json(post("/api/transfers"), "{\"text\":\"to nobody\"}"))).andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/transfers"), "{\"recipientId\":\"host\",\"text\":\"to myself\"}"))
			.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/transfers"), "{\"recipientId\":\"" + deviceId(phone)
				+ "\",\"text\":\"both\",\"files\":[{\"name\":\"a\",\"size\":1}]}"))
			.andExpect(status().isBadRequest());

		MvcResult created = mvc.perform(as(phone, json(post("/api/transfers"),
				"{\"recipientId\":\"host\",\"files\":[{\"name\":\"a.bin\",\"size\":10}]}")))
			.andReturn();
		Sent sent = new Sent(read(created, "$.id"), read(created, "$.files[0].id"));
		mvc.perform(as(phone, upload(sent, new byte[3]))).andExpect(status().isBadRequest());
	}

	@Test
	void htmlIsNeverServedInline() throws Exception {
		Cookie phone = pair("Pixel");
		Sent sent = sendFile(phone, "host", "page.html", "text/html", "<script>alert(1)</script>".getBytes());
		mvc.perform(action(post("/api/transfers/{id}/accept", sent.id())));

		mvc.perform(get("/api/transfers/{id}/files/{f}/content", sent.id(), sent.fileId()))
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, Matchers.startsWith("attachment")))
			.andExpect(header().string("Content-Security-Policy", "sandbox"));
	}

	@Test
	void filesMovedAwayInFinderAreReportedAsUnavailable() throws Exception {
		Cookie phone = pair("Pixel");
		Sent sent = sendFile(phone, "host", "moved-later.png", "image/png", png());
		mvc.perform(action(post("/api/transfers/{id}/accept", sent.id())))
			.andExpect(jsonPath("$.files[0].available").value(true))
			.andExpect(jsonPath("$.files[0].thumbnail").value(true));

		Files.delete(STORAGE.resolve("moved-later.png"));
		mvc.perform(get("/api/transfers"))
			.andExpect(jsonPath("$[?(@.id == '" + sent.id() + "')].files[0].available").value(false))
			.andExpect(jsonPath("$[?(@.id == '" + sent.id() + "')].files[0].thumbnail").value(false));
		mvc.perform(get("/api/transfers/{id}/files/{f}/content", sent.id(), sent.fileId())).andExpect(status().isGone());
	}

	@Test
	void removingFromHistoryKeepsFilesAlreadySaved() throws Exception {
		Cookie phone = pair("Pixel");
		Sent sent = sendFile(phone, "host", "keep-me.txt", "text/plain", "mine now".getBytes());
		mvc.perform(action(post("/api/transfers/{id}/accept", sent.id())));

		mvc.perform(action(delete("/api/transfers/{id}", sent.id()))).andExpect(status().isNoContent());
		assertThat(STORAGE.resolve("keep-me.txt")).exists();
		mvc.perform(get("/api/transfers")).andExpect(jsonPath("$[?(@.id == '" + sent.id() + "')]").isEmpty());
	}

	@Test
	void transfersShowTheSendersCurrentName() throws Exception {
		Cookie phone = pair("Android phone");
		MvcResult sent = mvc.perform(as(phone, json(post("/api/transfers"), "{\"recipientId\":\"host\",\"text\":\"hi\"}")))
			.andReturn();
		mvc.perform(as(phone, json(patch("/api/session"), "{\"name\":\"Galaxy S25\"}")));
		mvc.perform(get("/api/transfers"))
			.andExpect(jsonPath("$[?(@.id == '" + read(sent, "$.id") + "')].senderName").value("Galaxy S25"));
	}

	@Test
	void onlyDevicesAndSettingsOutliveTheServer() throws Exception {
		Cookie phone = pair("Pixel");
		mvc.perform(as(phone, json(post("/api/transfers"), "{\"recipientId\":\"host\",\"text\":\"door code 4471\"}")))
			.andExpect(status().isOk());
		sendFile(phone, "host", "waiting.txt", "text/plain", "x".getBytes());
		Path meta = STORAGE.resolve(".androiddrop");
		assertThat(meta.resolve("staging")).isNotEmptyDirectory();
		assertThat(meta.resolve("transfers.json")).doesNotExist();

		storage.clearScratch(); // what stopping the server does

		for (String dir : List.of("staging", "tmp", "thumbs")) {
			assertThat(meta.resolve(dir)).doesNotExist();
		}
		assertThat(meta.resolve("devices.json")).exists();
		assertThat(meta.resolve("host.json")).doesNotExist();
	}

	@Test
	void aSecondUploadOfTheSameFileIsRefusedWhileTheFirstIsRunning() throws Exception {
		Cookie phone = pair("Pixel");
		byte[] bytes = "12345678".getBytes(StandardCharsets.UTF_8);
		MvcResult created = mvc.perform(as(phone, json(post("/api/transfers"),
				"{\"recipientId\":\"host\",\"files\":[{\"name\":\"slow.bin\",\"size\":" + bytes.length + "}]}")))
			.andReturn();
		Sent sent = new Sent(read(created, "$.id"), read(created, "$.files[0].id"));
		Device sender = deviceService.find(deviceId(phone)).orElseThrow();

		// The first upload stalls halfway, like a phone on a weak connection.
		CountDownLatch halfway = new CountDownLatch(1);
		CountDownLatch resume = new CountDownLatch(1);
		InputStream slowBody = new InputStream() {

			private int position;

			@Override
			public int read() throws IOException {
				if (position == bytes.length / 2) {
					halfway.countDown();
					awaitQuietly(resume);
				}
				return position < bytes.length ? bytes[position++] : -1;
			}

		};
		CompletableFuture<Transfer> first = CompletableFuture.supplyAsync(() -> {
			try {
				return transferService.receiveFile(sender, sent.id(), sent.fileId(), 0, bytes.length, slowBody);
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		});
		assertThat(halfway.await(5, TimeUnit.SECONDS)).isTrue();

		// Locked, not a conflict: the phone waits and tries again, as after a connection that hasn't timed out yet.
		mvc.perform(as(phone, upload(sent, bytes))).andExpect(status().isLocked());

		resume.countDown();
		assertThat(first.get(5, TimeUnit.SECONDS).file(sent.fileId()).orElseThrow().state()).isEqualTo(State.READY);
	}

	@Test
	void anUploadCutOffByWifiContinuesFromWhereItStopped() throws Exception {
		Cookie phone = pair("Pixel");
		byte[] movie = "0123456789".repeat(1000).getBytes(StandardCharsets.UTF_8);
		MvcResult created = mvc.perform(as(phone, json(post("/api/transfers"),
				"{\"recipientId\":\"host\",\"files\":[{\"name\":\"movie.mkv\",\"size\":" + movie.length + "}]}")))
			.andReturn();
		Sent sent = new Sent(read(created, "$.id"), read(created, "$.files[0].id"));
		Device sender = deviceService.find(deviceId(phone)).orElseThrow();

		// The connection drops after 4000 bytes: the request body just ends early.
		byte[] firstPart = java.util.Arrays.copyOf(movie, 4000);
		assertThatUploadFails(() -> transferService.receiveFile(sender, sent.id(), sent.fileId(), 0, -1,
				new java.io.ByteArrayInputStream(firstPart)));
		mvc.perform(as(phone, get("/api/transfers/{id}/files/{f}/received", sent.id(), sent.fileId())))
			.andExpect(jsonPath("$.received").value(4000));

		// Continuing from anywhere else is refused, so the file can't end up with a gap or an overlap.
		byte[] rest = java.util.Arrays.copyOfRange(movie, 4000, movie.length);
		byte[] fromTooEarly = java.util.Arrays.copyOfRange(movie, 3000, movie.length);
		mvc.perform(as(phone, upload(sent, fromTooEarly).param("offset", "3000"))).andExpect(status().isRequestedRangeNotSatisfiable());

		mvc.perform(as(phone, upload(sent, rest).param("offset", "4000")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.files[0].state").value("READY"));
		mvc.perform(action(post("/api/transfers/{id}/accept", sent.id()))).andExpect(status().isOk());
		assertThat(STORAGE.resolve("movie.mkv")).hasBinaryContent(movie);
	}

	@Test
	void aPhoneDownloadResumesAndStillCountsAsDelivered() throws Exception {
		Cookie phone = pair("Pixel");
		byte[] movie = "abcdefghij".repeat(500).getBytes(StandardCharsets.UTF_8);
		Sent sent = sendFile(null, deviceId(phone), "film.mp4", "video/mp4", movie);
		mvc.perform(as(phone, action(post("/api/transfers/{id}/accept", sent.id())))).andExpect(status().isOk());

		// Chrome resumes an interrupted download with the file's ETag in If-Range.
		MvcResult first = mvc.perform(as(phone, get("/api/transfers/{id}/files/{f}/content", sent.id(), sent.fileId())
			.param("download", "true")
			.header(HttpHeaders.RANGE, "bytes=1000-")
			.header(HttpHeaders.IF_RANGE, "\"" + sent.fileId() + "\"")))
			.andExpect(status().isPartialContent())
			.andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 1000-4999/5000"))
			.andReturn();
		assertThat(first.getResponse().getContentAsByteArray()).isEqualTo(java.util.Arrays.copyOfRange(movie, 1000, 5000));
		mvc.perform(get("/api/transfers"))
			.andExpect(jsonPath("$[?(@.id == '" + sent.id() + "')].files[0].state").value("DELIVERED"));

		// A validator that doesn't match (the file changed) gets the whole file instead.
		mvc.perform(as(phone, get("/api/transfers/{id}/files/{f}/content", sent.id(), sent.fileId())
			.param("download", "true")
			.header(HttpHeaders.RANGE, "bytes=1000-")
			.header(HttpHeaders.IF_RANGE, "\"something-else\"")))
			.andExpect(status().isOk())
			.andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(movie));
	}

	private static void assertThatUploadFails(java.util.concurrent.Callable<?> upload) {
		try {
			upload.call();
		}
		catch (Exception ex) {
			return;
		}
		throw new AssertionError("The upload should have failed");
	}

	@Test
	void macCanSaveIntoAFolderItPicksWhenAccepting() throws Exception {
		Cookie phone = pair("Pixel");
		Path picked = Files.createTempDirectory("picked-folder").toRealPath();
		byte[] photo = "trip-photo".getBytes(StandardCharsets.UTF_8);
		MvcResult created = mvc.perform(as(phone, json(post("/api/transfers"),
				"{\"recipientId\":\"host\",\"files\":[{\"name\":\"trip.jpg\",\"size\":" + photo.length + "}]}")))
			.andReturn();
		Sent sent = new Sent(read(created, "$.id"), read(created, "$.files[0].id"));

		// Accepted before the upload finishes: the file still lands in the picked folder once it arrives.
		mvc.perform(json(post("/api/transfers/{id}/accept", sent.id()), "{\"folder\":\"" + picked + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.folder").value(picked.toString()));
		mvc.perform(as(phone, upload(sent, photo))).andExpect(jsonPath("$.files[0].state").value("SAVED"));

		assertThat(picked.resolve("trip.jpg")).hasBinaryContent(photo);
		assertThat(STORAGE.resolve("trip.jpg")).doesNotExist();
		mvc.perform(get("/api/transfers/{id}/files/{f}/content", sent.id(), sent.fileId()))
			.andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(photo));
		mvc.perform(get("/api/host/folders"))
			.andExpect(jsonPath("$.folders[0].isDefault").value(true))
			.andExpect(jsonPath("$.folders[1].path").value(picked.toString()));
	}

	@Test
	void filesGoToTheDefaultFolderSetInSettings() throws Exception {
		Path downloads = Files.createTempDirectory("default-folder").toRealPath();
		mvc.perform(json(put("/api/host/folders/default"), "{\"path\":\"" + downloads + "\"}"))
			.andExpect(jsonPath("$.folders[0].path").value(downloads.toString()))
			.andExpect(jsonPath("$.folders[0].isDefault").value(true));
		// Kept with the host's other preferences, the one file that outlives the server.
		assertThat(Files.readString(STORAGE.resolve(".androiddrop/devices.json"))).contains(downloads.toString());
		try {
			Cookie phone = pair("Pixel");
			Sent sent = sendFile(phone, "host", "default.txt", "text/plain", "d".getBytes());
			mvc.perform(action(post("/api/transfers/{id}/accept", sent.id())))
				.andExpect(jsonPath("$.folder").value(downloads.toString()));
			assertThat(downloads.resolve("default.txt")).exists();
		}
		finally {
			mvc.perform(json(put("/api/host/folders/default"), "{\"path\":\"" + STORAGE + "\"}"));
		}
	}

	@Test
	void onlyTheMacPicksFoldersAndOnlyUsableOnes() throws Exception {
		Cookie phone = pair("Pixel");
		String phoneId = deviceId(phone);
		Sent toMac = sendFile(phone, "host", "a.txt", "text/plain", "a".getBytes());
		Sent toPhone = sendFile(null, phoneId, "b.txt", "text/plain", "b".getBytes());

		for (String folder : new String[] { "relative/path", STORAGE.resolve("missing").toString(),
				STORAGE.resolve(".androiddrop").toString() }) {
			mvc.perform(json(post("/api/transfers/{id}/accept", toMac.id()), "{\"folder\":\"" + folder + "\"}"))
				.andExpect(status().isBadRequest());
		}
		mvc.perform(as(phone, json(post("/api/transfers/{id}/accept", toPhone.id()), "{\"folder\":\"" + STORAGE + "\"}")))
			.andExpect(status().isForbidden());
		mvc.perform(as(phone, get("/api/host/folders"))).andExpect(status().isForbidden());
	}

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await(5, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	@Test
	void sameNameDoesNotOverwrite() throws Exception {
		Cookie phone = pair("Pixel");
		for (String content : new String[] { "one", "two" }) {
			Sent sent = sendFile(phone, "host", "notes.txt", "text/plain", content.getBytes());
			mvc.perform(action(post("/api/transfers/{id}/accept", sent.id())));
		}
		try (Stream<java.nio.file.Path> files = Files.list(STORAGE)) {
			assertThat(files.map(path -> path.getFileName().toString())).contains("notes.txt", "notes (1).txt");
		}
	}

	private static byte[] png() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB), "png", out);
		return out.toByteArray();
	}

}
