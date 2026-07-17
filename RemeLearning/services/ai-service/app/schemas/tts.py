"""Request/response models for the synchronous TTS endpoint (app/api/routes.py). Kept out of
events.py because TTS is a REST call, not a Kafka event payload."""

from pydantic import BaseModel


class TtsSynthesizeRequest(BaseModel):
    text: str
    lang: str = "en"
    # Preset voice id (F1-F5 / M1-M5); None lets the service use its configured default.
    voice: str | None = None


class TtsSynthesizeResponse(BaseModel):
    audio_base64: str
    mime_type: str
    sample_rate: int
