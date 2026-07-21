import re
from dataclasses import dataclass

from app.stt.base import WordTiming

# Matches word-shaped alphanumeric tokens; used to strip punctuation/case before comparing a
# script word against a word Whisper transcribed, since the two rarely match byte-for-byte.
_WORD_PATTERN = re.compile(r"[a-z0-9']+")

# A sentence match is rejected (start_ms/end_ms left None) unless at least this fraction of its
# tokens were actually found in the ASR timeline, AND the last token was among them - accepting a
# partial match anchored on an earlier word produces a boundary that truncates the clip mid-sentence,
# which is worse than no boundary at all.
_MIN_MATCH_RATIO = 0.8

# faster-whisper's per-word timestamps come from decoder cross-attention, not forced alignment, and
# are routinely tight/clipped at phoneme boundaries - widen each side by this much so playback
# doesn't audibly cut the first/last sound of the sentence.
_PADDING_MS = 100

# A word is only trusted as a start/end anchor (or as a mid-sentence match) if faster-whisper is at
# least this confident it transcribed the word correctly. Below this, the timestamp is more likely
# to belong to the wrong token entirely.
_MIN_WORD_CONFIDENCE = 0.35

# Two tokens of at least this length are treated as the same word if their edit distance is at most
# _MAX_FUZZY_EDIT_DISTANCE - tolerates Whisper mis-transcribing a single letter of an otherwise
# correctly-heard, in-vocabulary word. Left exact below this length since short words (2-3 letters)
# have too little room for a 1-edit fuzzy match to still mean "the same word".
_MIN_FUZZY_TOKEN_LENGTH = 4
_MAX_FUZZY_EDIT_DISTANCE = 1


@dataclass
class SentenceTiming:
    start_ms: int | None
    end_ms: int | None


def _tokenize(text: str) -> list[str]:
    """Splits text into lower-cased, punctuation-stripped word tokens for loose comparison."""
    return _WORD_PATTERN.findall(text.lower())


def _levenshtein(a: str, b: str) -> int:
    """Standard edit distance between two strings, used only for the short fuzzy-match check below."""
    previous_row = list(range(len(b) + 1))
    for i, char_a in enumerate(a, start=1):
        current_row = [i]
        for j, char_b in enumerate(b, start=1):
            insert_cost = current_row[j - 1] + 1
            delete_cost = previous_row[j] + 1
            replace_cost = previous_row[j - 1] + (char_a != char_b)
            current_row.append(min(insert_cost, delete_cost, replace_cost))
        previous_row = current_row
    return previous_row[-1]


def _tokens_match(script_token: str, asr_token: str) -> bool:
    """Exact match, or - for longer words only - a 1-edit fuzzy match."""
    if script_token == asr_token:
        return True
    if len(script_token) < _MIN_FUZZY_TOKEN_LENGTH or len(asr_token) < _MIN_FUZZY_TOKEN_LENGTH:
        return False
    return _levenshtein(script_token, asr_token) <= _MAX_FUZZY_EDIT_DISTANCE


def _word_token(word: WordTiming) -> str | None:
    """The word's single token, or None if it tokenizes to zero/multiple tokens (e.g. punctuation-only)."""
    tokens = _tokenize(word.word)
    return tokens[0] if len(tokens) == 1 else None


def _is_confident(word: WordTiming) -> bool:
    return word.probability >= _MIN_WORD_CONFIDENCE


def align_sentences(sentences: list[str], words: list[WordTiming]) -> list[SentenceTiming]:
    """Matches each script sentence, in order, against Whisper's word-level transcription timeline.

    For each sentence, finds its first token's next confident occurrence at or after the running
    cursor, then walks forward from there fuzzy-matching the sentence's remaining tokens in order
    (tolerating extra/misheard words in between). The match is accepted only if the sentence's last
    token was reached and at least _MIN_MATCH_RATIO of its tokens matched overall - a partial match
    is rejected rather than persisted as a truncated boundary. Accepted boundaries are padded by
    _PADDING_MS on each side (clamped so they never reach into a neighboring word's span) since
    Whisper's own word timestamps are frequently clipped at phoneme boundaries.

    A rejected sentence (first token never found, or too little of it matched) is left unmatched
    (None start/end) rather than guessing - the caller can retry alignment later instead of
    persisting a wrong timestamp. The cursor only advances past sentences that were matched.
    """
    timings: list[SentenceTiming] = []
    cursor = 0
    for sentence in sentences:
        tokens = _tokenize(sentence)
        if not tokens:
            timings.append(SentenceTiming(start_ms=None, end_ms=None))
            continue

        match = _match_sentence(words, tokens, cursor)
        if match is None:
            timings.append(SentenceTiming(start_ms=None, end_ms=None))
            continue

        start_index, end_index = match
        start_ms = round(words[start_index].start_seconds * 1000)
        end_ms = round(words[end_index].end_seconds * 1000)
        timings.append(SentenceTiming(
            start_ms=_pad_start(start_ms, words, start_index),
            end_ms=_pad_end(end_ms, words, end_index),
        ))
        cursor = end_index + 1
    return timings


def _match_sentence(words: list[WordTiming], tokens: list[str], cursor: int) -> tuple[int, int] | None:
    """Finds a (start_index, end_index) word-span for `tokens`, or None if the match doesn't meet
    the minimum-quality bar (see _MIN_MATCH_RATIO)."""
    start_index = _find_token(words, tokens[0], cursor)
    if start_index is None:
        return None

    end_index, matched_count = _consume_tokens(words, tokens, start_index)
    reached_last_token = matched_count == len(tokens)
    if not reached_last_token or matched_count / len(tokens) < _MIN_MATCH_RATIO:
        return None
    return start_index, end_index


def _find_token(words: list[WordTiming], token: str, from_index: int) -> int | None:
    """First index at/after from_index whose word confidently fuzzy-matches `token`."""
    for index in range(from_index, len(words)):
        word_token = _word_token(words[index])
        if word_token is not None and _is_confident(words[index]) and _tokens_match(token, word_token):
            return index
    return None


def _consume_tokens(words: list[WordTiming], tokens: list[str], start_index: int) -> tuple[int, int]:
    """Walks forward from start_index, fuzzy-matching `tokens` in order against the words stream
    (skipping non-matching or low-confidence words in between). Returns the index of the last
    matched word and how many of `tokens` were matched in total (tokens[0] counts as matched at
    start_index)."""
    token_index = 1
    last_matched = start_index
    for word_index in range(start_index + 1, len(words)):
        if token_index >= len(tokens):
            break
        word_token = _word_token(words[word_index])
        if word_token is None or not _is_confident(words[word_index]):
            continue
        if _tokens_match(tokens[token_index], word_token):
            last_matched = word_index
            token_index += 1
    return last_matched, token_index


def _pad_start(start_ms: int, words: list[WordTiming], start_index: int) -> int:
    """Widens the start boundary by _PADDING_MS, clamped so it never reaches back into the
    previous word's span (which likely belongs to an earlier sentence) or below zero."""
    floor_ms = round(words[start_index - 1].end_seconds * 1000) if start_index > 0 else 0
    return max(start_ms - _PADDING_MS, floor_ms, 0)


def _pad_end(end_ms: int, words: list[WordTiming], end_index: int) -> int:
    """Widens the end boundary by _PADDING_MS, clamped so it never reaches into the next word's
    span (which may belong to the next sentence once matched)."""
    if end_index + 1 < len(words):
        ceiling_ms = round(words[end_index + 1].start_seconds * 1000)
    else:
        ceiling_ms = end_ms + _PADDING_MS
    return min(end_ms + _PADDING_MS, ceiling_ms)
