"""Offline calibration of the scoring parameters (BKT + Rasch difficulty) from real attempt data,
replacing the literature-default BktParams once enough review logs exist.

This is a first-pass **empirical** estimator (Baker/Corbett-style conditional probabilities): cheap,
closed-form, and honest about being an approximation. When the review-log volume justifies it, the
natural upgrade is full Expectation-Maximization over the BKT HMM - the CLI below is the wiring point
for that. Kept as pure functions over in-memory sequences so it's unit-testable without a DB or the
ML stack; the DB-reading CLI (`python -m app.scoring.fit`) is a thin wrapper to add when data lands.
"""

import math
from dataclasses import dataclass

from app.scoring.models import BktParams

# Clamp bounds so a tiny/degenerate sample can't produce absurd (0 or 1) probabilities.
_MIN_P = 0.02
_MAX_SLIP = 0.3
_MAX_GUESS = 0.5
_MAX_TRANSIT = 0.6


@dataclass
class FitResult:
    params: BktParams
    sample_size: int


def _clamp(value: float, low: float, high: float) -> float:
    return min(high, max(low, value))


def estimate_bkt_params(sequences: list[list[bool]]) -> FitResult:
    """Estimates BKT params from per-learner ordered correct/incorrect sequences for one item/skill.

    - p_slip  ~ P(incorrect now | correct previously)  - a mistake despite apparent mastery.
    - p_guess ~ P(correct now   | incorrect previously) - a correct answer despite no mastery.
    - p_init  ~ de-biased fraction correct on the first opportunity: (P(correct_1) - guess)/(1 - slip - guess).
    - p_transit ~ P(correct now | incorrect previously) net of guess, i.e. the learning rate signal.

    Sequences shorter than 1 are ignored; returns literature defaults when there's no usable data.
    """
    first_correct = 0
    first_total = 0
    after_correct_total = 0
    after_correct_incorrect = 0
    after_incorrect_total = 0
    after_incorrect_correct = 0

    for seq in sequences:
        if not seq:
            continue
        first_total += 1
        if seq[0]:
            first_correct += 1
        for prev, curr in zip(seq, seq[1:]):
            if prev:
                after_correct_total += 1
                if not curr:
                    after_correct_incorrect += 1
            else:
                after_incorrect_total += 1
                if curr:
                    after_incorrect_correct += 1

    if first_total == 0:
        return FitResult(params=BktParams(p_init=0.3, p_transit=0.1, p_slip=0.1, p_guess=0.2), sample_size=0)

    p_slip = _clamp(after_correct_incorrect / after_correct_total, _MIN_P, _MAX_SLIP) if after_correct_total else 0.1
    p_guess = _clamp(after_incorrect_correct / after_incorrect_total, _MIN_P, _MAX_GUESS) if after_incorrect_total else 0.2

    first_correct_rate = first_correct / first_total
    denom = max(1e-6, 1 - p_slip - p_guess)
    p_init = _clamp((first_correct_rate - p_guess) / denom, _MIN_P, 0.95)
    # Learning rate: how often an incorrect is followed by a correct, net of pure guessing.
    p_transit = _clamp((after_incorrect_correct / after_incorrect_total - p_guess) if after_incorrect_total else 0.1,
                       _MIN_P, _MAX_TRANSIT)

    return FitResult(
        params=BktParams(p_init=p_init, p_transit=p_transit, p_slip=p_slip, p_guess=p_guess),
        sample_size=first_total,
    )


def estimate_rasch_difficulty(correct_count: int, incorrect_count: int, smoothing: float = 1.0) -> float:
    """Rasch difficulty parameter b (log-odds of failure) for an item, from population counts. Matches
    the log-odds the live RaschDifficultyEstimator uses, exposed as the raw fitted difficulty here."""
    return math.log((incorrect_count + smoothing) / (correct_count + smoothing))


def main() -> None:  # pragma: no cover - DB wiring point, exercised manually once data exists
    """CLI stub: once review logs exist, load attempt sequences per (category, item) from the
    practice data and print calibrated BktParams to seed english-service's per-category config."""
    raise SystemExit(
        "Parameter fitting needs real attempt data. Wire this to the practice logs "
        "(mistake_history / practice_attempts) when volume is sufficient; estimate_bkt_params() "
        "and estimate_rasch_difficulty() are ready and unit-tested."
    )


if __name__ == "__main__":  # pragma: no cover
    main()
