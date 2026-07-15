import tempfile
import wave

import av

TARGET_SAMPLE_RATE = 16000
SAMPLE_WIDTH_BYTES = 2


def convert_to_wav(input_path: str) -> str:
    """Extracts the audio track from any container (mp4, mkv, mp3, ...) faster-whisper's
    internal decoder can read, and writes it as 16kHz mono PCM WAV.

    pyannote.audio's diarization pipeline loads files via torchaudio's soundfile backend,
    which (unlike faster-whisper) can only read raw audio formats - not video containers.
    """
    output_path = tempfile.NamedTemporaryFile(delete=False, suffix=".wav").name

    with av.open(input_path) as container:
        stream = container.streams.audio[0]
        resampler = av.audio.resampler.AudioResampler(format="s16", layout="mono", rate=TARGET_SAMPLE_RATE)

        with wave.open(output_path, "wb") as wav_file:
            wav_file.setnchannels(1)
            wav_file.setsampwidth(2)
            wav_file.setframerate(TARGET_SAMPLE_RATE)

            for packet in container.demux(stream):
                for frame in packet.decode():
                    for resampled in resampler.resample(frame):
                        wav_file.writeframes(resampled.to_ndarray().tobytes())

    return output_path


def wav_duration_seconds(wav_path: str) -> float:
    """Length of a 16kHz mono PCM WAV file produced by convert_to_wav/slice_wav."""
    with wave.open(wav_path, "rb") as wav_file:
        return wav_file.getnframes() / wav_file.getframerate()


def slice_wav(wav_path: str, start_seconds: float, end_seconds: float) -> str:
    """Extracts [start_seconds, end_seconds) from a 16kHz mono PCM WAV into a new temp WAV file.

    Used to split a recording into per-speaker-turn clips (from diarization) so each turn can be
    transcribed independently - and, since each turn's language is auto-detected on its own, in
    parallel across turns spoken in different languages.
    """
    output_path = tempfile.NamedTemporaryFile(delete=False, suffix=".wav").name

    with wave.open(wav_path, "rb") as src:
        frame_rate = src.getframerate()
        start_frame = max(0, int(start_seconds * frame_rate))
        frame_count = max(0, int(end_seconds * frame_rate) - start_frame)
        src.setpos(start_frame)
        frames = src.readframes(frame_count)

        with wave.open(output_path, "wb") as dst:
            dst.setnchannels(1)
            dst.setsampwidth(SAMPLE_WIDTH_BYTES)
            dst.setframerate(TARGET_SAMPLE_RATE)
            dst.writeframes(frames)

    return output_path
