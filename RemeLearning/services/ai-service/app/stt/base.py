from abc import ABC, abstractmethod
from dataclasses import dataclass


@dataclass
class RawSegment:
    text: str
    start_seconds: float
    end_seconds: float


@dataclass
class SpeakerTurn:
    speaker: str
    start_seconds: float
    end_seconds: float


@dataclass
class WordTiming:
    word: str
    start_seconds: float
    end_seconds: float
    # faster-whisper's per-word confidence (0-1); defaults to 1.0 for callers/tests that don't
    # have a real ASR confidence to report, so a low value is always a deliberate signal.
    probability: float = 1.0


class SpeechToTextEngine(ABC):
    """Transcribes an audio file into timestamped text segments (no speaker labels yet)."""

    @abstractmethod
    def transcribe(self, audio_path: str, language_code: str = "en") -> list[RawSegment]:
        raise NotImplementedError

    @abstractmethod
    def transcribe_auto(self, audio_path: str, language_code: str | None) -> tuple[list[RawSegment], str]:
        """Like transcribe, but also reports the language used (auto-detected when language_code is None)."""
        raise NotImplementedError

    @abstractmethod
    def transcribe_words(self, audio_path: str, language_code: str | None = None) -> list[WordTiming]:
        """Transcribes audio into a flat, chronologically-ordered list of word-level timestamps
        (finer-grained than transcribe's segment-level timestamps) - used to align a known script's
        sentences against the audio timeline (see app/align/sentence_aligner.py)."""
        raise NotImplementedError
