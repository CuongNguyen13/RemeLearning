from app.analysis.rule_based_analyzer import RuleBasedAnalyzer
from app.schemas.events import MistakeHistoryItem, Segment


def _history_item(**overrides) -> MistakeHistoryItem:
    defaults = dict(
        item_id="1",
        category="grammar",
        label="past perfect tense",
        occurrence_count=3,
        last_seen_days_ago=10.0,
    )
    defaults.update(overrides)
    return MistakeHistoryItem(**defaults)


def test_ranks_higher_occurrence_and_older_last_seen_first():
    weak = _history_item(item_id="weak", occurrence_count=5, last_seen_days_ago=20.0)
    strong = _history_item(item_id="strong", occurrence_count=1, last_seen_days_ago=0.5)

    result = RuleBasedAnalyzer().analyze(segments=[], history=[strong, weak])

    assert [wp.item_id for wp in result] == ["weak", "strong"]


def test_recurrence_in_session_boosts_score():
    item = _history_item(item_id="1", label="vocabulary: reluctant")
    segments_with_mention = [Segment(speaker="s1", text="I was reluctant to go", start_seconds=0, end_seconds=2)]

    without_mention = RuleBasedAnalyzer().analyze(segments=[], history=[item])[0]
    with_mention = RuleBasedAnalyzer().analyze(segments=segments_with_mention, history=[item])[0]

    assert with_mention.forgetting_score > without_mention.forgetting_score


def test_respects_top_n():
    history = [_history_item(item_id=str(i)) for i in range(5)]

    result = RuleBasedAnalyzer(top_n=2).analyze(segments=[], history=history)

    assert len(result) == 2
