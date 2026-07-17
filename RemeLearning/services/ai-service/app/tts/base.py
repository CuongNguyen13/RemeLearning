"""Pure text-to-speech contracts and helpers, kept free of the Supertonic/ONNX ML imports (which
live in supertonic_engine.py) so these can be imported and unit-tested without the model stack -
same split as app/stt/base.py vs app/stt/whisper_engine.py."""

from dataclasses import dataclass

# Supertonic ships ten preset voices: five female (F1-F5) and five male (M1-M5).
SUPPORTED_VOICES = ("F1", "F2", "F3", "F4", "F5", "M1", "M2", "M3", "M4", "M5")
DEFAULT_VOICE = "F1"
DEFAULT_LANG = "en"
# Supertonic outputs 44.1kHz WAV; used only if the loaded model doesn't expose a sample_rate attr.
DEFAULT_SAMPLE_RATE_FALLBACK = 44100


@dataclass
class TtsSynthesisRequest:
    text: str
    lang: str = DEFAULT_LANG
    voice: str = DEFAULT_VOICE


@dataclass
class TtsSynthesisResult:
    audio_base64: str
    mime_type: str
    sample_rate: int


def resolve_voice(voice: str | None) -> str:
    """Returns a valid Supertonic preset voice: the requested one (case-insensitive) if supported,
    else the default. Never raises, so an unknown/blank voice degrades to the default rather than
    failing synthesis."""
    if not voice:
        return DEFAULT_VOICE
    normalized = voice.strip().upper()
    return normalized if normalized in SUPPORTED_VOICES else DEFAULT_VOICE


def resolve_lang(lang: str | None) -> str:
    """Returns a short language code Supertonic expects; a full BCP-47 tag like 'en-US' is reduced
    to its primary subtag ('en'), and a blank value falls back to the default."""
    if not lang:
        return DEFAULT_LANG
    return lang.split("-")[0].strip().lower() or DEFAULT_LANG
