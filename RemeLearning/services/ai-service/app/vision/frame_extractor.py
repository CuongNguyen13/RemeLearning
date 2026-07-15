import tempfile

import av

from app.vision.base import FrameSample


def extract_frames(video_path: str, interval_seconds: float) -> list[FrameSample]:
    """Samples one frame every interval_seconds from the video's first video stream,
    saving each as a temporary JPEG for a VisionCaptionEngine to read. Caller is
    responsible for removing the returned images once captioning is done."""
    frames: list[FrameSample] = []
    with av.open(video_path) as container:
        if not container.streams.video:
            return frames

        stream = container.streams.video[0]
        next_capture_seconds = 0.0
        for frame in container.decode(stream):
            timestamp_seconds = float(frame.pts * stream.time_base)
            if timestamp_seconds < next_capture_seconds:
                continue

            image_path = tempfile.NamedTemporaryFile(delete=False, suffix=".jpg").name
            frame.to_image().save(image_path)
            frames.append(FrameSample(image_path=image_path, timestamp_seconds=timestamp_seconds))
            next_capture_seconds += interval_seconds

    return frames


def extract_frames_in_range(video_path: str, start_seconds: float, end_seconds: float, max_frames: int) -> list[FrameSample]:
    """Samples up to max_frames frames evenly spread across [start_seconds, end_seconds),
    for app/face/pipeline.py to run face detection on a single diarized speaker turn's time
    range. Reuses the same PyAV decode loop as extract_frames rather than a separate
    implementation - caller is responsible for removing the returned images."""
    frames: list[FrameSample] = []
    span = max(end_seconds - start_seconds, 0.0)
    if span == 0.0 or max_frames <= 0:
        return frames

    step = span / max_frames
    next_capture_seconds = start_seconds
    with av.open(video_path) as container:
        if not container.streams.video:
            return frames

        stream = container.streams.video[0]
        for frame in container.decode(stream):
            timestamp_seconds = float(frame.pts * stream.time_base)
            if timestamp_seconds < next_capture_seconds:
                continue
            if timestamp_seconds >= end_seconds or len(frames) >= max_frames:
                break

            image_path = tempfile.NamedTemporaryFile(delete=False, suffix=".jpg").name
            frame.to_image().save(image_path)
            frames.append(FrameSample(image_path=image_path, timestamp_seconds=timestamp_seconds))
            next_capture_seconds += step

    return frames
