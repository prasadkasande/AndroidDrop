package com.androiddrop.device;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.androiddrop.storage.JsonStore;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** devices.json written by older versions must still load, or every phone would have to pair again. */
class DeviceFileTests {

	@Test
	void olderDeviceFilesStillLoad(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("devices.json");
		Files.writeString(file, """
				[ {
				  "id" : "a1", "name" : "Pixel", "platform" : "ANDROID", "host" : false,
				  "pairedAt" : "2026-09-29T10:00:00Z", "tokenHash" : null, "autoAccept" : null,
				  "tokenHashes" : [ "h1", "h2" ]
				} ]
				""");
		List<Device> devices = new JsonStore<List<Device>>(file, JsonMapper.builder().build(), new TypeReference<>() {
		}).load(List::of);

		assertThat(devices).singleElement().satisfies(device -> {
			assertThat(device.name()).isEqualTo("Pixel");
			assertThat(device.autoAccept()).isFalse();
			assertThat(device.tokenHashes()).containsExactly("h1", "h2");
		});
		assertThat(file).exists(); // read as it is, not set aside as unreadable
	}

}
