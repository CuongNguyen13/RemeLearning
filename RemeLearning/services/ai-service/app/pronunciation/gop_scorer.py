"""Goodness-of-Pronunciation (GOP) scoring: given a learner's audio and the phoneme sequence they
were expected to say, scores how well the acoustic model's posteriors support each expected phone
having actually occurred.

ponytail: this is a SIMPLIFIED GOP, not the textbook forced-alignment version (Viterbi/CTC forced
segmentation of the acoustic model against the expected sequence, then log-posterior-ratio per
aligned phone). Instead: (1) greedy-decode the model's own most-likely phone at every ~20ms frame
and collapse repeats ("recognition", not alignment-to-expected), (2) edit-distance-align that
recognized sequence against the expected sequence (same Wagner-Fischer approach
`DictationScorer.java` uses for words, applied to phones here), (3) score each aligned expected
phone by the mean posterior *of that expected phone* (not the recognized one) over the frames the
alignment assigned it, and (4) score a deleted (unmatched) expected phone as 0 - the model's own
top pick never matched it anywhere in the utterance.
Ceiling: for tightly-packed or reordered speech this recognition-then-align approach can
mis-segment frame ranges that true forced alignment would place correctly, and it costs one
argmax + edit-distance pass instead of a full DP forced alignment.
Upgrade path: replace steps (1)-(2) with proper CTC forced alignment
(`torchaudio.functional.forced_align`, available in newer torchaudio - not the 2.2.2 pinned here)
once accuracy against real graded audio shows this approximation isn't good enough.
"""

from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np

from app.pronunciation.arpabet_ipa import arpabet_to_ipa
from app.pronunciation.g2p import WordPhonemes
from app.pronunciation.gop_model import FRAME_DURATION_SECONDS

# A phone whose GOP score falls below this is surfaced in `weak_phonemes`.
WEAK_PHONEME_THRESHOLD = 0.4


@dataclass
class PhonemeScore:
    arpabet: str
    ipa: str
    score: float


@dataclass
class WordScore:
    word: str
    score: float
    phonemes: list[PhonemeScore] = field(default_factory=list)


@dataclass
class GopResult:
    overall: float
    words: list[WordScore]
    weak_phonemes: list[str]


def _greedy_segments(log_probs: np.ndarray, id2label: dict[int, str], blank_id: int) -> list[tuple[str, int, int]]:
    """Collapses consecutive identical non-blank argmax frames into (label, start_frame, end_frame) runs."""
    argmax_ids = log_probs.argmax(axis=1)
    segments: list[tuple[str, int, int]] = []
    current_label: str | None = None
    start = 0
    total = len(argmax_ids)
    for t in range(total):
        label_id = int(argmax_ids[t])
        label = None if label_id == blank_id else id2label.get(label_id)
        if label != current_label:
            if current_label is not None:
                segments.append((current_label, start, t))
            current_label = label
            start = t
    if current_label is not None:
        segments.append((current_label, start, total))
    return segments


def _align(expected: list[str], predicted: list[str]) -> list[tuple[int | None, int | None]]:
    """Wagner-Fischer edit-distance alignment (same algorithm as DictationScorer.java, applied to
    phone labels instead of words). Returns pairs of (expected_index, predicted_index); either
    side is None for a deletion (expected, unmatched) or insertion (predicted, ignored by the caller).
    """
    n, m = len(expected), len(predicted)
    dp = [[0] * (m + 1) for _ in range(n + 1)]
    for i in range(n + 1):
        dp[i][0] = i
    for j in range(m + 1):
        dp[0][j] = j
    for i in range(1, n + 1):
        for j in range(1, m + 1):
            if expected[i - 1] == predicted[j - 1]:
                dp[i][j] = dp[i - 1][j - 1]
            else:
                dp[i][j] = 1 + min(dp[i - 1][j - 1], dp[i - 1][j], dp[i][j - 1])

    pairs: list[tuple[int | None, int | None]] = []
    i, j = n, m
    while i > 0 or j > 0:
        if i > 0 and j > 0 and expected[i - 1] == predicted[j - 1] and dp[i][j] == dp[i - 1][j - 1]:
            pairs.append((i - 1, j - 1))
            i, j = i - 1, j - 1
        elif i > 0 and j > 0 and dp[i][j] == dp[i - 1][j - 1] + 1:
            pairs.append((i - 1, j - 1))
            i, j = i - 1, j - 1
        elif i > 0 and dp[i][j] == dp[i - 1][j] + 1:
            pairs.append((i - 1, None))
            i -= 1
        else:
            pairs.append((None, j - 1))
            j -= 1
    pairs.reverse()
    return pairs


def score_pronunciation(
    expected_words: list[WordPhonemes],
    log_probs: np.ndarray,
    id2label: dict[int, str],
    blank_id: int,
) -> GopResult:
    label_to_id = {label: idx for idx, label in id2label.items()}
    segments = _greedy_segments(log_probs, id2label, blank_id)
    predicted_labels = [label for label, _, _ in segments]

    # Flatten expected phones to one IPA sequence, remembering which word/arpabet each came from;
    # phones with no ARPAbet->IPA mapping (shouldn't happen for standard CMUdict output - see
    # arpabet_ipa.py) are skipped rather than guessed.
    flat_expected: list[tuple[int, str, str]] = []  # (word_index, arpabet, ipa)
    for word_idx, word in enumerate(expected_words):
        for phone in word.phones:
            ipa = arpabet_to_ipa(phone)
            if ipa is not None:
                flat_expected.append((word_idx, phone, ipa))

    expected_ipas = [ipa for _, _, ipa in flat_expected]
    alignment = _align(expected_ipas, predicted_labels)
    aligned_predicted_index_by_expected: dict[int, int] = {
        exp_i: pred_i for exp_i, pred_i in alignment if exp_i is not None and pred_i is not None
    }

    words: list[WordScore] = [WordScore(word=w.word, score=0.0) for w in expected_words]
    all_scores: list[float] = []
    weak: list[str] = []

    for flat_i, (word_idx, arpabet, ipa) in enumerate(flat_expected):
        predicted_i = aligned_predicted_index_by_expected.get(flat_i)
        if predicted_i is None:
            score = 0.0
        else:
            _, start, end = segments[predicted_i]
            vocab_id = label_to_id.get(ipa)
            if vocab_id is None or end <= start:
                score = 0.0
            else:
                score = float(np.exp(log_probs[start:end, vocab_id]).mean())
        score = max(0.0, min(1.0, score))
        all_scores.append(score)
        if score < WEAK_PHONEME_THRESHOLD:
            weak.append(arpabet)
        words[word_idx].phonemes.append(PhonemeScore(arpabet=arpabet, ipa=ipa, score=score))

    for word_score in words:
        word_score.score = (
            sum(p.score for p in word_score.phonemes) / len(word_score.phonemes) if word_score.phonemes else 0.0
        )

    overall = sum(all_scores) / len(all_scores) if all_scores else 0.0
    # Deduplicate weak phonemes, preserving first-seen order.
    seen: set[str] = set()
    unique_weak = [p for p in weak if not (p in seen or seen.add(p))]
    return GopResult(overall=overall, words=words, weak_phonemes=unique_weak)


__all__ = ["GopResult", "PhonemeScore", "WordScore", "score_pronunciation", "FRAME_DURATION_SECONDS"]
