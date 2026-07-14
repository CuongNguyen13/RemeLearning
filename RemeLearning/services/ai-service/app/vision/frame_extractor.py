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
