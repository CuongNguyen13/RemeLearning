"""Pure-logic tests for app/tts/base.py - no Supertonic/ONNX model needed (the ML wrapper lives in
supertonic_engine.py, which is not imported here)."""

import pytest

from app.tts.base import DEFAULT_LANG, DEFAULT_VOICE, resolve_lang, resolve_voice


@pytest.mark.parametrize(
    "given,expected",
    [
        ("F1", "F1"),
        ("m3", "M3"),  # case-insensitive
        ("  f2  ", "F2"),  # trimmed
        ("X9", DEFAULT_VOICE),  # unknown -> default
        ("", DEFAULT_VOICE),
        (None, DEFAULT_VOICE),
    ],
)
def test_resolve_voice(given, expected):
    assert resolve_voice(given) == expected


@pytest.mark.parametrize(
    "given,expected",
    [
        ("en", "en"),
        ("en-US", "en"),  # BCP-47 tag reduced to primary subtag
        ("VI", "vi"),
        ("", DEFAULT_LANG),
        (None, DEFAULT_LANG),
    ],
)
def test_resolve_lang(given, expected):
    assert resolve_lang(given) == expected
