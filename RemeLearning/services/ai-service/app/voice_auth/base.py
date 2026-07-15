from abc import ABC, abstractmethod
from dataclasses import dataclass, field

HUMAN = "human"
SYNTHETIC = "synthetic"
UNCERTAIN = "uncertain"


@dataclass
class VoiceAuthenticityResult:
    label: str  # HUMAN | SYNTHETIC | UNCERTAIN
    confidence: float
    features: dict[str, float] = field(default_factory=dict)


class VoiceAuthenticityAnalyzer(ABC):
    """Classifies one audio clip as a real human voice vs. a machine/synthetic voice (e.g. a
    TTS bot or recorded playback in a Teams/Meet call). Swappable so a trained
    anti-spoofing/deepfake-detection model can replace HeuristicVoiceAuthenticityAnalyzer
    later without touching callers - same seam as app/analysis/base.py's MistakeAnalyzer."""

    @abstractmethod
    def analyze(self, wav_path: str) -> VoiceAuthenticityResult:
        raise NotImplementedError
