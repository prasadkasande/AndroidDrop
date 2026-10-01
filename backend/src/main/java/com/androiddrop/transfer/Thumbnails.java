package com.androiddrop.transfer;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.androiddrop.storage.StorageLayout;

/**
 * Small JPEG previews for the activity feed, so a phone doesn't download full-size photos just to show a list.
 * Generated on first request and cached under {@code .androiddrop/thumbs} until the server stops.
 */
@Component
public class Thumbnails {

	private static final Logger log = LoggerFactory.getLogger(Thumbnails.class);

	/** Formats the JDK can decode. Browsers render WebP/GIF/AVIF themselves, so those use the original. */
	private static final Set<String> SUPPORTED = Set.of("image/jpeg", "image/png", "image/bmp");

	/** Short edge in pixels: previews are at most 96 CSS px, so this is sharp on 2x screens. */
	static final int SHORT_EDGE = 240;

	static {
		ImageIO.setUseCache(false);
	}

	private final Path dir;
	/** Items that couldn't be decoded, so a list redrawing doesn't make the server try again every time. */
	private final Set<String> undecodable = ConcurrentHashMap.newKeySet();

	/** Decoding a photo is CPU- and memory-heavy; a list full of new photos shouldn't decode them all at once. */
	private final Semaphore decoding = new Semaphore(Math.max(2, Runtime.getRuntime().availableProcessors()));

	public Thumbnails(StorageLayout storage) {
		this.dir = storage.thumbsDir();
	}

	public static boolean supports(String mimeType) {
		return SUPPORTED.contains(mimeType);
	}

	/** Whether {@link #thumbnailFor} is worth asking: a supported type that hasn't already failed to decode. */
	public boolean offers(String itemId, String mimeType) {
		return supports(mimeType) && !undecodable.contains(itemId);
	}

	/** The cached thumbnail, created on first use. Empty if the image can't be decoded. */
	public Optional<Path> thumbnailFor(String itemId, Path source, String mimeType) {
		Path thumbnail = dir.resolve(itemId + ".jpg");
		if (Files.exists(thumbnail)) {
			return Optional.of(thumbnail);
		}
		if (undecodable.contains(itemId)) {
			return Optional.empty();
		}
		decoding.acquireUninterruptibly();
		try {
			if (Files.exists(thumbnail)) {
				return Optional.of(thumbnail); // made by another request while this one waited
			}
			BufferedImage image = read(source);
			if (image == null) {
				undecodable.add(itemId);
				return Optional.empty();
			}
			int orientation = mimeType.equals("image/jpeg") ? ExifOrientation.read(source) : ExifOrientation.NORMAL;
			Files.createDirectories(dir);
			Path tmp = dir.resolve(itemId + "-" + UUID.randomUUID() + ".tmp");
			writeJpeg(orient(downscale(image), orientation), tmp);
			Files.move(tmp, thumbnail, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			return Optional.of(thumbnail);
		}
		catch (IOException | RuntimeException ex) {
			// CMYK JPEGs, truncated uploads... the UI falls back to a file icon.
			log.debug("No thumbnail for {}: {}", source.getFileName(), ex.toString());
			undecodable.add(itemId);
			return Optional.empty();
		}
		finally {
			decoding.release();
		}
	}

	public void delete(String itemId) throws IOException {
		undecodable.remove(itemId);
		Files.deleteIfExists(dir.resolve(itemId + ".jpg"));
	}

	/** Decodes with subsampling so a 50-megapixel photo never has to be fully decoded into memory. */
	private static BufferedImage read(Path source) throws IOException {
		try (ImageInputStream in = ImageIO.createImageInputStream(source.toFile())) {
			Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
			if (readers == null || !readers.hasNext()) {
				return null;
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(in, true, true);
				int step = Math.max(1, Math.min(reader.getWidth(0), reader.getHeight(0)) / (SHORT_EDGE * 2));
				ImageReadParam param = reader.getDefaultReadParam();
				param.setSourceSubsampling(step, step, 0, 0);
				return reader.read(0, param);
			}
			finally {
				reader.dispose();
			}
		}
	}

	/** Halves repeatedly, then scales to the final size, which looks far better than one big bilinear step. */
	static BufferedImage downscale(BufferedImage source) {
		BufferedImage current = toRgb(source, source.getWidth(), source.getHeight());
		while (Math.min(current.getWidth(), current.getHeight()) > SHORT_EDGE) {
			double factor = Math.max(0.5, (double) SHORT_EDGE / Math.min(current.getWidth(), current.getHeight()));
			current = toRgb(current, (int) Math.max(1, Math.round(current.getWidth() * factor)),
					(int) Math.max(1, Math.round(current.getHeight() * factor)));
		}
		return current;
	}

	/** Draws onto an opaque RGB canvas (JPEG has no alpha; transparent areas become white). */
	private static BufferedImage toRgb(BufferedImage source, int width, int height) {
		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, width, height);
		g.drawImage(source, 0, 0, width, height, null);
		g.dispose();
		return out;
	}

	/** Applies an EXIF orientation (1-8) so the thumbnail matches what a browser shows. */
	static BufferedImage orient(BufferedImage image, int orientation) {
		if (orientation <= ExifOrientation.NORMAL || orientation > 8) {
			return image;
		}
		int w = image.getWidth();
		int h = image.getHeight();
		boolean swap = orientation >= 5;
		AffineTransform t = new AffineTransform();
		switch (orientation) {
			case 2 -> { // mirror horizontal
				t.scale(-1, 1);
				t.translate(-w, 0);
			}
			case 3 -> { // rotate 180
				t.translate(w, h);
				t.rotate(Math.PI);
			}
			case 4 -> { // mirror vertical
				t.scale(1, -1);
				t.translate(0, -h);
			}
			case 5 -> { // transpose
				t.rotate(-Math.PI / 2);
				t.scale(-1, 1);
			}
			case 6 -> { // rotate 90 clockwise
				t.translate(h, 0);
				t.rotate(Math.PI / 2);
			}
			case 7 -> { // transverse
				t.scale(-1, 1);
				t.translate(-h, 0);
				t.translate(0, w);
				t.rotate(3 * Math.PI / 2);
			}
			default -> { // 8: rotate 90 counter-clockwise
				t.translate(0, w);
				t.rotate(3 * Math.PI / 2);
			}
		}
		BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(image, t, null);
		g.dispose();
		return out;
	}

	private static void writeJpeg(BufferedImage image, Path target) throws IOException {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ImageWriteParam param = writer.getDefaultWriteParam();
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		param.setCompressionQuality(0.82f);
		try (ImageOutputStream out = ImageIO.createImageOutputStream(target.toFile())) {
			writer.setOutput(out);
			writer.write(null, new IIOImage(image, null, null), param);
		}
		finally {
			writer.dispose();
		}
	}

}
