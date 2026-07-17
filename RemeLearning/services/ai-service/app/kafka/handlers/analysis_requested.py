from app.analysis.base import MistakeAnalyzer
from app.analysis.factory import create_analyzer
from app.kafka import topics
from app.kafka.producer import event_producer
from app.schemas.events import AnalysisRequestedEvent, LearningGapAnalyzedEvent

# Chosen from config (rule-based | scoring-engine); swappable for an LLM-backed analyzer later.
_analyzer: MistakeAnalyzer = create_analyzer()


async def handle_analysis_requested(payload: dict) -> None:
    event = AnalysisRequestedEvent.model_validate(payload)
    weak_points = _analyzer.analyze(event.segments, event.history)

    analyzed_event = LearningGapAnalyzedEvent(
        recording_id=event.recording_id,
        user_id=event.user_id,
        weak_points=weak_points,
    )
    await event_producer.publish(topics.LEARNING_GAP_ANALYZED, key=event.recording_id, event=analyzed_event)
