from datetime import datetime, timezone

from sqlalchemy import DateTime, Float, String
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

from app.config import settings


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


class Base(DeclarativeBase):
    """Every table lives under the dedicated "ai" schema (settings.ai_db_schema), not the
    default "public" schema - keeps ai-service's tables namespaced within its own reme_ai
    database, per the schema convention this feature was asked to follow."""


class KnownFace(Base):
    """One enrolled person: a face embedding sourced from a user-service profile photo,
    used to match faces detected in uploaded recordings back to a userId."""

    __tablename__ = "known_faces"
    __table_args__ = {"schema": settings.ai_db_schema}

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    user_id: Mapped[str] = mapped_column(String(100), unique=True, nullable=False)
    name: Mapped[str] = mapped_column(String(255), nullable=False)
    # ArcFace embeddings are 512-dim float vectors; stored as a JSONB float array rather than
    # requiring the pgvector extension, which isn't assumed to be installed on the shared
    # Postgres instance. Cosine similarity is computed in Python at match time (app/face/enrollment.py).
    embedding: Mapped[list[float]] = mapped_column(JSONB, nullable=False)
    source: Mapped[str] = mapped_column(String(50), nullable=False, default="user-service-photo")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow, onupdate=_utcnow)


class FaceRecognitionResult(Base):
    """Best-match result of identify_speakers_by_face for one diarized speaker label within
    one recording - persisted so results survive beyond a single request/response."""

    __tablename__ = "face_recognition_results"
    __table_args__ = {"schema": settings.ai_db_schema}

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    recording_id: Mapped[str] = mapped_column(String(100), nullable=False, index=True)
    speaker_label: Mapped[str] = mapped_column(String(100), nullable=False)
    matched_user_id: Mapped[str | None] = mapped_column(String(100), nullable=True)
    matched_name: Mapped[str | None] = mapped_column(String(255), nullable=True)
    similarity: Mapped[float | None] = mapped_column(Float, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)


class VoiceAuthenticityResult(Base):
    """Human-vs-synthetic verdict for one diarized speaker turn within one recording, from
    the heuristic classifier in app/voice_auth/heuristic_analyzer.py."""

    __tablename__ = "voice_authenticity_results"
    __table_args__ = {"schema": settings.ai_db_schema}

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    recording_id: Mapped[str] = mapped_column(String(100), nullable=False, index=True)
    speaker_label: Mapped[str] = mapped_column(String(100), nullable=False)
    start_seconds: Mapped[float] = mapped_column(Float, nullable=False)
    end_seconds: Mapped[float] = mapped_column(Float, nullable=False)
    label: Mapped[str] = mapped_column(String(20), nullable=False)
    confidence: Mapped[float] = mapped_column(Float, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)
