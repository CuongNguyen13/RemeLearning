import os

from app.config import settings
from app.kafka import topics
from app.kafka.producer import event_producer
from app.schemas.events import RecordingUploadedEvent, TranscriptReadyEvent
from app.storage.s3_client import download_to_tempfile
from app.stt.audio_convert import convert_to_wav
from app.stt.diarization import DiarizationEngine
from app.stt.pipeline import build_transcription_result
from app.stt.whisper_engine import FasterWhisperEngine
from app.vision.frame_extractor import extract_frames
from app.vision.gemini_vision_engine import GeminiVisionEngine
from app.vision.pipeline import build_caption_segments, merge_caption_segments

_whisper_engine: FasterWhisperEngine | None = None
_diarization_engine: DiarizationEngine | None = None
_vision_engine: GeminiVisionEngine | None = None


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


def _get_vision_engine() -> GeminiVisionEngine:
    global _vision_engine
    if _vision_engine is None:
        _vision_engine = GeminiVisionEngine()
    return _vision_engine


def _add_vision_captions(result, video_path):
    """When VISION_ENABLED=true, samples frames from the source video and merges Gemini's
    captions of them into the transcript as extra segments, so recurring-vocabulary analysis
    can also draw on visually-shown text/objects, not just spoken words."""
    frames = extract_frames(video_path, settings.vision_frame_interval_seconds)
    try:
        captions = _get_vision_engine().caption_frames(frames)
    finally:
        for frame in frames:
            os.remove(frame.image_path)
    return merge_caption_segments(result, build_caption_segments(captions))


async def handle_recording_uploaded(payload: dict) -> None:
    event = RecordingUploadedEvent.model_validate(payload)
    audio_path = download_to_tempfile(event.s3_bucket, event.s3_key)
    wav_path = convert_to_wav(audio_path)
    try:
        segments = _get_whisper_engine().transcribe(wav_path, event.language_code)
        turns = _get_diarization_engine().diarize(wav_path)
        result = build_transcription_result(segments, turns)
        if settings.vision_enabled:
            result = _add_vision_captions(result, audio_path)
    finally:
        os.remove(audio_path)
        os.remove(wav_path)

    ready_event = TranscriptReadyEvent(
        recording_id=event.recording_id,
        user_id=event.user_id,
        full_text=result.full_text,
        segments=result.segments,
    )
    await event_producer.publish(topics.TRANSCRIPT_READY, key=event.recording_id, event=ready_event)
