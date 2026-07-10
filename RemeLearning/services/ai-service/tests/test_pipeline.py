from app.stt.base import RawSegment, SpeakerTurn
from app.stt.pipeline import build_transcription_result


def test_assigns_speaker_by_max_overlap():
    segments = [
        RawSegment(text="Hello there", start_seconds=0.0, end_seconds=2.0),
        RawSegment(text="Hi, how are you", start_seconds=2.0, end_seconds=4.5),
    ]
    turns = [
        SpeakerTurn(speaker="SPEAKER_00", start_seconds=0.0, end_seconds=2.1),
        SpeakerTurn(speaker="SPEAKER_01", start_seconds=2.0, end_seconds=5.0),
    ]

    result = build_transcription_result(segments, turns)

    assert result.full_text == "Hello there Hi, how are you"
    assert result.segments[0].speaker == "SPEAKER_00"
    assert result.segments[1].speaker == "SPEAKER_01"


def test_unmatched_segment_gets_unknown_speaker():
    segments = [RawSegment(text="...", start_seconds=100.0, end_seconds=102.0)]
    turns = [SpeakerTurn(speaker="SPEAKER_00", start_seconds=0.0, end_seconds=2.0)]

    result = build_transcription_result(segments, turns)

    assert result.segments[0].speaker == "unknown"
