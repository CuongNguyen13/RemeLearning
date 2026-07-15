import os
from collections import Counter

from app.face.base import EnrolledFace, FaceMatch, FaceRecognitionEngine
from app.face.enrollment import best_match
from app.stt.base import SpeakerTurn
from app.vision.frame_extractor import extract_frames_in_range

# NOTE on accuracy: this identifies "who is on screen most often while SPEAKER_XX is talking",
# not true active-speaker detection (no lip-sync/mouth-motion analysis). It works well for
# recordings where the visible face genuinely corresponds to whoever is speaking (e.g.
# single-face/speaker-view layouts). In a multi-face gallery-view recording where every
# participant's tile is visible regardless of who's talking, this heuristic is much weaker -
# every speaker turn will tend to see the same set of faces. Documented here rather than
# quietly overclaiming accuracy.


def identify_speakers_by_face(
    video_path: str,
    turns: list[SpeakerTurn],
    engine: FaceRecognitionEngine,
    known_faces: list[EnrolledFace],
    similarity_threshold: float,
    frames_per_turn: int,
) -> dict[str, FaceMatch]:
    """For each diarized speaker turn, samples a few video frames within its time range,
    detects faces, and matches each against the enrolled known_faces list. Returns the most
    frequent above-threshold match per speaker label - speakers with no matching enrolled
    face simply have no entry (unidentified)."""
    matches_by_speaker: dict[str, FaceMatch] = {}

    for turn in turns:
        if turn.speaker in matches_by_speaker:
            continue

        frames = extract_frames_in_range(video_path, turn.start_seconds, turn.end_seconds, frames_per_turn)
        try:
            candidate_matches = []
            for frame in frames:
                for detected_face in engine.detect_faces(frame.image_path):
                    match = best_match(detected_face.embedding, known_faces, similarity_threshold)
                    if match is not None:
                        candidate_matches.append(match)
        finally:
            for frame in frames:
                os.remove(frame.image_path)

        if not candidate_matches:
            continue

        # Majority vote across sampled frames, keyed by user_id; ties broken by highest
        # single-frame similarity seen for that user.
        counts = Counter(match.user_id for match in candidate_matches)
        winning_user_id = max(counts, key=lambda uid: (counts[uid], max(m.similarity for m in candidate_matches if m.user_id == uid)))
        best_for_winner = max((m for m in candidate_matches if m.user_id == winning_user_id), key=lambda m: m.similarity)
        matches_by_speaker[turn.speaker] = best_for_winner

    return matches_by_speaker
