"""Tests for the offline parameter estimator (app/scoring/fit.py). Pure logic, no DB/ML needed."""

import math

import pytest

from app.scoring.fit import estimate_bkt_params, estimate_rasch_difficulty


def test_estimate_returns_defaults_for_no_data():
    result = estimate_bkt_params([])
    assert result.sample_size == 0
    assert result.params.p_init == pytest.approx(0.3)


def test_estimate_recovers_low_slip_high_guess_ordering():
    # Learners who answer correctly then keep answering correctly (very low slip),
    # and who often turn an incorrect into a correct (higher guess/learning).
    sequences = [
        [True, True, True, True],
        [False, True, True, True],
        [False, False, True, True],
        [True, True, True, False],  # a single slip
    ]
    result = estimate_bkt_params(sequences)
    params = result.params

    assert result.sample_size == 4
    # All probabilities stay within their clamped, sensible ranges.
    assert 0.0 < params.p_slip <= 0.3
    assert 0.0 < params.p_guess <= 0.5
    assert 0.02 <= params.p_init <= 0.95
    assert 0.02 <= params.p_transit <= 0.6
    # This data has exactly one correct->incorrect (slip) but several incorrect->correct (guess/learn),
    # so guess should come out clearly higher than slip.
    assert params.p_guess > params.p_slip


def test_rasch_difficulty_sign():
    # An item the population fails more often than it passes -> positive difficulty.
    assert estimate_rasch_difficulty(correct_count=2, incorrect_count=20) > 0
    # An easy item -> negative difficulty.
    assert estimate_rasch_difficulty(correct_count=20, incorrect_count=2) < 0
    # Balanced -> ~0.
    assert estimate_rasch_difficulty(correct_count=5, incorrect_count=5) == pytest.approx(0.0, abs=1e-9)
