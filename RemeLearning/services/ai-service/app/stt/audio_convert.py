import tempfile
import wave

import av

TARGET_SAMPLE_RATE = 16000


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
