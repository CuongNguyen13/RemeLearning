"""Analyzer that ranks recurring mistakes with the SAME composite formula as Java's
common.scoring.WeakPointScoringEngine (Rasch difficulty x adaptive-half-life forgetting x mastery
gap x recurrence boost), instead of the older `occurrence_count x forgetting` heuristic. This makes
the ai-service/Kafka `learning.gap.analyzed` scores consistent with english-service's Java-direct
redo path. Selected via ANALYSIS_SCORER=scoring-engine (see app/analysis/factory.py)."""

from app.analysis.base import MistakeAnalyzer
from app.analysis.rule_based_analyzer import _mentions_label, _recommendation_for
from app.schemas.events import MistakeHistoryItem, Segment, WeakPoint
from app.scoring.engine import weak_score
from app.scoring.models import PopulationStats, ScoringState


class ScoringEngineAnalyzer(MistakeAnalyzer):
    def __init__(self, top_n: int = 10) -> None:
        self._top_n = top_n

    # Builds each item's ScoringState/PopulationStats from the event (cold-start defaults when a
    # field is absent), scores the current weakness, and returns the top-N most-forgotten items.
    def analyze(self, segments: list[Segment], history: list[MistakeHistoryItem]) -> list[WeakPoint]:
        session_text = " ".join(seg.text for seg in segments)

        weak_points = []
        for item in history:
            recurs = _mentions_label(session_text, item.label)
            state = ScoringState(
                half_life_days=item.half_life_days or 7.0,
                ease_factor=item.ease_factor or 2.5,
                mastery=item.mastery,
                leitner_box=item.leitner_box or 1,
                days_since_prior_review=item.last_seen_days_ago,
            )
            stats = PopulationStats(correct_count=item.correct_count, incorrect_count=item.incorrect_count)
            score = weak_score(state, stats, recurs)
            weak_points.append(
                WeakPoint(
                    item_id=item.item_id,
                    category=item.category,
                    label=item.label,
                    forgetting_score=round(score, 4),
                    recommendation=_recommendation_for(item),
                )
            )

        weak_points.sort(key=lambda wp: wp.forgetting_score, reverse=True)
        return weak_points[: self._top_n]
