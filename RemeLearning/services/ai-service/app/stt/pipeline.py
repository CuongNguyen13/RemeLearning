from app.schemas.events import Segment, TranscriptionResult
from app.stt.base import RawSegment, SpeakerTurn

UNKNOWN_SPEAKER = "unknown"


def _overlap_seconds(a_start: float, a_end: float, b_start: float, b_end: float) -> float:
    return max(0.0, min(a_end, b_end) - max(a_start, b_start))


def assign_speakers(segments: list[RawSegment], turns: list[SpeakerTurn]) -> list[Segment]:
    """Labels each whisper segment with the speaker turn it overlaps the most."""
    labeled: list[Segment] = []
    for seg in segments:
        best_speaker = UNKNOWN_SPEAKER
        best_overlap = 0.0
        for turn in turns:
            overlap = _overlap_seconds(seg.start_seconds, seg.end_seconds, turn.start_seconds, turn.end_seconds)
            if overlap > best_overlap:
                best_overlap = overlap
                best_speaker = turn.speaker
        labeled.append(
            Segment(
                speaker=best_speaker,
                text=seg.text,
                start_seconds=seg.start_seconds,
                end_seconds=seg.end_seconds,
            )
        )
    return labeled


def build_transcription_result(segments: list[RawSegment], turns: list[SpeakerTurn]) -> TranscriptionResult:
    labeled_segments = assign_speakers(segments, turns)
    full_text = " ".join(seg.text for seg in labeled_segments)
    return TranscriptionResult(full_text=full_text, segments=labeled_segments)
