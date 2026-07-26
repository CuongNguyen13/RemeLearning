import io
import wave

import av

from app.stt.audio_convert import encode_to_opus


def _write_silent_wav(path: str, duration_seconds: float = 0.5, frame_rate: int = 16000) -> None:
    frame_count = int(duration_seconds * frame_rate)
    with wave.open(path, "wb") as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(frame_rate)
        wav_file.writeframes(b"\x00\x00" * frame_count)


def test_encode_to_opus_produces_decodable_ogg_opus(tmp_path):
    wav_path = tmp_path / "silent.wav"
    _write_silent_wav(str(wav_path))

    opus_bytes = encode_to_opus(str(wav_path))

    assert opus_bytes
    with av.open(io.BytesIO(opus_bytes)) as container:
        assert container.format.name in ("ogg",)
        stream = container.streams.audio[0]
        assert stream.codec_context.name == "opus"
