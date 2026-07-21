import wave

from app.stt.audio_convert import wav_duration_seconds
from app.stt.base import RawSegment, SpeakerTurn, SpeechToTextEngine
from app.stt.pipeline import UNKNOWN_SPEAKER, transcribe_turns_multilingual


def _write_silence_wav(path, duration_seconds, frame_rate=16000):
    frame_count = int(duration_seconds * frame_rate)
    with wave.open(str(path), "wb") as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(frame_rate)
        wav_file.writeframes(b"\x00\x00" * frame_count)


class FakeWhisperEngine(SpeechToTextEngine):
    """Test double keyed by clip duration (rather than call order), since turns run concurrently
    and complete in an unpredictable order. Mirrors FasterWhisperEngine's real contract: an
    explicit language_code forces that language; None reports whatever was "detected"."""

    def __init__(self, responses_by_duration_seconds):
        self._responses_by_duration_seconds = responses_by_duration_seconds

    def transcribe(self, audio_path, language_code="en"):
        raise NotImplementedError

    def transcribe_auto(self, audio_path, language_code):
        duration = round(wav_duration_seconds(audio_path))
        text, detected_language = self._responses_by_duration_seconds[duration]
        segment = RawSegment(text=text, start_seconds=0.0, end_seconds=duration)
        return [segment], (language_code or detected_language)

    def transcribe_words(self, audio_path, language_code=None):
        raise NotImplementedError


def test_transcribes_each_speaker_turn_in_its_own_detected_language(tmp_path):
    wav_path = tmp_path / "recording.wav"
    _write_silence_wav(wav_path, duration_seconds=5.0)
    turns = [
        SpeakerTurn(speaker="SPEAKER_00", start_seconds=0.0, end_seconds=2.0),
        SpeakerTurn(speaker="SPEAKER_01", start_seconds=2.0, end_seconds=5.0),
    ]
    engine = FakeWhisperEngine({2: ("Hello there", "en"), 3: ("Xin chao", "vi")})

    result = transcribe_turns_multilingual(str(wav_path), engine, turns, language_code=None, max_workers=2)

    by_speaker = {seg.speaker: seg for seg in result.segments}
    assert by_speaker["SPEAKER_00"].text == "Hello there"
    assert by_speaker["SPEAKER_00"].language == "en"
    assert by_speaker["SPEAKER_01"].text == "Xin chao"
    assert by_speaker["SPEAKER_01"].language == "vi"
    assert result.full_text == "Hello there Xin chao"


def test_segment_timestamps_are_offset_by_turn_start(tmp_path):
    wav_path = tmp_path / "recording.wav"
    _write_silence_wav(wav_path, duration_seconds=5.0)
    turns = [SpeakerTurn(speaker="SPEAKER_00", start_seconds=3.0, end_seconds=5.0)]
    engine = FakeWhisperEngine({2: ("Hello there", "en")})

    result = transcribe_turns_multilingual(str(wav_path), engine, turns, language_code=None)

    assert result.segments[0].start_seconds == 3.0
    assert result.segments[0].end_seconds == 5.0


def test_explicit_language_code_forces_same_language_across_turns(tmp_path):
    wav_path = tmp_path / "recording.wav"
    _write_silence_wav(wav_path, duration_seconds=4.0)
    turns = [
        SpeakerTurn(speaker="SPEAKER_00", start_seconds=0.0, end_seconds=1.0),
        SpeakerTurn(speaker="SPEAKER_01", start_seconds=1.0, end_seconds=4.0),
    ]
    # "detected" languages are "vi" for both, but a forced language_code should win.
    engine = FakeWhisperEngine({1: ("Bonjour", "vi"), 3: ("Salut", "vi")})

    result = transcribe_turns_multilingual(str(wav_path), engine, turns, language_code="fr", max_workers=2)

    assert all(seg.language == "fr" for seg in result.segments)


def test_falls_back_to_whole_file_as_single_turn_when_no_speech_detected(tmp_path):
    wav_path = tmp_path / "recording.wav"
    _write_silence_wav(wav_path, duration_seconds=4.0)
    engine = FakeWhisperEngine({4: ("Some speech", "en")})

    result = transcribe_turns_multilingual(str(wav_path), engine, turns=[], language_code=None)

    assert len(result.segments) == 1
    assert result.segments[0].speaker == UNKNOWN_SPEAKER
    assert result.segments[0].language == "en"
