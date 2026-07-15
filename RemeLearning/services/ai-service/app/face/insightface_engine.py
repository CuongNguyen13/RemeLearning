import cv2
from insightface.app import FaceAnalysis

from app.face.base import DetectedFace, FaceRecognitionEngine


class InsightFaceEngine(FaceRecognitionEngine):
    """Face detection + ArcFace embeddings via insightface's buffalo_l model pack, run on
    onnxruntime. Downloads model weights to ~/.insightface on first use - the same
    "pretrained model fetched at runtime" pattern app/stt/diarization.py and
    app/stt/whisper_engine.py already follow for pyannote/faster-whisper."""

    def __init__(self) -> None:
        self._app = FaceAnalysis(name="buffalo_l", providers=["CPUExecutionProvider"])
        self._app.prepare(ctx_id=-1, det_size=(640, 640))

    def detect_faces(self, image_path: str) -> list[DetectedFace]:
        image = cv2.imread(image_path)
        if image is None:
            return []

        faces = self._app.get(image)
        return [
            DetectedFace(
                embedding=face.normed_embedding.tolist(),
                timestamp_seconds=0.0,
                detection_confidence=float(face.det_score),
            )
            for face in faces
        ]
