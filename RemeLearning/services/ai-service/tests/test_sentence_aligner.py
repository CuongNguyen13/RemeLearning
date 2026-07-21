from app.align.sentence_aligner import align_sentences
from app.stt.base import WordTiming


def _word(text, start, end):
    return WordTiming(word=text, start_seconds=start, end_seconds=end)


def test_aligns_each_sentence_to_its_matching_word_span():
    words = [
        _word("Hi", 0.0, 0.4), _word("there", 0.4, 0.8),
        _word("Good", 1.0, 1.3), _word("bye", 1.3, 1.7),
    ]

    timings = align_sentences(["Hi there.", "Good bye."], words)

    assert timings[0].start_ms == 0 and timings[0].end_ms == 800
    assert timings[1].start_ms == 1000 and timings[1].end_ms == 1700


def test_matching_is_case_and_punctuation_insensitive():
    words = [_word("HI,", 0.0, 0.5), _word("THERE!", 0.5, 1.0)]

    timings = align_sentences(["hi there"], words)

    assert timings[0].start_ms == 0
    assert timings[0].end_ms == 1000


def test_advances_cursor_so_sentences_never_reuse_earlier_words():
    words = [_word("go", 0.0, 0.3), _word("go", 0.3, 0.6), _word("go", 0.6, 0.9)]

    timings = align_sentences(["go", "go", "go"], words)

    assert [t.start_ms for t in timings] == [0, 300, 600]
    assert [t.end_ms for t in timings] == [300, 600, 900]


def test_tolerates_misheard_words_between_matched_tokens():
    # Whisper inserted a stray "um" that isn't part of the script sentence.
    words = [_word("Hi", 0.0, 0.3), _word("um", 0.3, 0.5), _word("there", 0.5, 0.9)]

    timings = align_sentences(["Hi there"], words)

    assert timings[0].start_ms == 0
    assert timings[0].end_ms == 900


def test_sentence_with_no_matching_words_is_left_unaligned():
    words = [_word("Hello", 0.0, 0.5)]

    timings = align_sentences(["Goodbye"], words)

    assert timings[0].start_ms is None
    assert timings[0].end_ms is None


def test_blank_sentence_is_left_unaligned_without_consuming_a_word():
    words = [_word("Hi", 0.0, 0.5)]

    timings = align_sentences(["   ", "Hi"], words)

    assert timings[0].start_ms is None and timings[0].end_ms is None
    assert timings[1].start_ms == 0 and timings[1].end_ms == 500


def test_empty_word_list_leaves_every_sentence_unaligned():
    timings = align_sentences(["Hi there.", "Bye."], [])

    assert all(t.start_ms is None and t.end_ms is None for t in timings)
