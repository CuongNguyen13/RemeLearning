import json
import os
import tempfile
import uuid

from fastapi import APIRouter, File, Form, HTTPException, UploadFile

from app.align.sentence_aligner import align_sentences
from app.analysis.factory import create_analyzer
from app.clients.user_service_client import UserServiceNotFoundError, user_service_client
from app.config import settings
from app.db.repository import save_face_recognition_result, save_voice_authenticity_result
from app.db.session import session_scope
from app.face.enrollment import FaceEnrollmentService, NoFaceDetectedError
from app.face.insightface_engine import InsightFaceEngine
from app.face.pipeline import identify_speakers_by_face
from app.schemas.align import SentenceTimingResponse
from app.schemas.events import (
    AnalysisRequestedEvent,
    EnrolledFaceResponse,
    LearningGapAnalyzedEvent,
    RecordingUploadedEvent,
    SpeakerIdentity,
    TranscriptionResult,
)
from app.schemas.tts import TtsSynthesizeRequest, TtsSynthesizeResponse
from app.storage.s3_client import download_to_tempfile
from app.stt.audio_convert import convert_to_wav
from app.tts.base import TtsSynthesisRequest
from app.tts.supertonic_engine import SupertonicEngine
from app.stt.base import SpeakerTurn
from app.stt.diarization import DiarizationEngine
from app.stt.pipeline import transcribe_turns_multilingual
from app.stt.whisper_engine import FasterWhisperEngine
from app.vision.frame_extractor import extract_frames
from app.vision.gemini_vision_engine import GeminiVisionEngine
from app.vision.pipeline import build_caption_segments, merge_caption_segments
from app.voice_auth.heuristic_analyzer import HeuristicVoiceAuthenticityAnalyzer
from app.voice_auth.pipeline import annotate_voice_authenticity

router = APIRouter()

UPLOAD_CHUNK_SIZE_BYTES = 1024 * 1024  # 1 MiB

_analyzer = create_analyzer()
_whisper_engine: FasterWhisperEngine | None = None
_diarization_engine: DiarizationEngine | None = None
_vision_engine: GeminiVisionEngine | None = None
_face_engine: InsightFaceEngine | None = None
_face_enrollment: FaceEnrollmentService | None = None
_voice_auth_analyzer: HeuristicVoiceAuthenticityAnalyzer | None = None
_tts_engine: SupertonicEngine | None = None


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


def _get_tts_engine() -> SupertonicEngine:
    global _tts_engine
    if _tts_engine is None:
        _tts_engine = SupertonicEngine(settings.tts_default_voice, settings.tts_default_lang)
    return _tts_engine


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


def _add_speaker_identities(
    result: TranscriptionResult, video_path: str, turns: list[SpeakerTurn], recording_id: str
) -> TranscriptionResult:
    """When FACE_RECOGNITION_ENABLED=true, matches faces sampled during each diarized
    speaker turn's time range against enrolled reference photos (app/face/pipeline.py),
    persists the per-recording result, and attaches it to the response."""
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

    identities = [
        SpeakerIdentity(speaker=speaker, user_id=match.user_id, name=match.name, similarity=match.similarity)
        for speaker, match in matches.items()
    ]
    return result.model_copy(update={"speaker_identities": identities})


def _add_voice_authenticity(
    result: TranscriptionResult, wav_path: str, turns: list[SpeakerTurn], recording_id: str
) -> TranscriptionResult:
    """When VOICE_AUTHENTICITY_ENABLED=true, runs the heuristic human-vs-synthetic voice
    classifier (app/voice_auth/) over each diarized speaker turn, persists the per-recording
    result, and attaches it to the response."""
    authenticity = annotate_voice_authenticity(wav_path, turns, _get_voice_auth_analyzer())
    with session_scope() as session:
        for entry in authenticity:
            save_voice_authenticity_result(
                session, recording_id, entry.speaker, entry.start_seconds, entry.end_seconds,
                entry.label, entry.confidence,
            )
    return result.model_copy(update={"voice_authenticity": authenticity})


@router.get("/health")
def health() -> dict:
    return {"status": "ok"}


@router.post("/api/v1/transcribe", response_model=TranscriptionResult)
def transcribe(event: RecordingUploadedEvent) -> TranscriptionResult:
    """Synchronous STT + diarization, for manual/ad-hoc calls (main pipeline uses Kafka)."""
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
            result = _add_speaker_identities(result, audio_path, turns, event.recording_id)
        if settings.voice_authenticity_enabled:
            result = _add_voice_authenticity(result, wav_path, turns, event.recording_id)
        return result.model_copy(update={"recording_id": event.recording_id})
    finally:
        os.remove(audio_path)
        os.remove(wav_path)


@router.post("/api/v1/upload", response_model=TranscriptionResult)
async def upload(file: UploadFile = File(...), language_code: str | None = Form(None)) -> TranscriptionResult:
    """Accepts a video/audio file directly (multipart/form-data) and runs STT + diarization
    synchronously. For environments without Kafka/S3 wired up yet - the Java side can call this
    directly with the raw file instead of publishing recording.uploaded.

    Reads the upload in fixed-size chunks (UPLOAD_CHUNK_SIZE_BYTES) instead of buffering the whole
    file in memory via a single file.read() - keeps memory flat regardless of file size.

    Diarizes first, then transcribes each speaker turn concurrently. Omitting language_code (the
    default) auto-detects the language of every turn independently, so a recording with speakers
    using different languages is analyzed correctly; passing a language_code forces that language
    for every turn instead (skips per-turn detection, useful for known single-language audio).

    When FACE_RECOGNITION_ENABLED/VOICE_AUTHENTICITY_ENABLED are set, also matches faces sampled
    from the video against enrolled reference photos and runs the heuristic human-vs-synthetic
    voice check per diarized speaker turn (see app/face/ and app/voice_auth/) - results are
    persisted under a generated recording_id (returned in the response) since an ad-hoc upload
    has no recordingId of its own.
    """
    recording_id = str(uuid.uuid4())
    suffix = os.path.splitext(file.filename or "")[1]
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
        while chunk := await file.read(UPLOAD_CHUNK_SIZE_BYTES):
            tmp.write(chunk)
        audio_path = tmp.name

    wav_path = convert_to_wav(audio_path)
    try:
        turns = _get_diarization_engine().diarize(wav_path)
        result = transcribe_turns_multilingual(
            wav_path,
            _get_whisper_engine(),
            turns,
            language_code=language_code,
            max_workers=settings.stt_max_concurrent_transcriptions,
        )
        if settings.vision_enabled:
            result = _add_vision_captions(result, audio_path)
        if settings.face_recognition_enabled:
            result = _add_speaker_identities(result, audio_path, turns, recording_id)
        if settings.voice_authenticity_enabled:
            result = _add_voice_authenticity(result, wav_path, turns, recording_id)
        return result.model_copy(update={"recording_id": recording_id})
    finally:
        os.remove(audio_path)
        os.remove(wav_path)


@router.post("/api/v1/dictation/align-sentences", response_model=list[SentenceTimingResponse])
async def align_dictation_sentences(
    audio: UploadFile = File(...),
    sentences: str = Form(...),
) -> list[SentenceTimingResponse]:
    """Aligns a dictation clip's script sentences, in order, against its own audio: transcribes the
    audio with word-level timestamps (FasterWhisperEngine.transcribe_words) then matches each
    sentence's words sequentially against that timeline (app/align/sentence_aligner.py). Used by
    english-service's dictation getClipDetail the first time a clip's sentences are read without
    startMs/endMs - a sentence Whisper couldn't locate comes back with null timings rather than a
    guess, so english-service leaves it unaligned and can retry on a later read.

    `sentences` is a JSON-encoded array of strings (order matters - it's the clip's sentences in
    seq order) since multipart/form-data has no native list type; `audio` is the clip's own audio
    file, read/converted the same way /api/v1/upload does.
    """
    try:
        sentence_list = json.loads(sentences)
    except json.JSONDecodeError as exc:
        raise HTTPException(status_code=400, detail="sentences must be a JSON array of strings") from exc

    suffix = os.path.splitext(audio.filename or "")[1]
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
        while chunk := await audio.read(UPLOAD_CHUNK_SIZE_BYTES):
            tmp.write(chunk)
        audio_path = tmp.name

    wav_path = convert_to_wav(audio_path)
    try:
        words = _get_whisper_engine().transcribe_words(wav_path)
        timings = align_sentences(sentence_list, words)
        return [SentenceTimingResponse(start_ms=t.start_ms, end_ms=t.end_ms) for t in timings]
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


@router.post("/api/v1/tts/synthesize", response_model=TtsSynthesizeResponse)
def synthesize_tts(request: TtsSynthesizeRequest) -> TtsSynthesizeResponse:
    """Synthesizes text into speech with the on-device Supertonic model and returns base64-encoded
    44.1kHz WAV. Used by english-service's dictation "Luyện nghe với AI" section to voice the
    Gemini-suggested practice sentences a learner then dictates. The model loads lazily on the first
    call; returns 503 when TTS is disabled so callers can degrade instead of hanging."""
    if not settings.tts_enabled:
        raise HTTPException(status_code=503, detail="TTS is disabled (set TTS_ENABLED=true)")

    result = _get_tts_engine().synthesize(
        TtsSynthesisRequest(
            text=request.text,
            lang=request.lang,
            voice=request.voice or settings.tts_default_voice,
        )
    )
    return TtsSynthesizeResponse(
        audio_base64=result.audio_base64,
        mime_type=result.mime_type,
        sample_rate=result.sample_rate,
    )


@router.post("/api/v1/faces/enroll", response_model=EnrolledFaceResponse)
async def enroll_face(
    user_id: str = Form(...),
    name: str | None = Form(None),
    image: UploadFile | None = File(None),
) -> EnrolledFaceResponse:
    """Enrolls user_id as a known face. By default fetches the reference photo from
    user-service (GET /api/v1/users/{userId}, using its photoUrl) - the production path, so
    face recognition is sourced from user-service's stored image data as intended. Passing
    image directly enrolls from a local file instead, without needing a reachable
    user-service (useful for local testing)."""
    image_bytes = await image.read() if image is not None else None
    try:
        enrolled = _get_face_enrollment().enroll(user_id, name, image_bytes)
    except NoFaceDetectedError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except UserServiceNotFoundError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    return EnrolledFaceResponse(user_id=enrolled.user_id, name=enrolled.name)


@router.get("/api/v1/faces", response_model=list[EnrolledFaceResponse])
def list_faces() -> list[EnrolledFaceResponse]:
    """Lists every enrolled known face - lets a caller verify enrollment before testing
    /api/v1/upload's face-recognition output."""
    return [
        EnrolledFaceResponse(user_id=face.user_id, name=face.name)
        for face in _get_face_enrollment().list_enrolled()
    ]
