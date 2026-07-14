import os
import tempfile

from fastapi import APIRouter, File, Form, UploadFile

from app.analysis.rule_based_analyzer import RuleBasedAnalyzer
from app.config import settings
from app.schemas.events import (
    AnalysisRequestedEvent,
    LearningGapAnalyzedEvent,
    RecordingUploadedEvent,
    TranscriptionResult,
)
from app.storage.s3_client import download_to_tempfile
from app.stt.audio_convert import convert_to_wav
from app.stt.diarization import DiarizationEngine
from app.stt.pipeline import build_transcription_result
from app.stt.whisper_engine import FasterWhisperEngine
from app.vision.frame_extractor import extract_frames
from app.vision.gemini_vision_engine import GeminiVisionEngine
from app.vision.pipeline import build_caption_segments, merge_caption_segments

router = APIRouter()

_analyzer = RuleBasedAnalyzer()
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


def _add_vision_captions(result: TranscriptionResult, video_path: str) -> TranscriptionResult:
    """When VISION_ENABLED=true, samples frames from the source video and merges Gemini's
    captions of them into the transcript as extra segments, for vocabulary/mistake analysis
    to draw on visually-shown text/objects in addition to spoken words."""
    frames = extract_frames(video_path, settings.vision_frame_interval_seconds)
    try:
        captions = _get_vision_engine().caption_frames(frames)
    finally:
        for frame in frames:
            os.remove(frame.image_path)
    return merge_caption_segments(result, build_caption_segments(captions))


@router.get("/health")
def health() -> dict:
    return {"status": "ok"}


@router.post("/api/v1/transcribe", response_model=TranscriptionResult)
def transcribe(event: RecordingUploadedEvent) -> TranscriptionResult:
    """Synchronous STT + diarization, for manual/ad-hoc calls (main pipeline uses Kafka)."""
    audio_path = download_to_tempfile(event.s3_bucket, event.s3_key)
    wav_path = convert_to_wav(audio_path)
    try:
        segments = _get_whisper_engine().transcribe(wav_path, event.language_code)
        turns = _get_diarization_engine().diarize(wav_path)
        result = build_transcription_result(segments, turns)
        if settings.vision_enabled:
            result = _add_vision_captions(result, audio_path)
        return result
    finally:
        os.remove(audio_path)
        os.remove(wav_path)


@router.post("/api/v1/upload", response_model=TranscriptionResult)
async def upload(file: UploadFile = File(...), language_code: str = Form("en")) -> TranscriptionResult:
    """Accepts a video/audio file directly (multipart/form-data) and runs STT + diarization
    synchronously. For environments without Kafka/S3 wired up yet - the Java side can call this
    directly with the raw file instead of publishing recording.uploaded."""
    suffix = os.path.splitext(file.filename or "")[1]
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
        tmp.write(await file.read())
        audio_path = tmp.name

    wav_path = convert_to_wav(audio_path)
    try:
        segments = _get_whisper_engine().transcribe(wav_path, language_code)
        turns = _get_diarization_engine().diarize(wav_path)
        result = build_transcription_result(segments, turns)
        if settings.vision_enabled:
            result = _add_vision_captions(result, audio_path)
        return result
    finally:
        os.remove(audio_path)
        os.remove(wav_path)


@router.post("/api/v1/analyze", response_model=LearningGapAnalyzedEvent)
def analyze(event: AnalysisRequestedEvent) -> LearningGapAnalyzedEvent:
    """Synchronous forgetting-pattern analysis, for manual re-runs (main pipeline uses Kafka)."""
    weak_points = _analyzer.analyze(event.segments, event.history)
    return LearningGapAnalyzedEvent(
        recording_id=event.recording_id,
        user_id=event.user_id,
        weak_points=weak_points,
    )
