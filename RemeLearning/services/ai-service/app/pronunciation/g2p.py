"""Grapheme-to-phoneme (G2P): turns target English text into the ARPAbet phoneme sequence a
learner is expected to pronounce, split by word so GOP scores can be aggregated per word.

Two backends, one interface (``G2pProvider``), so the module can move from the pure-Python
``g2p_en`` backend used today to ``phonemizer``/``espeak-ng`` later (better out-of-CMUdict-
vocabulary coverage, real IPA output) without touching the scoring code - see
``docs/`` decision log / PLAN's "Rủi ro" section for the tradeoff.
"""

from __future__ import annotations

import re
from abc import ABC, abstractmethod
from dataclasses import dataclass


@dataclass(frozen=True)
class WordPhonemes:
    word: str
    # ARPAbet phones, stress markers stripped (e.g. "IH1" -> "IH") - GOP scores the phone
    # identity, not lexical stress.
    phones: list[str]


class G2pProvider(ABC):
    """Converts English text into its expected ARPAbet phoneme sequence, one entry per word."""

    @abstractmethod
    def phonemize(self, text: str) -> list[WordPhonemes]:
        raise NotImplementedError


_STRESS_DIGIT = re.compile(r"\d$")
# An ARPAbet phone token: 1-3 uppercase letters, optionally followed by a 0-2 stress digit (e.g.
# "DH", "IH1"). Anything else in g2p_en's output (" ", ".", ",", ...) is a separator or punctuation,
# not a phone - `str.isalpha()` alone doesn't work here since a phone WITH a stress digit
# ("IH1") is not fully alphabetic.
_PHONE_TOKEN = re.compile(r"^[A-Z]{1,3}[0-2]?$")


class G2pEnProvider(G2pProvider):
    """Pure-Python backend (`g2p_en`, CMUdict + a small seq2seq model for out-of-vocabulary
    words) - no system dependency (no `espeak-ng` binary needed), the deliberate tradeoff for
    this stage: weaker than `phonemizer`/`espeak-ng` on words outside CMUdict, acceptable while
    this feature is new. See `G2pProvider`'s docstring for the upgrade path.
    """

    def __init__(self) -> None:
        from g2p_en import G2p  # heavy import (loads NLTK data) - deferred to first use

        self._g2p = G2p()

    def phonemize(self, text: str) -> list[WordPhonemes]:
        raw = self._g2p(text)
        words: list[WordPhonemes] = []
        current_word: list[str] = []
        for token in raw:
            if token == " ":
                if current_word:
                    words.append(WordPhonemes(word="", phones=current_word))
                    current_word = []
                continue
            if not _PHONE_TOKEN.match(token):
                # Punctuation from g2p_en (".", ",", ...) - not a phone, and not a word boundary
                # by itself (g2p_en already emits a preceding " ").
                continue
            current_word.append(_STRESS_DIGIT.sub("", token))
        if current_word:
            words.append(WordPhonemes(word="", phones=current_word))

        # g2p_en doesn't echo the original word text per phone group, so recover it by
        # re-splitting the input on whitespace.
        # ponytail: g2p_en tokenizes internally with nltk.word_tokenize, which splits
        # contractions ("don't" -> "do", "n't") into two phone-groups; a plain whitespace split
        # here keeps "don't" as one word, so word labels can misalign by one for sentences with
        # contractions/other multi-token words. Acceptable for this PoC (phone-level scores are
        # still correct; only the word-label attribution can drift) - upgrade path: tokenize with
        # nltk.word_tokenize on both sides instead of str.split.
        plain_words = [w.strip(".,!?;:\"'") for w in re.split(r"\s+", text.strip()) if w.strip(".,!?;:\"'")]
        return [
            WordPhonemes(word=plain_words[i] if i < len(plain_words) else "", phones=group.phones)
            for i, group in enumerate(words)
        ]
