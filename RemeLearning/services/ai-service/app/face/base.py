from abc import ABC, abstractmethod
from dataclasses import dataclass


@dataclass
class DetectedFace:
    """One face detected in a single video frame, with its ArcFace embedding."""

    embedding: list[float]
    timestamp_seconds: float
    detection_confidence: float


@dataclass
class EnrolledFace:
    """A known person's reference embedding, sourced from their user-service photo."""

    user_id: str
    name: str
    embedding: list[float]


@dataclass
class FaceMatch:
    user_id: str
    name: str
    similarity: float


class FaceRecognitionEngine(ABC):
    """Detects faces in an image and returns their embeddings. Swappable so a different
    detector/embedding model can replace InsightFaceEngine later without touching callers -
    same seam as app/vision/base.py's VisionCaptionEngine."""

    @abstractmethod
    def detect_faces(self, image_path: str) -> list[DetectedFace]:
        raise NotImplementedError
