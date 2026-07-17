"""Python mirror of the Java scoring value types (common.scoring.*), so ai-service can compute the
SAME composite weak-point score the Java-direct redo path does. Kept import-light (pure dataclasses)
so it can be unit-tested without the ML stack."""

from dataclasses import dataclass


@dataclass
class ScoringState:
    """Mirrors common.scoring.ScoringState - a learner's per-item memory/mastery state."""

    half_life_days: float
    ease_factor: float
    mastery: float
    leitner_box: int
    days_since_prior_review: float


@dataclass
class PopulationStats:
    """Mirrors common.scoring.PopulationStats - cross-learner correct/incorrect counts for one item."""

    correct_count: int = 0
    incorrect_count: int = 0


@dataclass
class BktParams:
    """Mirrors common.scoring.BktParams - the four Bayesian Knowledge Tracing parameters."""

    p_init: float
    p_transit: float
    p_slip: float
    p_guess: float


# Mirrors common.scoring.BktParams.DEFAULT (literature-typical defaults for a generic skill).
DEFAULT_BKT_PARAMS = BktParams(p_init=0.3, p_transit=0.1, p_slip=0.1, p_guess=0.2)

# Cold-start defaults mirroring english-service's WeakPointScoringOrchestratorImpl, used when an
# event doesn't carry the per-item scoring state yet.
DEFAULT_HALF_LIFE_DAYS = 7.0
DEFAULT_EASE_FACTOR = 2.5
DEFAULT_LEITNER_BOX = 1
DEFAULT_MASTERY = DEFAULT_BKT_PARAMS.p_init
