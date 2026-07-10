from app.analysis.base import MistakeAnalyzer
from app.analysis.rule_based_analyzer import RuleBasedAnalyzer
from app.kafka import topics
from app.kafka.producer import event_producer
from app.schemas.events import AnalysisRequestedEvent, LearningGapAnalyzedEvent

# Swappable: replace with an LLM-backed MistakeAnalyzer later without touching this handler.
_analyzer: MistakeAnalyzer = RuleBasedAnalyzer()


async def handle_analysis_requested(payload: dict) -> None:
    event = AnalysisRequestedEvent.model_validate(payload)
    weak_points = _analyzer.analyze(event.segments, event.history)

    analyzed_event = LearningGapAnalyzedEvent(
        recording_id=event.recording_id,
        user_id=event.user_id,
        weak_points=weak_points,
    )
    await event_producer.publish(topics.LEARNING_GAP_ANALYZED, key=event.recording_id, event=analyzed_event)
