package com.remelearning.english.dictation.audio;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WavAudioMergerTest {

	@Test
	void returnsSingleClipUnchanged() {
		byte[] clip = buildWav(new byte[] { 1, 2, 3 });

		assertThat(WavAudioMerger.merge(List.of(clip))).isSameAs(clip);
	}

	@Test
	void concatenatesDataChunksFromMultipleClipsUnderOneHeader() {
		byte[] first = buildWav(new byte[] { 1, 2 });
		byte[] second = buildWav(new byte[] { 3, 4, 5 });

		byte[] merged = WavAudioMerger.merge(List.of(first, second));

		// 12-byte RIFF/WAVE header + 8-byte "fmt " id/size + 16-byte fmt payload
		// + 8-byte "data" id/size + 5 bytes of concatenated PCM data.
		assertThat(merged).hasSize(12 + 8 + 16 + 8 + 5);
		byte[] mergedData = extractDataChunk(merged);
		assertThat(mergedData).containsExactly(1, 2, 3, 4, 5);
	}

	@Test
	void throwsWhenGivenNoClips() {
		assertThatThrownBy(() -> WavAudioMerger.merge(List.of())).isInstanceOf(IllegalArgumentException.class);
	}

	// Builds a minimal canonical WAV: 44-byte header (RIFF/WAVE + 16-byte "fmt " + "data" chunk id/size)
	// followed by the given PCM payload; fmt content itself is irrelevant to these tests.
	private static byte[] buildWav(byte[] pcmData) {
		ByteBuffer buffer = ByteBuffer.allocate(44 + pcmData.length).order(ByteOrder.LITTLE_ENDIAN);
		buffer.put("RIFF".getBytes()).putInt(36 + pcmData.length).put("WAVE".getBytes());
		buffer.put("fmt ".getBytes()).putInt(16).put(new byte[16]);
		buffer.put("data".getBytes()).putInt(pcmData.length).put(pcmData);
		return buffer.array();
	}

	// The merged output has the same 44-byte canonical header shape as buildWav's single-clip
	// output (one "fmt " chunk + one "data" chunk), so the concatenated PCM payload starts right
	// after it, same as in any individual clip.
	private static byte[] extractDataChunk(byte[] wav) {
		ByteBuffer buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
		buffer.position(44);
		byte[] data = new byte[wav.length - 44];
		buffer.get(data);
		return data;
	}
}
