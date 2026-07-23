"""Orchestrates one /api/v1/pronunciation/score call: G2P the expected text, run the acoustic
model, and score. Kept separate from the FastAPI route so it's callable/testable without an
HTTP layer.
"""

from __future__ import annotations

import wave

import numpy as np

from app.pronunciation.g2p import G2pProvider
from app.pronunciation.gop_model import Wav2Vec2GopModel
from app.pronunciation.gop_scorer import GopResult, score_pronunciation


def read_wav_as_float32(wav_path: str) -> tuple[np.ndarray, int]:
    """Reads a 16-bit PCM mono WAV (as produced by `app.stt.audio_convert.convert_to_wav`) into a
    float32 array in [-1, 1], the format `Wav2Vec2FeatureExtractor` expects."""
    with wave.open(wav_path, "rb") as wav_file:
        sample_rate = wav_file.getframerate()
        raw = wav_file.readframes(wav_file.getnframes())
    audio = np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0
    return audio, sample_rate


def score_pronunciation_from_wav(
    wav_path: str, expected_text: str, g2p_provider: G2pProvider, gop_model: Wav2Vec2GopModel
) -> GopResult:
    expected_words = g2p_provider.phonemize(expected_text)
    audio, sample_rate = read_wav_as_float32(wav_path)
    log_probs, id2label = gop_model.frame_log_probs(audio, sample_rate)
    return score_pronunciation(expected_words, log_probs, id2label, gop_model.blank_id)
