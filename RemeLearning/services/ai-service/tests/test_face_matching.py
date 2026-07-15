import pytest

import app.face.pipeline as pipeline_module
from app.face.base import DetectedFace, EnrolledFace, FaceRecognitionEngine
from app.face.enrollment import best_match, cosine_similarity
from app.face.pipeline import identify_speakers_by_face
from app.stt.base import SpeakerTurn
from app.vision.base import FrameSample


def test_cosine_similarity_identical_vectors_is_one():
    assert cosine_similarity([1.0, 0.0], [1.0, 0.0]) == pytest.approx(1.0)


def test_cosine_similarity_orthogonal_vectors_is_zero():
    assert cosine_similarity([1.0, 0.0], [0.0, 1.0]) == pytest.approx(0.0)


def test_best_match_returns_closest_enrolled_face_above_threshold():
    known = [
        EnrolledFace(user_id="u-cuong", name="Cuong", embedding=[1.0, 0.0]),
        EnrolledFace(user_id="u-trong", name="Trong", embedding=[0.0, 1.0]),
    ]

    match = best_match([0.9, 0.1], known, threshold=0.5)

    assert match.user_id == "u-cuong"


def test_best_match_returns_none_when_nothing_clears_threshold():
    known = [EnrolledFace(user_id="u-cuong", name="Cuong", embedding=[1.0, 0.0])]

    assert best_match([0.0, 1.0], known, threshold=0.5) is None


class FakeFaceRecognitionEngine(FaceRecognitionEngine):
    """Test double keyed by call order - each sampled frame returns the next embedding in
    the list, mirroring FakeWhisperEngine's role in tests/test_pipeline.py."""

    def __init__(self, embeddings_in_call_order: list[list[float] | None]):
        self._embeddings = embeddings_in_call_order
        self._call_count = 0

    def detect_faces(self, image_path: str) -> list[DetectedFace]:
        embedding = self._embeddings[self._call_count]
        self._call_count += 1
        if embedding is None:
            return []
        return [DetectedFace(embedding=embedding, timestamp_seconds=0.0, detection_confidence=0.9)]


def _fake_frames(tmp_path, start_seconds: float, count: int) -> list[FrameSample]:
    frames = []
    for i in range(count):
        path = tmp_path / f"frame_{start_seconds}_{i}.jpg"
        path.write_bytes(b"fake-jpeg-bytes")
        frames.append(FrameSample(image_path=str(path), timestamp_seconds=start_seconds))
    return frames


def test_identify_speakers_by_face_picks_majority_match_per_speaker(tmp_path, monkeypatch):
    monkeypatch.setattr(
        pipeline_module, "extract_frames_in_range", lambda video_path, start, end, max_frames: _fake_frames(tmp_path, start, max_frames)
    )

    known = [
        EnrolledFace(user_id="u-cuong", name="Cuong", embedding=[1.0, 0.0]),
        EnrolledFace(user_id="u-trong", name="Trong", embedding=[0.0, 1.0]),
    ]
    # Speaker 0's 3 sampled frames mostly match Cuong; speaker 1's frames all match Trong.
    engine = FakeFaceRecognitionEngine([[1.0, 0.0], [1.0, 0.0], [0.0, 1.0], [0.0, 1.0], [0.0, 1.0], [0.0, 1.0]])
    turns = [
        SpeakerTurn(speaker="SPEAKER_00", start_seconds=0.0, end_seconds=3.0),
        SpeakerTurn(speaker="SPEAKER_01", start_seconds=3.0, end_seconds=6.0),
    ]

    matches = identify_speakers_by_face("fake.mp4", turns, engine, known, similarity_threshold=0.5, frames_per_turn=3)

    assert matches["SPEAKER_00"].user_id == "u-cuong"
    assert matches["SPEAKER_01"].user_id == "u-trong"


def test_identify_speakers_by_face_leaves_unmatched_speakers_absent(tmp_path, monkeypatch):
    monkeypatch.setattr(
        pipeline_module, "extract_frames_in_range", lambda video_path, start, end, max_frames: _fake_frames(tmp_path, start, max_frames)
    )

    known = [EnrolledFace(user_id="u-cuong", name="Cuong", embedding=[1.0, 0.0])]
    # No detected face at all for this turn.
    engine = FakeFaceRecognitionEngine([None, None, None])
    turns = [SpeakerTurn(speaker="SPEAKER_00", start_seconds=0.0, end_seconds=3.0)]

    matches = identify_speakers_by_face("fake.mp4", turns, engine, known, similarity_threshold=0.5, frames_per_turn=3)

    assert "SPEAKER_00" not in matches
