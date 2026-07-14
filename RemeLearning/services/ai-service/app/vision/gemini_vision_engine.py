import base64

import httpx

from app.config import settings
from app.vision.base import FrameCaption, FrameSample, VisionCaptionEngine

_GENERATE_CONTENT_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
_CAPTION_PROMPT = (
    "Describe in one short English sentence what is visually shown in this video frame - "
    "concrete objects, actions, and any on-screen text (captions, signs, slides)."
)


class GeminiVisionEngine(VisionCaptionEngine):
    """Calls Gemini's generateContent REST API with an inline image part to caption each
    sampled video frame. common's LlmClient/GeminiLlmClient (Java) only sends text parts
    today, so this is a fresh REST call rather than a shared client - see GeminiLlmClient
    for the equivalent text-only request/response shape this mirrors."""

    def __init__(self) -> None:
        self._client = httpx.Client(timeout=30.0)

    def caption_frames(self, frames: list[FrameSample]) -> list[FrameCaption]:
        return [self._caption_one(frame) for frame in frames]

    def _caption_one(self, frame: FrameSample) -> FrameCaption:
        with open(frame.image_path, "rb") as image_file:
            image_b64 = base64.b64encode(image_file.read()).decode("ascii")

        response = self._client.post(
            _GENERATE_CONTENT_URL.format(model=settings.gemini_vision_model),
            params={"key": settings.gemini_api_key},
            json={
                "contents": [
                    {
                        "role": "user",
                        "parts": [
                            {"text": _CAPTION_PROMPT},
                            {"inline_data": {"mime_type": "image/jpeg", "data": image_b64}},
                        ],
                    }
                ]
            },
        )
        response.raise_for_status()

        candidates = response.json().get("candidates") or []
        caption = candidates[0]["content"]["parts"][0]["text"].strip() if candidates else ""
        return FrameCaption(caption=caption, timestamp_seconds=frame.timestamp_seconds)
