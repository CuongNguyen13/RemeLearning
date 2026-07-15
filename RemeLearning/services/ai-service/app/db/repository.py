from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db.models import FaceRecognitionResult, KnownFace, VoiceAuthenticityResult


def upsert_known_face(session: Session, user_id: str, name: str, embedding: list[float], source: str) -> KnownFace:
    """Inserts a new enrolled face, or refreshes an existing one's name/embedding/source when
    the same userId is re-enrolled (e.g. after the user updates their user-service photo)."""
    existing = session.scalar(select(KnownFace).where(KnownFace.user_id == user_id))
    if existing is None:
        existing = KnownFace(user_id=user_id, name=name, embedding=embedding, source=source)
        session.add(existing)
    else:
        existing.name = name
        existing.embedding = embedding
        existing.source = source
    session.flush()
    return existing


def list_known_faces(session: Session) -> list[KnownFace]:
    return list(session.scalars(select(KnownFace)))


def save_face_recognition_result(
    session: Session,
    recording_id: str,
    speaker_label: str,
    matched_user_id: str | None,
    matched_name: str | None,
    similarity: float | None,
) -> None:
    session.add(
        FaceRecognitionResult(
            recording_id=recording_id,
            speaker_label=speaker_label,
            matched_user_id=matched_user_id,
            matched_name=matched_name,
            similarity=similarity,
        )
    )


def save_voice_authenticity_result(
    session: Session,
    recording_id: str,
    speaker_label: str,
    start_seconds: float,
    end_seconds: float,
    label: str,
    confidence: float,
) -> None:
    session.add(
        VoiceAuthenticityResult(
            recording_id=recording_id,
            speaker_label=speaker_label,
            start_seconds=start_seconds,
            end_seconds=end_seconds,
            label=label,
            confidence=confidence,
        )
    )
