import asyncio
import json
import logging
from typing import Awaitable, Callable

from aiokafka import AIOKafkaConsumer

from app.config import settings

logger = logging.getLogger(__name__)


async def consume_forever(topic: str, handler: Callable[[dict], Awaitable[None]]) -> None:
    consumer = AIOKafkaConsumer(
        topic,
        bootstrap_servers=settings.kafka_bootstrap_servers,
        group_id=settings.kafka_consumer_group,
        value_deserializer=lambda v: json.loads(v.decode("utf-8")),
        auto_offset_reset="earliest",
    )
    await consumer.start()
    try:
        async for message in consumer:
            try:
                await handler(message.value)
            except Exception:
                logger.exception("Failed to process message from topic %s: %r", topic, message.value)
    finally:
        await consumer.stop()


def spawn_consumer_task(topic: str, handler: Callable[[dict], Awaitable[None]]) -> asyncio.Task:
    return asyncio.create_task(consume_forever(topic, handler))
