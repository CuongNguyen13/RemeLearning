from faster_whisper import WhisperModel

from app.config import settings
from app.stt.base import RawSegment, SpeechToTextEngine


class FasterWhisperEngine(SpeechToTextEngine):
    """Local, open-source STT using faster-whisper (CTranslate2). No per-request cost."""

    def __init__(self) -> None:
        compute_type = "int8" if settings.device == "cpu" else "float16"
        self._model = WhisperModel(
            settings.whisper_model_size,
            device=settings.device,
            compute_type=compute_type,
        )

    def transcribe(self, audio_path: str, language_code: str = "en") -> list[RawSegment]:
        segments, _info = self._model.transcribe(audio_path, language=language_code)
        return [
            RawSegment(text=segment.text.strip(), start_seconds=segment.start, end_seconds=segment.end)
            for segment in segments
        ]

    def transcribe_auto(self, audio_path: str, language_code: str | None) -> tuple[list[RawSegment], str]:
        """Transcribes a single clip and reports which language was used.

        Passing None lets faster-whisper auto-detect the language from the audio itself
        (`info.language`), instead of forcing a language across the whole clip - used to
        resolve each diarized speaker turn's language independently.
        """
        segments, info = self._model.transcribe(audio_path, language=language_code)
        raw_segments = [
            RawSegment(text=segment.text.strip(), start_seconds=segment.start, end_seconds=segment.end)
            for segment in segments
        ]
        detected_language = language_code or info.language
        return raw_segments, detected_language
