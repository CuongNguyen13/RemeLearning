"""Loads `facebook/wav2vec2-lv-60-espeak-cv-ft` and runs a forward pass to get per-frame phoneme
log-probabilities, the acoustic side of Goodness-of-Pronunciation (GOP) scoring.

Deliberately bypasses `Wav2Vec2Processor`/`Wav2Vec2PhonemeCTCTokenizer`: that tokenizer's
`from_pretrained` requires the `phonemizer` package, which itself needs the `espeak-ng` system
binary - exactly the system dependency this stage's G2P (`app.pronunciation.g2p`) was chosen to
avoid. We only need this model's *acoustic* output (per-frame logits over its phoneme vocabulary)
and the id->phoneme vocab mapping to read them, neither of which needs the phonemizer-backed
tokenizer - so `Wav2Vec2FeatureExtractor` (audio preprocessing) plus a raw `vocab.json` load
covers everything the GOP scorer needs.
"""

from __future__ import annotations

import json

import numpy as np
import torch

MODEL_ID = "facebook/wav2vec2-lv-60-espeak-cv-ft"
# wav2vec2's conv-feature-encoder has a total stride of 320 samples at 16kHz => 20ms/frame.
FRAME_DURATION_SECONDS = 320 / 16000


class Wav2Vec2GopModel:
    """Lazily loads the model/feature-extractor/vocab on first use (loading takes real time and
    memory - not worth paying at import time or for callers that never touch pronunciation)."""

    def __init__(self, model_id: str = MODEL_ID) -> None:
        self._model_id = model_id
        self._model = None
        self._feature_extractor = None
        self._id2label: dict[int, str] | None = None
        self._blank_id: int = 0

    def _ensure_loaded(self) -> None:
        if self._model is not None:
            return
        from huggingface_hub import hf_hub_download
        from transformers import Wav2Vec2FeatureExtractor, Wav2Vec2ForCTC

        self._feature_extractor = Wav2Vec2FeatureExtractor.from_pretrained(self._model_id)
        self._model = Wav2Vec2ForCTC.from_pretrained(self._model_id)
        self._model.eval()
        self._blank_id = self._model.config.pad_token_id or 0
        vocab_path = hf_hub_download(self._model_id, "vocab.json")
        with open(vocab_path, encoding="utf-8") as f:
            vocab: dict[str, int] = json.load(f)
        self._id2label = {v: k for k, v in vocab.items()}

    @property
    def blank_id(self) -> int:
        self._ensure_loaded()
        return self._blank_id

    def frame_log_probs(self, audio: np.ndarray, sample_rate: int) -> tuple[np.ndarray, dict[int, str]]:
        """
        @param audio 1-D float32 mono waveform in [-1, 1]
        @param sample_rate must be 16000 (the model's expected rate - callers resample beforehand)
        @return (log_probs [T, V], id2label) - log_probs[t] is the log-softmax over the vocabulary
                at frame t (~20ms each).
        """
        self._ensure_loaded()
        if sample_rate != 16000:
            raise ValueError(f"Wav2Vec2GopModel requires 16kHz audio, got {sample_rate}Hz")

        inputs = self._feature_extractor(audio, sampling_rate=sample_rate, return_tensors="pt")
        with torch.no_grad():
            logits = self._model(inputs.input_values).logits[0]  # [T, V]
        log_probs = torch.log_softmax(logits, dim=-1).numpy()
        return log_probs, dict(self._id2label)
