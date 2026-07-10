from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.routes import router
from app.config import settings
from app.kafka import topics
from app.kafka.consumer import spawn_consumer_task
from app.kafka.handlers.analysis_requested import handle_analysis_requested
from app.kafka.handlers.recording_uploaded import handle_recording_uploaded
from app.kafka.producer import event_producer


@asynccontextmanager
async def lifespan(app: FastAPI):
    if not settings.kafka_enabled:
        # KAFKA_ENABLED=false: skip Kafka entirely, only the REST endpoints
        # (/api/v1/upload, /api/v1/transcribe, /api/v1/analyze) are available.
        yield
        return

    await event_producer.start()
    consumer_tasks = [
        spawn_consumer_task(topics.RECORDING_UPLOADED, handle_recording_uploaded),
        spawn_consumer_task(topics.LEARNING_GAP_ANALYSIS_REQUESTED, handle_analysis_requested),
    ]
    try:
        yield
    finally:
        for task in consumer_tasks:
            task.cancel()
        await event_producer.stop()


app = FastAPI(title="ai-service", lifespan=lifespan)
app.include_router(router)

if __name__ == "__main__":
    import uvicorn

    # Pass the app object directly (not the "app.main:app" import string) so this also works
    # when launched as `python app/main.py` from an IDE, where sys.path[0] is app/'s own
    # directory rather than ai-service/ - an import-string re-import would fail with
    # "No module named 'app'" in that case. This means --reload isn't available here;
    # use `uvicorn app.main:app --reload` from the ai-service/ directory for reload support.
    uvicorn.run(app, host=settings.api_host, port=settings.api_port)
