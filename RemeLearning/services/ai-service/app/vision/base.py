from abc import ABC, abstractmethod
from dataclasses import dataclass


@dataclass
class FrameSample:
    image_path: str
    timestamp_seconds: float


@dataclass
class FrameCaption:
    caption: str
    timestamp_seconds: float


class VisionCaptionEngine(ABC):
    @abstractmethod
    def caption_frames(self, frames: list[FrameSample]) -> list[FrameCaption]:
        raise NotImplementedError
