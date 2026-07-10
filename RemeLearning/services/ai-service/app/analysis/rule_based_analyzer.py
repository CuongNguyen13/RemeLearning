import math
import re

from app.analysis.base import MistakeAnalyzer
from app.schemas.events import MistakeHistoryItem, Segment, WeakPoint

# Ebbinghaus-style decay constant (days) - how fast retention fades without review.
DECAY_CONSTANT_DAYS = 7.0
# Boost applied when the mistake shows up again in the *current* session's transcript,
# i.e. direct evidence the learner is still making it ("quen di quen lai").
RECURRENCE_BOOST = 1.5


def _forgetting_score(item: MistakeHistoryItem, recurs_in_session: bool) -> float:
    retention = math.exp(-item.last_seen_days_ago / DECAY_CONSTANT_DAYS)
    forgetting = 1.0 - retention
    score = item.occurrence_count * forgetting
    if recurs_in_session:
        score *= RECURRENCE_BOOST
    return score


def _mentions_label(text: str, label: str) -> bool:
    keyword = label.split(":")[-1].strip().lower()
    if not keyword:
        return False
    return re.search(re.escape(keyword), text, re.IGNORECASE) is not None


_RECOMMENDATION_TEMPLATES = {
    "grammar": "Ôn lại quy tắc ngữ pháp: {label}. Làm 5-10 bài tập áp dụng và thử dùng nó trong câu nói của bạn.",
    "vocabulary": "Ôn lại từ vựng: {label}. Đặt câu mới với từ này và ôn lại theo lịch spaced-repetition.",
    "pronunciation": "Luyện phát âm: {label}. Nghe mẫu chuẩn, ghi âm lại bản thân và so sánh.",
}
_DEFAULT_TEMPLATE = "Ôn lại nội dung: {label}."


def _recommendation_for(item: MistakeHistoryItem) -> str:
    template = _RECOMMENDATION_TEMPLATES.get(item.category, _DEFAULT_TEMPLATE)
    return template.format(label=item.label)


class RuleBasedAnalyzer(MistakeAnalyzer):
    """Frequency + recency based scoring - no LLM/API cost."""

    def __init__(self, top_n: int = 10) -> None:
        self._top_n = top_n

    def analyze(self, segments: list[Segment], history: list[MistakeHistoryItem]) -> list[WeakPoint]:
        session_text = " ".join(seg.text for seg in segments)

        weak_points = []
        for item in history:
            recurs = _mentions_label(session_text, item.label)
            score = _forgetting_score(item, recurs)
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
