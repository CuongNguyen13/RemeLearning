from pydantic import BaseModel


class SentenceTimingResponse(BaseModel):
    """One script sentence's aligned audio timestamps, or nulls if it couldn't be located
    (see app/align/sentence_aligner.py) - the caller (english-service) leaves that sentence
    unaligned and may retry later rather than persisting a guessed timestamp."""

    start_ms: int | None = None
    end_ms: int | None = None
