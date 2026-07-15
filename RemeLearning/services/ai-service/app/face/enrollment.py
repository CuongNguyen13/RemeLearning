import tempfile

import numpy as np

from app.clients.user_service_client import UserServiceClient
from app.db.repository import list_known_faces, upsert_known_face
from app.db.session import session_scope
from app.face.base import DetectedFace, EnrolledFace, FaceMatch, FaceRecognitionEngine


class NoFaceDetectedError(Exception):
    pass


def cosine_similarity(a: list[float], b: list[float]) -> float:
    """Both insightface embeddings are already L2-normalized (normed_embedding), so the dot
    product alone equals cosine similarity - but this normalizes explicitly rather than
    relying on that, so it stays correct for any embedding source."""
    vec_a, vec_b = np.array(a), np.array(b)
    denom = np.linalg.norm(vec_a) * np.linalg.norm(vec_b)
    if denom == 0.0:
        return 0.0
    return float(np.dot(vec_a, vec_b) / denom)


def best_match(embedding: list[float], known_faces: list[EnrolledFace], threshold: float) -> FaceMatch | None:
    """Compares one detected face's embedding against every enrolled face and returns the
    closest match, or None when nothing clears the similarity threshold - i.e. the face
    belongs to someone who isn't enrolled."""
    best: FaceMatch | None = None
    for known in known_faces:
        similarity = cosine_similarity(embedding, known.embedding)
        if similarity >= threshold and (best is None or similarity > best.similarity):
            best = FaceMatch(user_id=known.user_id, name=known.name, similarity=similarity)
    return best


class FaceEnrollmentService:
    """Enrolls a user's reference photo (fetched from user-service, or passed directly for
    local testing) as a known face, and serves the enrolled-faces list for matching. Backed
    by the ai schema's known_faces table (app/db/models.py) rather than an in-process cache,
    so enrollment survives process restarts."""

    def __init__(self, engine: FaceRecognitionEngine, user_service_client: UserServiceClient) -> None:
        self._engine = engine
        self._user_service_client = user_service_client

    def enroll(self, user_id: str, name: str | None = None, image_bytes: bytes | None = None) -> EnrolledFace:
        """Embeds a reference photo and upserts it as a known face. When image_bytes isn't
        given, fetches the photo from user-service by userId (the primary, production path);
        image_bytes lets a caller enroll directly from a local file for testing without a
        reachable user-service."""
        fetched_from_user_service = image_bytes is None
        if fetched_from_user_service:
            profile = self._user_service_client.get_user(user_id)
            if not profile.photo_url:
                raise NoFaceDetectedError(f"user-service has no photo on file for userId={user_id}")
            image_bytes = self._user_service_client.fetch_photo_bytes(profile.photo_url)
            name = name or profile.name

        with tempfile.NamedTemporaryFile(delete=False, suffix=".jpg") as tmp:
            tmp.write(image_bytes)
            image_path = tmp.name

        faces = self._engine.detect_faces(image_path)
        if not faces:
            raise NoFaceDetectedError(f"No face detected in the reference photo for userId={user_id}")

        # Reference photos are enrolled one at a time, so a random second face in frame (e.g.
        # someone in the background) shouldn't win over the intended subject - keep the one
        # detection with the highest detector confidence.
        primary_face = max(faces, key=lambda face: face.detection_confidence)

        with session_scope() as session:
            row = upsert_known_face(
                session,
                user_id=user_id,
                name=name or user_id,
                embedding=primary_face.embedding,
                source="user-service-photo" if fetched_from_user_service else "local-upload",
            )
            return EnrolledFace(user_id=row.user_id, name=row.name, embedding=row.embedding)

    def list_enrolled(self) -> list[EnrolledFace]:
        with session_scope() as session:
            return [
                EnrolledFace(user_id=row.user_id, name=row.name, embedding=row.embedding)
                for row in list_known_faces(session)
            ]
