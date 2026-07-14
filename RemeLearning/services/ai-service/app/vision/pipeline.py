from app.schemas.events import Segment, TranscriptionResult
from app.vision.base import FrameCaption

VISION_SPEAKER = "vision"


def build_caption_segments(captions: list[FrameCaption]) -> list[Segment]:
    """Wraps each frame caption as a Segment so it flows through the same MistakeAnalyzer
    text-matching logic as spoken-word segments, tagged with a distinct speaker label."""
    return [
        Segment(
            speaker=VISION_SPEAKER,
            text=caption.caption,
            start_seconds=caption.timestamp_seconds,
            end_seconds=caption.timestamp_seconds,
        )
        for caption in captions
        if caption.caption
    ]


def merge_caption_segments(result: TranscriptionResult, caption_segments: list[Segment]) -> TranscriptionResult:
    """Merges visual-frame captions into an existing transcript, ordered by timestamp, so
    vocabulary/mistake analysis can draw on both spoken and on-screen visual content."""
    if not caption_segments:
        return result

    merged = sorted(result.segments + caption_segments, key=lambda seg: seg.start_seconds)
    full_text = " ".join(seg.text for seg in merged)
    return TranscriptionResult(full_text=full_text, segments=merged)
