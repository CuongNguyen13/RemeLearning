"""Faithful Python port of the Java weak-point scoring engine
(RemeLearning/common/src/main/java/com/remelearning/common/scoring/*). Every constant matches the
Java side exactly, so the same inputs produce the same score/state in both languages - this is what
makes ai-service's Kafka `learning.gap.analyzed` path consistent with english-service's Java-direct
redo path. See docs/API.md "Cơ chế chấm điểm" and the Java class docs for the rationale."""

import math

from app.scoring.models import BktParams, PopulationStats, ScoringState

# --- RaschDifficultyEstimator ---
_RASCH_SMOOTHING = 1.0
_RASCH_SCALE = 2.0
_RASCH_MIN_WEIGHT = 0.5
_RASCH_MAX_WEIGHT = 2.0

# --- WeakPointScoringEngine ---
_RECURRENCE_BOOST = 1.5
_MIN_MASTERY_GAP = 0.15

# --- AdaptiveHalfLifeModel ---
_MIN_EASE_FACTOR = 1.3
_MAX_EASE_FACTOR = 3.0
_MIN_HALF_LIFE_DAYS = 0.5
_EASE_FACTOR_GROWTH_STEP = 0.1
_EASE_FACTOR_SHRINK_STEP = 0.2
_INCORRECT_HALF_LIFE_MULTIPLIER = 0.5

# --- LeitnerScheduler ---
_MIN_BOX = 1
_MAX_BOX = 5


def forgetting(days_since_prior_review: float, prior_half_life_days: float) -> float:
    """1 - exp(-days / halfLife) - retention-decay probability (AdaptiveHalfLifeModel.forgetting)."""
    days = max(0.0, days_since_prior_review)
    return 1.0 - math.exp(-days / prior_half_life_days)


def difficulty_weight(stats: PopulationStats) -> float:
    """Rasch-inspired difficulty weight centered at 1.0 (RaschDifficultyEstimator.difficultyWeight)."""
    log_odds = math.log((stats.incorrect_count + _RASCH_SMOOTHING) / (stats.correct_count + _RASCH_SMOOTHING))
    weight = 1.0 + math.tanh(log_odds / _RASCH_SCALE)
    return min(_RASCH_MAX_WEIGHT, max(_RASCH_MIN_WEIGHT, weight))


def weak_score(state: ScoringState, stats: PopulationStats, recurs_in_batch: bool) -> float:
    """Composite weak-point score = difficultyWeight x forgetting x masteryGap x recurrenceBoost
    (the score half of WeakPointScoringEngine.scoreAfterAttempt). Used to rank recurring mistakes;
    no state update, since the analysis path scores current weakness rather than a graded attempt."""
    fg = forgetting(state.days_since_prior_review, state.half_life_days)
    boost = _RECURRENCE_BOOST if recurs_in_batch else 1.0
    weight = difficulty_weight(stats)
    mastery_gap = max(_MIN_MASTERY_GAP, 1.0 - state.mastery)
    return weight * fg * mastery_gap * boost


def update_mastery(prior_mastery: float, correct: bool, params: BktParams) -> float:
    """BKT posterior P(mastered) after one attempt (BktModel.updateMastery)."""
    p = prior_mastery
    if correct:
        numerator = p * (1 - params.p_slip)
        denominator = numerator + (1 - p) * params.p_guess
    else:
        numerator = p * params.p_slip
        denominator = numerator + (1 - p) * (1 - params.p_guess)
    evidence = numerator / denominator
    posterior = evidence + (1 - evidence) * params.p_transit
    return min(1.0, max(0.0, posterior))


def update_mastery_continuous(prior_mastery: float, score: float, params: BktParams) -> float:
    """Partial-credit BKT (BktModel.updateMasteryContinuous): blends the correct/incorrect posteriors
    by score in [0,1]; reduces exactly to update_mastery at score 1.0/0.0. For pronunciation-style skills."""
    clamped = min(1.0, max(0.0, score))
    mastery_if_correct = update_mastery(prior_mastery, True, params)
    mastery_if_incorrect = update_mastery(prior_mastery, False, params)
    return clamped * mastery_if_correct + (1 - clamped) * mastery_if_incorrect


def update_half_life_continuous(state: ScoringState, score: float) -> tuple[float, float]:
    """Partial-credit half-life (AdaptiveHalfLifeModel.updateHalfLifeContinuous): blends the
    correct/incorrect (half_life, ease) results by score; reduces exactly to the binary update at 1.0/0.0."""
    clamped = min(1.0, max(0.0, score))
    hl_correct, ease_correct = update_half_life(state, True)
    hl_incorrect, ease_incorrect = update_half_life(state, False)
    return (
        clamped * hl_correct + (1 - clamped) * hl_incorrect,
        clamped * ease_correct + (1 - clamped) * ease_incorrect,
    )


def update_half_life(state: ScoringState, correct: bool) -> tuple[float, float]:
    """Next (half_life_days, ease_factor) from this attempt (AdaptiveHalfLifeModel.updateHalfLife)."""
    if correct:
        ease_factor = min(_MAX_EASE_FACTOR, state.ease_factor + _EASE_FACTOR_GROWTH_STEP)
        half_life_days = state.half_life_days * state.ease_factor
    else:
        ease_factor = max(_MIN_EASE_FACTOR, state.ease_factor - _EASE_FACTOR_SHRINK_STEP)
        half_life_days = state.half_life_days * _INCORRECT_HALF_LIFE_MULTIPLIER
    return max(_MIN_HALF_LIFE_DAYS, half_life_days), ease_factor


def leitner_next_box(current_box: int, correct: bool) -> int:
    """Next Leitner box: promote (capped at 5) on correct, reset to 1 on incorrect (LeitnerScheduler.next)."""
    return min(_MAX_BOX, current_box + 1) if correct else _MIN_BOX
