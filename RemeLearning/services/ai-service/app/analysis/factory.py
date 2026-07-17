"""Selects the active MistakeAnalyzer from config, so the request handler and REST route share one
place that decides between the legacy heuristic and the Java-consistent scoring engine."""

from app.analysis.base import MistakeAnalyzer
from app.analysis.rule_based_analyzer import RuleBasedAnalyzer
from app.analysis.scoring_engine_analyzer import ScoringEngineAnalyzer
from app.config import settings


def create_analyzer() -> MistakeAnalyzer:
    # scoring-engine -> Java-consistent composite score; anything else -> legacy occurrence x forgetting.
    if settings.analysis_scorer == "scoring-engine":
        return ScoringEngineAnalyzer()
    return RuleBasedAnalyzer()
