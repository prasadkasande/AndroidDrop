package com.androiddrop.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileNamesTests {

	@Test
	void stripsPathsHiddenPrefixesAndControlCharacters() {
		assertThat(FileNames.sanitize("../../etc/passwd")).isEqualTo("passwd");
		assertThat(FileNames.sanitize("C:\\Users\\me\\report.pdf")).isEqualTo("report.pdf");
		assertThat(FileNames.sanitize(".bashrc")).isEqualTo("bashrc");
		assertThat(FileNames.sanitize("..")).isEqualTo("file");
		assertThat(FileNames.sanitize("a\u0000b\nc.txt")).isEqualTo("abc.txt");
		assertThat(FileNames.sanitize("12:30 meeting.m4a")).isEqualTo("12-30 meeting.m4a");
		assertThat(FileNames.sanitize("   ")).isEqualTo("file");
		assertThat(FileNames.sanitize(null)).isEqualTo("file");
	}

	@Test
	void capsLengthInUtf8BytesAndKeepsExtension() {
		String longName = "写真".repeat(200) + ".jpeg";
		String safe = FileNames.sanitize(longName);
		assertThat(safe).endsWith(".jpeg");
		assertThat(safe.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(200);
	}

	@Test
	void numbersDuplicates(@TempDir Path dir) throws Exception {
		assertThat(FileNames.uniqueIn(dir, "photo.jpg").getFileName()).hasToString("photo.jpg");
		Files.createFile(dir.resolve("photo.jpg"));
		Files.createFile(dir.resolve("photo (1).jpg"));
		assertThat(FileNames.uniqueIn(dir, "photo.jpg").getFileName()).hasToString("photo (2).jpg");
		Files.createFile(dir.resolve("README"));
		assertThat(FileNames.uniqueIn(dir, "README").getFileName()).hasToString("README (1)");
	}

}
