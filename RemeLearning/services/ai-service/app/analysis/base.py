from abc import ABC, abstractmethod

from app.schemas.events import MistakeHistoryItem, Segment, WeakPoint


class MistakeAnalyzer(ABC):
    """Ranks the learner's recurring mistakes given the new session's transcript and their history.

    Swappable: today implemented with plain statistics (rule_based_analyzer.py); a paid-LLM
    implementation can be dropped in later behind this same interface without touching the pipeline.
    """

    @abstractmethod
    def analyze(self, segments: list[Segment], history: list[MistakeHistoryItem]) -> list[WeakPoint]:
        raise NotImplementedError
