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


class SpeechToTextEngine(ABC):
    """Transcribes an audio file into timestamped text segments (no speaker labels yet)."""

    @abstractmethod
    def transcribe(self, audio_path: str, language_code: str = "en") -> list[RawSegment]:
        raise NotImplementedError
