import os

from app.kafka import topics
from app.kafka.producer import event_producer
from app.schemas.events import RecordingUploadedEvent, TranscriptReadyEvent
from app.storage.s3_client import download_to_tempfile
from app.stt.diarization import DiarizationEngine
from app.stt.pipeline import build_transcription_result
from app.stt.whisper_engine import FasterWhisperEngine

_whisper_engine: FasterWhisperEngine | None = None
_diarization_engine: DiarizationEngine | None = None


def _get_whisper_engine() -> FasterWhisperEngine:
    global _whisper_engine
    if _whisper_engine is None:
        _whisper_engine = FasterWhisperEngine()
    return _whisper_engine


def _get_diarization_engine() -> DiarizationEngine:
    global _diarization_engine
    if _diarization_engine is None:
        _diarization_engine = DiarizationEngine()
    return _diarization_engine


async def handle_recording_uploaded(payload: dict) -> None:
    event = RecordingUploadedEvent.model_validate(payload)
    audio_path = download_to_tempfile(event.s3_bucket, event.s3_key)
    try:
        segments = _get_whisper_engine().transcribe(audio_path, event.language_code)
        turns = _get_diarization_engine().diarize(audio_path)
        result = build_transcription_result(segments, turns)
    finally:
        os.remove(audio_path)

    ready_event = TranscriptReadyEvent(
        recording_id=event.recording_id,
        user_id=event.user_id,
        full_text=result.full_text,
        segments=result.segments,
    )
    await event_producer.publish(topics.TRANSCRIPT_READY, key=event.recording_id, event=ready_event)
