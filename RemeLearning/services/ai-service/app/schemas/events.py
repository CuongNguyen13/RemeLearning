from pydantic import BaseModel


class Segment(BaseModel):
    speaker: str
    text: str
    start_seconds: float
    end_seconds: float
    language: str = "en"


class SpeakerIdentity(BaseModel):
    """Best-guess identity for one diarized speaker label, from app/face/pipeline.py matching
    faces sampled during that speaker's turns against enrolled reference photos."""

    speaker: str
    user_id: str
    name: str
    similarity: float


class SegmentAuthenticity(BaseModel):
    """Heuristic human-vs-synthetic verdict for one diarized speaker turn, from
    app/voice_auth/heuristic_analyzer.py. Not a trained model - see that module's docstring."""

    speaker: str
    start_seconds: float
    end_seconds: float
    label: str  # "human" | "synthetic" | "uncertain"
    confidence: float


class TranscriptionResult(BaseModel):
    full_text: str
    segments: list[Segment]
    # Correlates this response with the ai schema's face_recognition_results/
    # voice_authenticity_results rows - generated when the caller doesn't already have a
    # recordingId (e.g. the ad-hoc /api/v1/upload path).
    recording_id: str | None = None
    # Populated only when settings.face_recognition_enabled / voice_authenticity_enabled are
    # true; empty otherwise. Deliberately kept off the Kafka transcript.ready contract (see
    # docs/flow/ai-service-data-flow.md) so english-service's consumers are unaffected -
    # these are ai-service-local, REST-response-only enrichments for now.
    speaker_identities: list[SpeakerIdentity] = []
    voice_authenticity: list[SegmentAuthenticity] = []


class EnrolledFaceResponse(BaseModel):
    """Public view of an enrolled known face - never includes the raw embedding vector."""

    user_id: str
    name: str


class RecordingUploadedEvent(BaseModel):
    recording_id: str
    user_id: str
    s3_bucket: str
    s3_key: str
    # None means "auto-detect per speaker turn" (multi-language recordings); a fixed
    # code forces that language for every turn, matching the old single-language behavior.
    language_code: str | None = "en"


class TranscriptReadyEvent(BaseModel):
    recording_id: str
    user_id: str
    full_text: str
    segments: list[Segment]


class MistakeHistoryItem(BaseModel):
    """One recurring mistake item as tracked by backend services (grammar/vocabulary/pronunciation).

    The scoring-state fields below (half_life_days .. incorrect_count) let the ScoringEngineAnalyzer
    reproduce english-service's Java composite score. They're optional: when a producer doesn't send
    them, the cold-start defaults here match english-service's WeakPointScoringOrchestratorImpl."""

    item_id: str
    category: str  # e.g. "grammar", "vocabulary", "pronunciation"
    label: str  # e.g. "past perfect tense", "word: reluctant"
    occurrence_count: int
    last_seen_days_ago: float
    # Per-item scoring state (from english-service's mistake_history), cold-start defaults otherwise.
    half_life_days: float = 7.0
    ease_factor: float = 2.5
    mastery: float = 0.3
    leitner_box: int = 1
    # Population-level (cross-learner) difficulty counts, from item_difficulty_stats.
    correct_count: int = 0
    incorrect_count: int = 0


class AnalysisRequestedEvent(BaseModel):
    recording_id: str
    user_id: str
    segments: list[Segment]
    history: list[MistakeHistoryItem]


class WeakPoint(BaseModel):
    item_id: str
    category: str
    label: str
    forgetting_score: float
    recommendation: str


class LearningGapAnalyzedEvent(BaseModel):
    recording_id: str
    user_id: str
    weak_points: list[WeakPoint]
