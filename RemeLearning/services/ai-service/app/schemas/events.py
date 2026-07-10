from pydantic import BaseModel


class Segment(BaseModel):
    speaker: str
    text: str
    start_seconds: float
    end_seconds: float


class TranscriptionResult(BaseModel):
    full_text: str
    segments: list[Segment]


class RecordingUploadedEvent(BaseModel):
    recording_id: str
    user_id: str
    s3_bucket: str
    s3_key: str
    language_code: str = "en"


class TranscriptReadyEvent(BaseModel):
    recording_id: str
    user_id: str
    full_text: str
    segments: list[Segment]


class MistakeHistoryItem(BaseModel):
    """One recurring mistake item as tracked by backend services (grammar/vocabulary/pronunciation)."""

    item_id: str
    category: str  # e.g. "grammar", "vocabulary", "pronunciation"
    label: str  # e.g. "past perfect tense", "word: reluctant"
    occurrence_count: int
    last_seen_days_ago: float


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
