package com.androiddrop.transfer;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads the EXIF orientation tag (1-8) from a JPEG. Phone cameras usually store photos in sensor orientation and
 * set this tag rather than rotating the pixels; browsers honour it, so thumbnails must too.
 */
final class ExifOrientation {

	static final int NORMAL = 1;

	private static final int ORIENTATION_TAG = 0x0112;

	private ExifOrientation() {
	}

	static int read(Path jpeg) {
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(jpeg)))) {
			if (in.readUnsignedShort() != 0xFFD8) {
				return NORMAL;
			}
			while (true) {
				int marker = in.readUnsignedShort();
				// Metadata segments all come before start-of-scan (DA); give up once image data begins.
				if ((marker & 0xFF00) != 0xFF00 || marker == 0xFFDA || marker == 0xFFD9) {
					return NORMAL;
				}
				int length = in.readUnsignedShort() - 2;
				if (length < 0) {
					return NORMAL;
				}
				if (marker == 0xFFE1) {
					int orientation = fromExifSegment(in.readNBytes(length));
					if (orientation != 0) {
						return orientation;
					}
				}
				else {
					in.skipNBytes(length);
				}
			}
		}
		catch (IOException | RuntimeException ex) {
			return NORMAL;
		}
	}

	/** The orientation from an APP1 segment, or 0 if it isn't EXIF or has no orientation tag. */
	static int fromExifSegment(byte[] segment) {
		if (segment.length < 14 || segment[0] != 'E' || segment[1] != 'x' || segment[2] != 'i' || segment[3] != 'f'
				|| segment[4] != 0 || segment[5] != 0) {
			return 0;
		}
		ByteBuffer tiff = ByteBuffer.wrap(segment, 6, segment.length - 6).slice();
		if (tiff.get(0) == 'I' && tiff.get(1) == 'I') {
			tiff.order(ByteOrder.LITTLE_ENDIAN);
		}
		else if (tiff.get(0) != 'M' || tiff.get(1) != 'M') {
			return 0;
		}
		if (tiff.getShort(2) != 42) {
			return 0;
		}
		int ifd = tiff.getInt(4);
		if (ifd < 8 || ifd + 2 > tiff.limit()) {
			return 0;
		}
		int entries = Short.toUnsignedInt(tiff.getShort(ifd));
		for (int i = 0; i < entries; i++) {
			int entry = ifd + 2 + i * 12;
			if (entry + 12 > tiff.limit()) {
				return 0;
			}
			if (Short.toUnsignedInt(tiff.getShort(entry)) == ORIENTATION_TAG) {
				int value = Short.toUnsignedInt(tiff.getShort(entry + 8));
				return value >= 1 && value <= 8 ? value : 0;
			}
		}
		return 0;
	}

}
