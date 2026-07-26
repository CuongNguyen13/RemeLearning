package com.remelearning.common.ai.audio;

import java.io.InputStream;

/**
 * Vendor-neutral contract for transcoding audio into the compressed Opus format before it is
 * persisted to storage. Concrete implementations live outside {@code common} (e.g. a REST call to
 * the Python ai-service, which already depends on PyAV/FFmpeg for audio decoding).
 */
public interface AudioTranscodeClient {

	/** Transcodes {@code audio} (any container/codec the backing engine can decode) into an Ogg/Opus clip. */
	byte[] toOpus(InputStream audio, String filename);
}
