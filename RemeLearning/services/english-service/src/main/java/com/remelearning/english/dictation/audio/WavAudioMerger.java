package com.remelearning.english.dictation.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Concatenates multiple RIFF/WAVE PCM clips - one per dialogue line, each individually
 * synthesized - into a single continuous WAV file. Reuses the first clip's {@code "fmt "} chunk
 * verbatim (every clip comes from the same TTS engine/model, so their format is identical) and
 * sums their {@code "data"} chunks.
 */
public final class WavAudioMerger {

	private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
	private static final byte[] WAVE = {'W', 'A', 'V', 'E'};
	private static final byte[] FMT = {'f', 'm', 't', ' '};
	private static final byte[] DATA = {'d', 'a', 't', 'a'};
	private static final int RIFF_HEADER_SIZE = 12;

	private WavAudioMerger() {
	}

	// Merges one or more WAV clips into a single WAV file, in given order; a single clip is
	// returned unchanged since there is nothing to concatenate.
	public static byte[] merge(List<byte[]> wavClips) {
		if (wavClips == null || wavClips.isEmpty()) {
			throw new IllegalArgumentException("No audio clips to merge");
		}
		if (wavClips.size() == 1) {
			return wavClips.get(0);
		}

		byte[] fmtChunk = findChunk(wavClips.get(0), FMT);
		List<byte[]> dataChunks = new ArrayList<>();
		int totalDataLength = 0;
		for (byte[] clip : wavClips) {
			byte[] data = findChunk(clip, DATA);
			dataChunks.add(data);
			totalDataLength += data.length;
		}

		int riffSize = 4 + (8 + fmtChunk.length) + (8 + totalDataLength);
		ByteBuffer buffer = ByteBuffer.allocate(8 + riffSize).order(ByteOrder.LITTLE_ENDIAN);
		buffer.put(RIFF).putInt(riffSize).put(WAVE);
		buffer.put(FMT).putInt(fmtChunk.length).put(fmtChunk);
		buffer.put(DATA).putInt(totalDataLength);
		dataChunks.forEach(buffer::put);
		return buffer.array();
	}

	// Scans a WAV file's chunk list for the given 4-byte chunk id and returns its raw data bytes;
	// throws if the clip is malformed or doesn't contain that chunk.
	private static byte[] findChunk(byte[] wav, byte[] chunkId) {
		ByteBuffer buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
		buffer.position(RIFF_HEADER_SIZE);
		while (buffer.remaining() >= 8) {
			byte[] id = new byte[4];
			buffer.get(id);
			int size = buffer.getInt();
			int dataStart = buffer.position();
			if (Arrays.equals(id, chunkId)) {
				byte[] data = new byte[size];
				buffer.get(data);
				return data;
			}
			// Chunks are padded to an even byte count; skip the pad byte too when present.
			buffer.position(dataStart + size + (size % 2));
		}
		throw new IllegalStateException("WAV chunk not found: " + new String(chunkId));
	}
}
