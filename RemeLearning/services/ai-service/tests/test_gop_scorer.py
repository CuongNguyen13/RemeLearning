import numpy as np

from app.pronunciation.g2p import WordPhonemes
from app.pronunciation.gop_scorer import score_pronunciation

# A tiny fake vocabulary: 0 is the CTC blank, 1-3 are the IPA phones "dot" needs (d, ɑ, t).
ID2LABEL = {0: "<pad>", 1: "d", 2: "ɑ", 3: "t"}
BLANK_ID = 0


def _frame(probs: dict[int, float]) -> list[float]:
    row = [1e-6] * len(ID2LABEL)
    for idx, p in probs.items():
        row[idx] = p
    total = sum(row)
    return [p / total for p in row]


def _log_probs(frames: list[dict[int, float]]) -> np.ndarray:
    return np.log(np.array([_frame(f) for f in frames], dtype=np.float64))


def test_correctly_pronounced_word_scores_high_with_no_weak_phonemes():
    # 3 frames per phone, each frame strongly favoring the expected phone in order d -> ɑ -> t.
    frames = (
        [{1: 0.95}] * 3
        + [{2: 0.95}] * 3
        + [{3: 0.95}] * 3
    )
    log_probs = _log_probs(frames)
    words = [WordPhonemes(word="dot", phones=["D", "AA", "T"])]

    result = score_pronunciation(words, log_probs, ID2LABEL, BLANK_ID)

    assert result.overall > 0.8
    assert result.words[0].score > 0.8
    assert result.weak_phonemes == []
    assert [p.ipa for p in result.words[0].phonemes] == ["d", "ɑ", "t"]


def test_missing_phone_scores_zero_and_is_reported_as_weak():
    # The learner never produced anything resembling "t" - only "d" and "ɑ" appear in the audio.
    frames = [{1: 0.95}] * 3 + [{2: 0.95}] * 3
    log_probs = _log_probs(frames)
    words = [WordPhonemes(word="dot", phones=["D", "AA", "T"])]

    result = score_pronunciation(words, log_probs, ID2LABEL, BLANK_ID)

    scores = {p.arpabet: p.score for p in result.words[0].phonemes}
    assert scores["T"] == 0.0
    assert "T" in result.weak_phonemes
    assert result.overall < 1.0


def test_unmapped_arpabet_phone_is_skipped_without_crashing():
    frames = [{1: 0.95}] * 3
    log_probs = _log_probs(frames)
    words = [WordPhonemes(word="x", phones=["D", "NOT_A_PHONE"])]

    result = score_pronunciation(words, log_probs, ID2LABEL, BLANK_ID)

    assert len(result.words[0].phonemes) == 1
    assert result.words[0].phonemes[0].arpabet == "D"
