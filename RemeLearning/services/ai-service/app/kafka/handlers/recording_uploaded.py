import os

from app.clients.user_service_client import user_service_client
from app.config import settings
from app.db.repository import save_face_recognition_result, save_voice_authenticity_result
from app.db.session import session_scope
from app.face.enrollment import FaceEnrollmentService
from app.face.insightface_engine import InsightFaceEngine
from app.face.pipeline import identify_speakers_by_face
from app.kafka import topics
from app.kafka.producer import event_producer
from app.schemas.events import RecordingUploadedEvent, TranscriptReadyEvent
from app.storage.s3_client import download_to_tempfile
from app.stt.audio_convert import convert_to_wav
from app.stt.diarization import DiarizationEngine
from app.stt.pipeline import transcribe_turns_multilingual
from app.stt.whisper_engine import FasterWhisperEngine
from app.vision.frame_extractor import extract_frames
from app.vision.gemini_vision_engine import GeminiVisionEngine
from app.vision.pipeline import build_caption_segments, merge_caption_segments
from app.voice_auth.heuristic_analyzer import HeuristicVoiceAuthenticityAnalyzer
from app.voice_auth.pipeline import annotate_voice_authenticity

_whisper_engine: FasterWhisperEngine | None = None
_diarization_engine: DiarizationEngine | None = None
_vision_engine: GeminiVisionEngine | None = None
_face_engine: InsightFaceEngine | None = None
_face_enrollment: FaceEnrollmentService | None = None
_voice_auth_analyzer: HeuristicVoiceAuthenticityAnalyzer | None = None


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


def _get_face_engine() -> InsightFaceEngine:
    global _face_engine
    if _face_engine is None:
        _face_engine = InsightFaceEngine()
    return _face_engine


def _get_face_enrollment() -> FaceEnrollmentService:
    global _face_enrollment
    if _face_enrollment is None:
        _face_enrollment = FaceEnrollmentService(_get_face_engine(), user_service_client)
    return _face_enrollment


def _get_voice_auth_analyzer() -> HeuristicVoiceAuthenticityAnalyzer:
    global _voice_auth_analyzer
    if _voice_auth_analyzer is None:
        _voice_auth_analyzer = HeuristicVoiceAuthenticityAnalyzer()
    return _voice_auth_analyzer


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


def _persist_speaker_identities(video_path, turns, recording_id) -> None:
    """When FACE_RECOGNITION_ENABLED=true, matches faces sampled per diarized speaker turn
    against enrolled reference photos and persists the result to the ai schema (see
    app/api/routes.py's _add_speaker_identities for the REST-response equivalent - this Kafka
    path persists only, since TranscriptReadyEvent deliberately doesn't carry these fields)."""
    known_faces = _get_face_enrollment().list_enrolled()
    matches = identify_speakers_by_face(
        video_path,
        turns,
        _get_face_engine(),
        known_faces,
        settings.face_match_similarity_threshold,
        settings.face_frames_per_turn,
    )
    with session_scope() as session:
        for speaker, match in matches.items():
            save_face_recognition_result(session, recording_id, speaker, match.user_id, match.name, match.similarity)


def _persist_voice_authenticity(wav_path, turns, recording_id) -> None:
    """When VOICE_AUTHENTICITY_ENABLED=true, runs the heuristic human-vs-synthetic voice
    classifier per diarized speaker turn and persists the result to the ai schema."""
    authenticity = annotate_voice_authenticity(wav_path, turns, _get_voice_auth_analyzer())
    with session_scope() as session:
        for entry in authenticity:
            save_voice_authenticity_result(
                session, recording_id, entry.speaker, entry.start_seconds, entry.end_seconds,
                entry.label, entry.confidence,
            )


async def handle_recording_uploaded(payload: dict) -> None:
    event = RecordingUploadedEvent.model_validate(payload)
    audio_path = download_to_tempfile(event.s3_bucket, event.s3_key)
    wav_path = convert_to_wav(audio_path)
    try:
        turns = _get_diarization_engine().diarize(wav_path)
        result = transcribe_turns_multilingual(
            wav_path,
            _get_whisper_engine(),
            turns,
            language_code=event.language_code,
            max_workers=settings.stt_max_concurrent_transcriptions,
        )
        if settings.vision_enabled:
            result = _add_vision_captions(result, audio_path)
        if settings.face_recognition_enabled:
            _persist_speaker_identities(audio_path, turns, event.recording_id)
        if settings.voice_authenticity_enabled:
            _persist_voice_authenticity(wav_path, turns, event.recording_id)
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
