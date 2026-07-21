from app.align.sentence_aligner import align_sentences
from app.stt.base import WordTiming


def _word(text, start, end, probability=1.0):
    return WordTiming(word=text, start_seconds=start, end_seconds=end, probability=probability)


def test_aligns_each_sentence_to_its_matching_word_span_with_padding():
    words = [
        _word("Hi", 0.0, 0.4), _word("there", 0.4, 0.8),
        _word("Good", 1.0, 1.3), _word("bye", 1.3, 1.7),
    ]

    timings = align_sentences(["Hi there.", "Good bye."], words)

    # end of sentence 1 is padded up to but not past the start of sentence 2's first word (1000ms)
    assert timings[0].start_ms == 0 and timings[0].end_ms == 900
    # start of sentence 2 is padded down to but not before the end of sentence 1's last word (900ms)
    assert timings[1].start_ms == 900 and timings[1].end_ms == 1800


def test_matching_is_case_and_punctuation_insensitive():
    words = [_word("HI,", 0.0, 0.5), _word("THERE!", 0.5, 1.0)]

    timings = align_sentences(["hi there"], words)

    assert timings[0].start_ms == 0
    assert timings[0].end_ms == 1100


def test_advances_cursor_so_sentences_never_reuse_earlier_words():
    words = [_word("go", 0.0, 0.3), _word("go", 0.3, 0.6), _word("go", 0.6, 0.9)]

    timings = align_sentences(["go", "go", "go"], words)

    assert [t.start_ms for t in timings] == [0, 300, 600]
    assert [t.end_ms for t in timings] == [300, 600, 1000]


def test_tolerates_misheard_words_between_matched_tokens():
    # Whisper inserted a stray "um" that isn't part of the script sentence.
    words = [_word("Hi", 0.0, 0.3), _word("um", 0.3, 0.5), _word("there", 0.5, 0.9)]

    timings = align_sentences(["Hi there"], words)

    assert timings[0].start_ms == 0
    assert timings[0].end_ms == 1000


def test_tolerates_single_letter_mistranscription_of_a_long_word():
    # Whisper heard "beatiful" instead of "beautiful" - within 1 edit, so still treated as a match.
    words = [_word("the", 0.0, 0.2), _word("beatiful", 0.2, 0.9)]

    timings = align_sentences(["the beautiful"], words)

    assert timings[0].start_ms == 0
    assert timings[0].end_ms == 1000


def test_rejects_partial_match_instead_of_returning_a_truncated_boundary():
    # Only "hi" is heard; "there" and "friend" (the sentence's last token) never appear, so the
    # match ratio (1/3) is far below threshold and the last token was never reached - previously
    # this produced a boundary anchored on "hi" alone, truncating the clip mid-sentence.
    words = [_word("hi", 0.0, 0.3), _word("something", 0.3, 0.6), _word("else", 0.6, 0.9)]

    timings = align_sentences(["hi there friend"], words)

    assert timings[0].start_ms is None
    assert timings[0].end_ms is None


def test_rejects_match_missing_the_sentence_final_token():
    # "hi there" both heard, but the sentence's last token "friend" never appears - even though
    # 2/3 of tokens matched, the final token must be reached or the boundary is untrustworthy.
    words = [_word("hi", 0.0, 0.3), _word("there", 0.3, 0.6)]

    timings = align_sentences(["hi there friend"], words)

    assert timings[0].start_ms is None
    assert timings[0].end_ms is None


def test_ignores_low_confidence_word_as_a_match_candidate():
    # A low-confidence "hi" should be skipped as an anchor; the real, confident "hi" later on is
    # used instead.
    words = [
        _word("hi", 0.0, 0.2, probability=0.1),
        _word("hi", 1.0, 1.3, probability=0.9),
        _word("there", 1.3, 1.7, probability=0.9),
    ]

    timings = align_sentences(["hi there"], words)

    assert timings[0].start_ms == 900
    assert timings[0].end_ms == 1800


def test_sentence_with_no_matching_words_is_left_unaligned():
    words = [_word("Hello", 0.0, 0.5)]

    timings = align_sentences(["Goodbye"], words)

    assert timings[0].start_ms is None
    assert timings[0].end_ms is None


def test_blank_sentence_is_left_unaligned_without_consuming_a_word():
    words = [_word("Hi", 0.0, 0.5)]

    timings = align_sentences(["   ", "Hi"], words)

    assert timings[0].start_ms is None and timings[0].end_ms is None
    assert timings[1].start_ms == 0 and timings[1].end_ms == 600


def test_empty_word_list_leaves_every_sentence_unaligned():
    timings = align_sentences(["Hi there.", "Bye."], [])

    assert all(t.start_ms is None and t.end_ms is None for t in timings)
