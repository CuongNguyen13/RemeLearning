from pyannote.audio import Pipeline

from app.config import settings
from app.stt.base import SpeakerTurn


class DiarizationEngine:
    """Local speaker diarization using pyannote.audio. Requires HF_TOKEN for model weights."""

    def __init__(self) -> None:
        self._pipeline = Pipeline.from_pretrained(
            "pyannote/speaker-diarization-3.1",
            use_auth_token=settings.hf_token or None,
        )
        if settings.device != "cpu":
            import torch

            self._pipeline.to(torch.device(settings.device))

    def diarize(self, audio_path: str) -> list[SpeakerTurn]:
        annotation = self._pipeline(audio_path)
        return [
            SpeakerTurn(speaker=speaker, start_seconds=turn.start, end_seconds=turn.end)
            for turn, _track, speaker in annotation.itertracks(yield_label=True)
        ]
