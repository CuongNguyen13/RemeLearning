import re
from dataclasses import dataclass

from app.stt.base import WordTiming

# Matches word-shaped alphanumeric tokens; used to strip punctuation/case before comparing a
# script word against a word Whisper transcribed, since the two rarely match byte-for-byte.
_WORD_PATTERN = re.compile(r"[a-z0-9']+")


@dataclass
class SentenceTiming:
    start_ms: int | None
    end_ms: int | None


def _tokenize(text: str) -> list[str]:
    """Splits text into lower-cased, punctuation-stripped word tokens for loose comparison."""
    return _WORD_PATTERN.findall(text.lower())


def align_sentences(sentences: list[str], words: list[WordTiming]) -> list[SentenceTiming]:
    """Matches each script sentence, in order, against Whisper's word-level transcription timeline.

    For each sentence, finds its first token's next occurrence at or after the running cursor, then
    walks forward from there consuming words that match the sentence's remaining tokens in order
    (tolerating extra/misheard words in between, since Whisper's own transcription rarely matches
    the script byte-for-byte). The sentence's start_ms/end_ms are the first/last matched word's
    timestamps; the cursor then advances past the last matched word so later sentences can't match
    words already claimed by an earlier one.

    A sentence whose first token never appears in the remaining words (Whisper misheard the whole
    sentence, or this clip's audio doesn't actually cover it) is left unmatched (None start/end)
    rather than guessing - the caller can retry alignment later instead of persisting a wrong
    timestamp.
    """
    timings: list[SentenceTiming] = []
    cursor = 0
    for sentence in sentences:
        tokens = _tokenize(sentence)
        if not tokens:
            timings.append(SentenceTiming(start_ms=None, end_ms=None))
            continue

        start_index = _find_token(words, tokens[0], cursor)
        if start_index is None:
            timings.append(SentenceTiming(start_ms=None, end_ms=None))
            continue

        end_index = _consume_tokens(words, tokens, start_index)
        timings.append(SentenceTiming(
            start_ms=round(words[start_index].start_seconds * 1000),
            end_ms=round(words[end_index].end_seconds * 1000),
        ))
        cursor = end_index + 1
    return timings


def _find_token(words: list[WordTiming], token: str, from_index: int) -> int | None:
    """First index at/after from_index whose word tokenizes to exactly `token`."""
    for index in range(from_index, len(words)):
        if _tokenize(words[index].word) == [token]:
            return index
    return None


def _consume_tokens(words: list[WordTiming], tokens: list[str], start_index: int) -> int:
    """Walks forward from start_index, matching `tokens` in order against the words stream
    (skipping non-matching words in between), and returns the index of the last matched word."""
    token_index = 1  # tokens[0] already matched at start_index
    last_matched = start_index
    for word_index in range(start_index + 1, len(words)):
        if token_index >= len(tokens):
            break
        if _tokenize(words[word_index].word) == [tokens[token_index]]:
            last_matched = word_index
            token_index += 1
    return last_matched
