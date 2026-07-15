import wave

from app.stt.audio_convert import slice_wav, wav_duration_seconds


def _write_tone_wav(path, duration_seconds, frame_rate=16000):
    frame_count = int(duration_seconds * frame_rate)
    with wave.open(str(path), "wb") as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(frame_rate)
        # distinct sample value per frame index so slices can be checked byte-for-byte
        wav_file.writeframes(b"".join(i.to_bytes(2, "little", signed=False) for i in range(frame_count)))


def test_wav_duration_seconds_matches_written_length(tmp_path):
    wav_path = tmp_path / "full.wav"
    _write_tone_wav(wav_path, duration_seconds=3.0)

    assert wav_duration_seconds(str(wav_path)) == 3.0


def test_slice_wav_extracts_requested_range(tmp_path):
    wav_path = tmp_path / "full.wav"
    _write_tone_wav(wav_path, duration_seconds=4.0, frame_rate=16000)

    sliced_path = slice_wav(str(wav_path), start_seconds=1.0, end_seconds=2.0)

    assert wav_duration_seconds(sliced_path) == 1.0
    with wave.open(sliced_path, "rb") as sliced:
        first_frame_value = int.from_bytes(sliced.readframes(1), "little", signed=False)
    assert first_frame_value == 16000  # frame index at t=1.0s with a 16kHz rate
