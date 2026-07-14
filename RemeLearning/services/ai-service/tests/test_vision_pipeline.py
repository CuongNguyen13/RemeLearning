from app.schemas.events import Segment, TranscriptionResult
from app.vision.base import FrameCaption
from app.vision.pipeline import build_caption_segments, merge_caption_segments


def test_build_caption_segments_skips_empty_captions():
    captions = [
        FrameCaption(caption="A person writing on a whiteboard", timestamp_seconds=1.0),
        FrameCaption(caption="", timestamp_seconds=2.0),
    ]

    segments = build_caption_segments(captions)

    assert len(segments) == 1
    assert segments[0].speaker == "vision"
    assert segments[0].text == "A person writing on a whiteboard"
    assert segments[0].start_seconds == 1.0


def test_merge_caption_segments_orders_by_timestamp():
    result = TranscriptionResult(
        full_text="Hello there",
        segments=[Segment(speaker="SPEAKER_00", text="Hello there", start_seconds=0.0, end_seconds=2.0)],
    )
    caption_segments = build_caption_segments(
        [FrameCaption(caption="A red car on screen", timestamp_seconds=1.0)]
    )

    merged = merge_caption_segments(result, caption_segments)

    assert [seg.text for seg in merged.segments] == ["Hello there", "A red car on screen"]
    assert merged.full_text == "Hello there A red car on screen"


def test_merge_caption_segments_returns_original_when_no_captions():
    result = TranscriptionResult(
        full_text="Hello there",
        segments=[Segment(speaker="SPEAKER_00", text="Hello there", start_seconds=0.0, end_seconds=2.0)],
    )

    merged = merge_caption_segments(result, [])

    assert merged is result
