import os

from app.schemas.events import SegmentAuthenticity
from app.stt.audio_convert import slice_wav
from app.stt.base import SpeakerTurn
from app.voice_auth.base import VoiceAuthenticityAnalyzer


def annotate_voice_authenticity(
    wav_path: str, turns: list[SpeakerTurn], analyzer: VoiceAuthenticityAnalyzer
) -> list[SegmentAuthenticity]:
    """Runs the voice-authenticity analyzer over each diarized speaker turn's own audio
    slice, reusing app/stt/audio_convert.py's slice_wav - the same per-turn slicing
    app/stt/pipeline.py already does for transcription."""
    results = []
    for turn in turns:
        turn_wav = slice_wav(wav_path, turn.start_seconds, turn.end_seconds)
        try:
            verdict = analyzer.analyze(turn_wav)
        finally:
            os.remove(turn_wav)

        results.append(
            SegmentAuthenticity(
                speaker=turn.speaker,
                start_seconds=turn.start_seconds,
                end_seconds=turn.end_seconds,
                label=verdict.label,
                confidence=verdict.confidence,
            )
        )
    return results
