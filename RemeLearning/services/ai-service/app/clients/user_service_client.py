from dataclasses import dataclass

import httpx

from app.config import settings


@dataclass
class UserProfile:
    user_id: str
    name: str
    photo_url: str | None


class UserServiceNotFoundError(Exception):
    pass


class UserServiceClient:
    """Talks to user-service's REST API to resolve a userId to its public profile - in
    particular photoUrl, the reference photo face enrollment matches faces against. Mirrors
    the shape of app/storage/s3_client.py: a thin client module, not a class hierarchy,
    since there's only ever one backing implementation (unlike VisionCaptionEngine/
    FaceRecognitionEngine, which are genuinely swappable)."""

    def __init__(self) -> None:
        self._client = httpx.Client(base_url=settings.user_service_base_url, timeout=10.0)

    def get_user(self, user_id: str) -> UserProfile:
        response = self._client.get(f"/api/v1/users/{user_id}")
        if response.status_code == 404:
            raise UserServiceNotFoundError(f"user-service has no user for userId={user_id}")
        response.raise_for_status()

        data = response.json()["data"]
        return UserProfile(user_id=data["userId"], name=data["name"], photo_url=data.get("photoUrl"))

    def fetch_photo_bytes(self, photo_url: str) -> bytes:
        response = self._client.get(photo_url)
        response.raise_for_status()
        return response.content


user_service_client = UserServiceClient()
