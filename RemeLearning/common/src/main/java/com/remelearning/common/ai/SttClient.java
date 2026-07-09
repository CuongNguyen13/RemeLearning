package com.remelearning.common.ai;

import java.io.InputStream;

public interface SttClient {

	TranscriptionResult transcribe(InputStream audio, String languageCode);
}
