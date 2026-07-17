"""Supertonic on-device (ONNX, CPU) text-to-speech engine wrapper.

The heavy `supertonic` import and model load happen lazily on first synthesize() so importing this
module - and running the pure-logic tests in app/tts/base.py - stays cheap and ML-free (same pattern
as app/stt/whisper_engine.py). Supertonic is a 99M-param model that runs on CPU and outputs 44.1kHz
WAV; no GPU is required."""

import base64
import os
import tempfile

from app.tts.base import (
    DEFAULT_SAMPLE_RATE_FALLBACK,
    TtsSynthesisRequest,
    TtsSynthesisResult,
    resolve_lang,
    resolve_voice,
)


class SupertonicEngine:
    """Lazy wrapper around `supertonic.TTS`. One instance is reused across requests so the model is
    loaded once."""

    def __init__(self, default_voice: str, default_lang: str):
        self._default_voice = default_voice
        self._default_lang = default_lang
        self._tts = None

    def _model(self):
        # Loads the Supertonic model (downloading weights on first run) the first time it's needed.
        if self._tts is None:
            from supertonic import TTS

            self._tts = TTS(auto_download=True)
        return self._tts

    def _voice_style(self, model, voice: str):
        # Resolves a preset voice id (e.g. "F1") to the style object Supertonic's synthesize expects.
        # Tries the known accessors defensively and returns None (model default) if none are present,
        # so a minor API-name change across supertonic versions degrades gracefully.
        for accessor in ("get_voice_style", "load_voice_style", "voice_style"):
            method = getattr(model, accessor, None)
            if callable(method):
                return method(voice)
        presets = getattr(model, "voices", None) or getattr(model, "presets", None)
        if isinstance(presets, dict):
            return presets.get(voice)
        return None

    def synthesize(self, request: TtsSynthesisRequest) -> TtsSynthesisResult:
        # Synthesizes text into a 44.1kHz WAV and base64-encodes it. Voice/lang are normalized to
        # valid Supertonic values first. Audio is written to a temp file via the model's own
        # save_audio, then read back as bytes, avoiding an extra soundfile dependency here.
        model = self._model()
        voice = resolve_voice(request.voice or self._default_voice)
        lang = resolve_lang(request.lang or self._default_lang)

        style = self._voice_style(model, voice)
        wav, _duration = model.synthesize(text=request.text, lang=lang, voice_style=style)

        with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
            out_path = tmp.name
        try:
            model.save_audio(wav, out_path)
            with open(out_path, "rb") as handle:
                audio_bytes = handle.read()
        finally:
            os.remove(out_path)

        sample_rate = int(getattr(model, "sample_rate", DEFAULT_SAMPLE_RATE_FALLBACK))
        return TtsSynthesisResult(
            audio_base64=base64.b64encode(audio_bytes).decode("ascii"),
            mime_type="audio/wav",
            sample_rate=sample_rate,
        )
