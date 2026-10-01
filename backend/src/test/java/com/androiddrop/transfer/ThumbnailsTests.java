package com.androiddrop.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.androiddrop.config.DropProperties;
import com.androiddrop.storage.StorageLayout;

class ThumbnailsTests {

	@TempDir
	Path storage;

	Thumbnails thumbnails;

	@BeforeEach
	void setUp() throws IOException {
		thumbnails = new Thumbnails(new StorageLayout(new DropProperties(storage, "", Duration.ofMinutes(10), 5, 1000, Duration.ofHours(1), Duration.ofHours(24), 0)));
	}

	@Test
	void readsOrientationFromExif() throws IOException {
		Path rotated = write("rotated.jpg", withExifOrientation(landscapeJpeg(), 6));
		Path plain = write("plain.jpg", landscapeJpeg());
		assertThat(ExifOrientation.read(rotated)).isEqualTo(6);
		assertThat(ExifOrientation.read(plain)).isEqualTo(ExifOrientation.NORMAL);
		assertThat(ExifOrientation.read(write("junk.jpg", "not a jpeg".getBytes()))).isEqualTo(ExifOrientation.NORMAL);
	}

	@Test
	void thumbnailIsSmallAndRotatedLikeTheBrowserShowsIt() throws IOException {
		// 1000x500 landscape, red on the left half, stored with "rotate 90° clockwise".
		Path photo = write("photo.jpg", withExifOrientation(landscapeJpeg(), 6));

		BufferedImage thumb = ImageIO.read(thumbnails.thumbnailFor("item-1", photo, "image/jpeg").orElseThrow().toFile());

		assertThat(thumb.getWidth()).isEqualTo(Thumbnails.SHORT_EDGE);
		assertThat(thumb.getHeight()).isEqualTo(Thumbnails.SHORT_EDGE * 2);
		// Rotating clockwise moves the left (red) half to the top.
		assertThat(isRed(thumb.getRGB(thumb.getWidth() / 2, thumb.getHeight() / 4))).isTrue();
		assertThat(isRed(thumb.getRGB(thumb.getWidth() / 2, thumb.getHeight() * 3 / 4))).isFalse();
	}

	@Test
	void undecodableImagesHaveNoThumbnail() throws IOException {
		Path broken = write("broken.jpg", "definitely not an image".getBytes(StandardCharsets.UTF_8));
		assertThat(thumbnails.thumbnailFor("item-2", broken, "image/jpeg")).isEmpty();
		assertThat(Thumbnails.supports("image/svg+xml")).isFalse();
	}

	private Path write(String name, byte[] bytes) throws IOException {
		return Files.write(storage.resolve(name), bytes);
	}

	private static byte[] landscapeJpeg() throws IOException {
		BufferedImage image = new BufferedImage(1000, 500, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.BLUE);
		g.fillRect(0, 0, 1000, 500);
		g.setColor(Color.RED);
		g.fillRect(0, 0, 500, 500);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "jpeg", out);
		return out.toByteArray();
	}

	/** Inserts a minimal big-endian EXIF APP1 segment holding just the orientation tag. */
	static byte[] withExifOrientation(byte[] jpeg, int orientation) {
		ByteBuffer tiff = ByteBuffer.allocate(26)
			.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(8)
			.putShort((short) 1)
			.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0)
			.putInt(0);
		byte[] header = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
		int length = header.length + tiff.capacity() + 2;
		ByteBuffer out = ByteBuffer.allocate(jpeg.length + length + 2);
		out.put((byte) 0xFF).put((byte) 0xD8).put((byte) 0xFF).put((byte) 0xE1).putShort((short) length);
		out.put(header).put(tiff.array()).put(jpeg, 2, jpeg.length - 2);
		return out.array();
	}

	private static boolean isRed(int rgb) {
		Color color = new Color(rgb);
		return color.getRed() > 180 && color.getBlue() < 90;
	}

}
