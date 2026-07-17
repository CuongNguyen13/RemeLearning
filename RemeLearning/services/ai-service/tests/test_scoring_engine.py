"""Parity tests for the Python scoring-engine port (app/scoring/) - values match the Java
common.scoring.* formulas - plus the ScoringEngineAnalyzer. Pure logic, no ML stack needed."""

import math

import pytest

from app.analysis.scoring_engine_analyzer import ScoringEngineAnalyzer
from app.schemas.events import MistakeHistoryItem, Segment
from app.scoring.engine import (
    difficulty_weight,
    forgetting,
    leitner_next_box,
    update_half_life,
    update_half_life_continuous,
    update_mastery,
    update_mastery_continuous,
    weak_score,
)
from app.scoring.models import DEFAULT_BKT_PARAMS, PopulationStats, ScoringState


def test_difficulty_weight_centers_at_one_and_clamps():
    assert difficulty_weight(PopulationStats(0, 0)) == pytest.approx(1.0)
    # Hard item (population misses it) -> weight > 1.
    assert difficulty_weight(PopulationStats(correct_count=0, incorrect_count=10)) > 1.5
    # Easy item -> clamped at the 0.5 floor.
    assert difficulty_weight(PopulationStats(correct_count=100, incorrect_count=0)) == pytest.approx(0.5)


def test_forgetting_matches_ebbinghaus():
    assert forgetting(0.0, 7.0) == pytest.approx(0.0)
    assert forgetting(7.0, 7.0) == pytest.approx(1.0 - math.exp(-1.0))


def test_weak_score_composite_and_recurrence_boost():
    state = ScoringState(half_life_days=7.0, ease_factor=2.5, mastery=0.3, leitner_box=1, days_since_prior_review=7.0)
    pop = PopulationStats(0, 0)
    base = weak_score(state, pop, recurs_in_batch=False)
    boosted = weak_score(state, pop, recurs_in_batch=True)
    # 1.0 (difficulty) * (1-e^-1) forgetting * 0.7 mastery gap * 1.0
    assert base == pytest.approx((1.0 - math.exp(-1.0)) * 0.7)
    assert boosted == pytest.approx(base * 1.5)


def test_bkt_update_mastery_matches_java():
    # Same numbers as the Java BktModel closed form for the DEFAULT params.
    posterior = update_mastery(0.3, correct=True, params=DEFAULT_BKT_PARAMS)
    assert posterior == pytest.approx(0.6927, abs=1e-4)


def test_adaptive_half_life_and_leitner():
    state = ScoringState(half_life_days=7.0, ease_factor=2.5, mastery=0.3, leitner_box=1, days_since_prior_review=7.0)
    hl_correct, ease_correct = update_half_life(state, correct=True)
    assert (hl_correct, ease_correct) == pytest.approx((17.5, 2.6))
    hl_wrong, ease_wrong = update_half_life(state, correct=False)
    assert (hl_wrong, ease_wrong) == pytest.approx((3.5, 2.3))
    assert leitner_next_box(1, True) == 2
    assert leitner_next_box(5, True) == 5
    assert leitner_next_box(3, False) == 1


def test_continuous_updates_reduce_to_binary_at_endpoints():
    # Continuous BKT/half-life at score 1.0/0.0 equal the binary correct/incorrect updates.
    assert update_mastery_continuous(0.3, 1.0, DEFAULT_BKT_PARAMS) == pytest.approx(
        update_mastery(0.3, True, DEFAULT_BKT_PARAMS))
    assert update_mastery_continuous(0.3, 0.0, DEFAULT_BKT_PARAMS) == pytest.approx(
        update_mastery(0.3, False, DEFAULT_BKT_PARAMS))

    state = ScoringState(half_life_days=7.0, ease_factor=2.5, mastery=0.3, leitner_box=1, days_since_prior_review=7.0)
    assert update_half_life_continuous(state, 1.0) == pytest.approx(update_half_life(state, True))
    assert update_half_life_continuous(state, 0.0) == pytest.approx(update_half_life(state, False))
    # A mid score interpolates strictly between the two.
    low = update_mastery(0.3, False, DEFAULT_BKT_PARAMS)
    high = update_mastery(0.3, True, DEFAULT_BKT_PARAMS)
    assert update_mastery_continuous(0.3, 0.5, DEFAULT_BKT_PARAMS) == pytest.approx((low + high) / 2)


def test_analyzer_ranks_by_weak_score_and_boosts_recurring():
    analyzer = ScoringEngineAnalyzer()
    history = [
        MistakeHistoryItem(item_id="a", category="vocabulary", label="reluctant",
                           occurrence_count=1, last_seen_days_ago=7.0),
        MistakeHistoryItem(item_id="b", category="grammar", label="past perfect",
                           occurrence_count=1, last_seen_days_ago=1.0),
    ]
    # Session mentions "reluctant" -> its score gets the recurrence boost -> ranks first.
    segments = [Segment(speaker="S1", text="I was reluctant.", start_seconds=0.0, end_seconds=1.0)]

    weak_points = analyzer.analyze(segments, history)

    assert [wp.item_id for wp in weak_points] == ["a", "b"]
    assert weak_points[0].forgetting_score > weak_points[1].forgetting_score
