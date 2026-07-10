import os
import tempfile

from fastapi import APIRouter, File, Form, UploadFile

from app.analysis.rule_based_analyzer import RuleBasedAnalyzer
from app.schemas.events import (
    AnalysisRequestedEvent,
    LearningGapAnalyzedEvent,
    RecordingUploadedEvent,
    TranscriptionResult,
)
from app.storage.s3_client import download_to_tempfile
from app.stt.diarization import DiarizationEngine
from app.stt.pipeline import build_transcription_result
from app.stt.whisper_engine import FasterWhisperEngine

router = APIRouter()

_analyzer = RuleBasedAnalyzer()
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


@router.get("/health")
def health() -> dict:
    return {"status": "ok"}


@router.post("/api/v1/transcribe", response_model=TranscriptionResult)
def transcribe(event: RecordingUploadedEvent) -> TranscriptionResult:
    """Synchronous STT + diarization, for manual/ad-hoc calls (main pipeline uses Kafka)."""
    audio_path = download_to_tempfile(event.s3_bucket, event.s3_key)
    try:
        segments = _get_whisper_engine().transcribe(audio_path, event.language_code)
        turns = _get_diarization_engine().diarize(audio_path)
        return build_transcription_result(segments, turns)
    finally:
        os.remove(audio_path)


@router.post("/api/v1/upload", response_model=TranscriptionResult)
async def upload(file: UploadFile = File(...), language_code: str = Form("en")) -> TranscriptionResult:
    """Accepts a video/audio file directly (multipart/form-data) and runs STT + diarization
    synchronously. For environments without Kafka/S3 wired up yet - the Java side can call this
    directly with the raw file instead of publishing recording.uploaded."""
    suffix = os.path.splitext(file.filename or "")[1]
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
        tmp.write(await file.read())
        audio_path = tmp.name

    try:
        segments = _get_whisper_engine().transcribe(audio_path, language_code)
        turns = _get_diarization_engine().diarize(audio_path)
        return build_transcription_result(segments, turns)
    finally:
        os.remove(audio_path)


@router.post("/api/v1/analyze", response_model=LearningGapAnalyzedEvent)
def analyze(event: AnalysisRequestedEvent) -> LearningGapAnalyzedEvent:
    """Synchronous forgetting-pattern analysis, for manual re-runs (main pipeline uses Kafka)."""
    weak_points = _analyzer.analyze(event.segments, event.history)
    return LearningGapAnalyzedEvent(
        recording_id=event.recording_id,
        user_id=event.user_id,
        weak_points=weak_points,
    )
