import math
import struct
import wave

import pytest

from app.voice_auth.base import SYNTHETIC, UNCERTAIN
from app.voice_auth.heuristic_analyzer import HeuristicVoiceAuthenticityAnalyzer


def _write_wav(path, samples: list[int], frame_rate: int = 16000) -> None:
    with wave.open(str(path), "wb") as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(frame_rate)
        wav_file.writeframes(b"".join(struct.pack("<h", sample) for sample in samples))


def test_perfectly_smooth_continuous_tone_is_classified_as_synthetic(tmp_path):
    """A pure sine tone at constant frequency/amplitude with no pauses has ~zero jitter,
    shimmer, and pause-length variation - exactly the "too smooth/regular" pattern the
    heuristic is designed to flag (see heuristic_analyzer.py's module docstring: real speech
    has natural micro-irregularities this signal lacks)."""
    frame_rate = 16000
    frequency_hz = 150.0
    amplitude = 10000
    sample_count = int(frame_rate * 2.0)
    samples = [int(amplitude * math.sin(2 * math.pi * frequency_hz * i / frame_rate)) for i in range(sample_count)]

    wav_path = tmp_path / "tone.wav"
    _write_wav(wav_path, samples, frame_rate)

    result = HeuristicVoiceAuthenticityAnalyzer().analyze(str(wav_path))

    assert result.label == SYNTHETIC
    assert result.features["jitter"] == pytest.approx(0.0, abs=1e-3)


def test_clip_shorter_than_minimum_duration_is_uncertain(tmp_path):
    wav_path = tmp_path / "short.wav"
    _write_wav(wav_path, [0] * 1000, frame_rate=16000)  # ~0.06s, well under the 0.5s floor

    result = HeuristicVoiceAuthenticityAnalyzer().analyze(str(wav_path))

    assert result.label == UNCERTAIN
    assert result.confidence == 0.0
    assert result.features == {"reason_too_short": 1.0}


def test_pure_silence_has_no_voiced_frames(tmp_path):
    wav_path = tmp_path / "silence.wav"
    _write_wav(wav_path, [0] * 16000 * 2, frame_rate=16000)  # 2s of digital silence

    result = HeuristicVoiceAuthenticityAnalyzer().analyze(str(wav_path))

    assert result.label == UNCERTAIN
    assert result.features == {"reason_no_voiced_frames": 1.0}
