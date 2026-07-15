import librosa
import numpy as np

from app.voice_auth.base import HUMAN, SYNTHETIC, UNCERTAIN, VoiceAuthenticityAnalyzer, VoiceAuthenticityResult

# All thresholds below are illustrative, hand-picked starting points, not the output of any
# training/calibration process - tune them against labeled real-vs-synthetic samples before
# relying on this in production. See the module docstring for why this exists as a heuristic
# rather than a trained model.
_MIN_VOICED_FRAMES = 5
_JITTER_HUMAN_FLOOR = 0.006  # relative frame-to-frame F0 variation below this reads as "too smooth"
_SHIMMER_HUMAN_FLOOR = 0.02  # relative frame-to-frame RMS variation below this reads as "too smooth"
_SPECTRAL_FLATNESS_SYNTHETIC_CEILING = 0.35  # unnaturally tonal/flat spectrum above this
_PAUSE_CV_HUMAN_FLOOR = 0.15  # coefficient of variation of pause lengths below this reads as "too regular"


class HeuristicVoiceAuthenticityAnalyzer(VoiceAuthenticityAnalyzer):
    """Distinguishes a real human voice from a machine/synthetic one (TTS bot, looped
    playback) using plain acoustic features - NOT a trained classifier. Real speech has
    natural micro-irregularities a simple heuristic can pick up on: pitch jitter, amplitude
    shimmer, and irregular pause timing. Older/simpler TTS and looped audio tend to be
    unnaturally smooth and regular on these same measures. This will misclassify
    high-quality modern TTS (which deliberately reintroduces these irregularities) - treat
    the output as a weak signal, not ground truth, and swap in a trained anti-spoofing model
    behind VoiceAuthenticityAnalyzer once one is available.
    """

    def analyze(self, wav_path: str) -> VoiceAuthenticityResult:
        y, sr = librosa.load(wav_path, sr=None, mono=True)
        if len(y) < sr * 0.5:
            return VoiceAuthenticityResult(label=UNCERTAIN, confidence=0.0, features={"reason_too_short": 1.0})

        f0, voiced_flag, _voiced_probs = librosa.pyin(
            y, fmin=librosa.note_to_hz("C2"), fmax=librosa.note_to_hz("C7"), sr=sr
        )
        voiced_f0 = f0[voiced_flag & ~np.isnan(f0)]
        if len(voiced_f0) < _MIN_VOICED_FRAMES:
            return VoiceAuthenticityResult(label=UNCERTAIN, confidence=0.0, features={"reason_no_voiced_frames": 1.0})

        jitter = self._relative_frame_to_frame_variation(voiced_f0)

        rms = librosa.feature.rms(y=y)[0]
        shimmer = self._relative_frame_to_frame_variation(rms[rms > 0])

        spectral_flatness = float(np.mean(librosa.feature.spectral_flatness(y=y)))

        pause_cv = self._pause_length_coefficient_of_variation(voiced_flag, sr, hop_length=512)

        features = {
            "jitter": jitter,
            "shimmer": shimmer,
            "spectral_flatness": spectral_flatness,
            "pause_length_cv": pause_cv,
        }

        # Each signal casts one "looks human" vote; average them into a 0-1 human-likeness score.
        votes = [
            jitter >= _JITTER_HUMAN_FLOOR,
            shimmer >= _SHIMMER_HUMAN_FLOOR,
            spectral_flatness <= _SPECTRAL_FLATNESS_SYNTHETIC_CEILING,
            pause_cv >= _PAUSE_CV_HUMAN_FLOOR,
        ]
        human_likeness = sum(votes) / len(votes)

        if human_likeness >= 0.75:
            label = HUMAN
        elif human_likeness <= 0.25:
            label = SYNTHETIC
        else:
            label = UNCERTAIN
        confidence = abs(human_likeness - 0.5) * 2
        return VoiceAuthenticityResult(label=label, confidence=confidence, features=features)

    @staticmethod
    def _relative_frame_to_frame_variation(values: np.ndarray) -> float:
        """Mean absolute frame-to-frame difference, normalized by the mean value - a
        jitter/shimmer-style measure of how much a signal wobbles frame to frame."""
        if len(values) < 2 or np.mean(values) == 0:
            return 0.0
        diffs = np.abs(np.diff(values))
        return float(np.mean(diffs) / np.mean(values))

    @staticmethod
    def _pause_length_coefficient_of_variation(voiced_flag: np.ndarray, sr: int, hop_length: int) -> float:
        """Coefficient of variation (std/mean) of consecutive unvoiced-run lengths. Natural
        speech pauses vary in length; a suspiciously uniform pause pattern (low CV) can
        indicate synthetic or looped audio."""
        frame_seconds = hop_length / sr
        pause_lengths = []
        current_run = 0
        for is_voiced in voiced_flag:
            if not is_voiced:
                current_run += 1
            elif current_run > 0:
                pause_lengths.append(current_run * frame_seconds)
                current_run = 0
        if current_run > 0:
            pause_lengths.append(current_run * frame_seconds)

        if len(pause_lengths) < 2 or np.mean(pause_lengths) == 0:
            return 0.0
        return float(np.std(pause_lengths) / np.mean(pause_lengths))
