import json

from aiokafka import AIOKafkaProducer
from pydantic import BaseModel

from app.config import settings


class EventProducer:
    def __init__(self) -> None:
        self._producer: AIOKafkaProducer | None = None

    async def start(self) -> None:
        self._producer = AIOKafkaProducer(
            bootstrap_servers=settings.kafka_bootstrap_servers,
            value_serializer=lambda v: json.dumps(v).encode("utf-8"),
        )
        await self._producer.start()

    async def stop(self) -> None:
        if self._producer is not None:
            await self._producer.stop()

    async def publish(self, topic: str, key: str, event: BaseModel) -> None:
        assert self._producer is not None, "EventProducer.start() must be called first"
        await self._producer.send_and_wait(topic, value=event.model_dump(), key=key.encode("utf-8"))


event_producer = EventProducer()
