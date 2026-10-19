package com.naukrinearby.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Minimal RIFF/WAVE header reader for the voice-search upload. Walks at most {@link #MAX_CHUNKS} chunks
 * with bounds checks, so a hostile file cannot loop or read past the buffer. Reads headers only; the
 * audio itself is never inspected or logged.
 *
 * @param audioFormat 1 = PCM
 * @param dataBytes   size of the {@code data} chunk (clamped to what was actually uploaded)
 */
public record WavFormat(int audioFormat, int channels, int sampleRate, int bitsPerSample, long dataBytes) {

	private static final int MAX_CHUNKS = 16;

	public static Optional<WavFormat> parse(byte[] bytes) {
		if (bytes == null || bytes.length < 12 || !tag(bytes, 0, "RIFF") || !tag(bytes, 8, "WAVE")) {
			return Optional.empty();
		}
		ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		int pos = 12;
		Integer format = null;
		int channels = 0;
		int sampleRate = 0;
		int bits = 0;
		for (int i = 0; i < MAX_CHUNKS && pos + 8 <= bytes.length; i++) {
			long size = Integer.toUnsignedLong(buf.getInt(pos + 4));
			int body = pos + 8;
			if (tag(bytes, pos, "fmt ")) {
				if (size < 16 || body + 16 > bytes.length) {
					return Optional.empty();
				}
				format = Short.toUnsignedInt(buf.getShort(body));
				channels = Short.toUnsignedInt(buf.getShort(body + 2));
				sampleRate = buf.getInt(body + 4);
				bits = Short.toUnsignedInt(buf.getShort(body + 14));
			}
			else if (tag(bytes, pos, "data")) {
				if (format == null) {
					return Optional.empty();
				}
				long available = bytes.length - (long) body;
				return Optional.of(new WavFormat(format, channels, sampleRate, bits, Math.min(size, available)));
			}
			long next = body + size + (size & 1); // chunks are word-aligned
			if (next > bytes.length) {
				return Optional.empty();
			}
			pos = (int) next;
		}
		return Optional.empty();
	}

	public boolean isPcm16Mono(int expectedSampleRate) {
		return audioFormat == 1 && channels == 1 && bitsPerSample == 16 && sampleRate == expectedSampleRate;
	}

	public double durationSeconds() {
		int bytesPerSecond = sampleRate * channels * (bitsPerSample / 8);
		return bytesPerSecond <= 0 ? 0 : (double) dataBytes / bytesPerSecond;
	}

	private static boolean tag(byte[] bytes, int offset, String tag) {
		return offset + 4 <= bytes.length
				&& new String(bytes, offset, 4, StandardCharsets.US_ASCII).equals(tag);
	}
}
