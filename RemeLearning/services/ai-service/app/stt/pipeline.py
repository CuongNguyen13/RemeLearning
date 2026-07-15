import os
from concurrent.futures import ThreadPoolExecutor

from app.schemas.events import Segment, TranscriptionResult
from app.stt.audio_convert import slice_wav, wav_duration_seconds
from app.stt.base import SpeakerTurn, SpeechToTextEngine

UNKNOWN_SPEAKER = "unknown"


def _transcribe_turn(
    wav_path: str,
    whisper_engine: SpeechToTextEngine,
    turn: SpeakerTurn,
    language_code: str | None,
) -> list[Segment]:
    """Slices one diarized speaker turn out of the full recording and transcribes just that
    clip, auto-detecting its language when language_code is None. Runs off the main thread so
    multiple turns can be transcribed concurrently (see transcribe_turns_multilingual)."""
    turn_wav = slice_wav(wav_path, turn.start_seconds, turn.end_seconds)
    try:
        raw_segments, detected_language = whisper_engine.transcribe_auto(turn_wav, language_code)
    finally:
        os.remove(turn_wav)

    return [
        Segment(
            speaker=turn.speaker,
            text=raw.text,
            start_seconds=turn.start_seconds + raw.start_seconds,
            end_seconds=turn.start_seconds + raw.end_seconds,
            language=detected_language,
        )
        for raw in raw_segments
    ]


def transcribe_turns_multilingual(
    wav_path: str,
    whisper_engine: SpeechToTextEngine,
    turns: list[SpeakerTurn],
    language_code: str | None = None,
    max_workers: int = 4,
) -> TranscriptionResult:
    """Transcribes a diarized recording turn-by-turn, concurrently, so speakers using different
    languages in the same recording are each decoded in their own language instead of one
    language being forced across the whole file.

    Falls back to treating the whole file as a single turn when diarization finds no speech.
    """
    effective_turns = turns or [
        SpeakerTurn(speaker=UNKNOWN_SPEAKER, start_seconds=0.0, end_seconds=wav_duration_seconds(wav_path))
    ]

    with ThreadPoolExecutor(max_workers=max_workers) as pool:
        per_turn_segments = pool.map(
            lambda turn: _transcribe_turn(wav_path, whisper_engine, turn, language_code),
            effective_turns,
        )
        segments = [segment for turn_segments in per_turn_segments for segment in turn_segments]

    segments.sort(key=lambda seg: seg.start_seconds)
    full_text = " ".join(seg.text for seg in segments)
    return TranscriptionResult(full_text=full_text, segments=segments)
