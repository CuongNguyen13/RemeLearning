from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    kafka_enabled: bool = False
    kafka_bootstrap_servers: str = "localhost:9092"
    kafka_consumer_group: str = "ai-service"

    redis_host: str = "localhost"
    redis_port: int = 6379

    s3_region: str = "ap-southeast-1"
    s3_endpoint: str = ""
    s3_access_key: str = ""
    s3_secret_key: str = ""

    # Which MistakeAnalyzer ranks recurring mistakes on the learning.gap.analysis.requested path:
    # "rule-based" (legacy occurrence_count x forgetting) or "scoring-engine" (the composite formula
    # matching english-service's Java WeakPointScoringEngine, so both flows produce consistent scores).
    analysis_scorer: str = "rule-based"

    whisper_model_size: str = "small"
    device: str = "cpu"
    hf_token: str = ""
    stt_max_concurrent_transcriptions: int = 4

    vision_enabled: bool = False
    vision_frame_interval_seconds: float = 10.0
    gemini_api_key: str = ""
    gemini_vision_model: str = "gemini-2.0-flash"

    # Face recognition (app/face/) - identifies which enrolled person is speaking by matching
    # faces sampled from each diarized speaker turn against photos fetched from user-service.
    face_recognition_enabled: bool = False
    face_match_similarity_threshold: float = 0.45
    face_frames_per_turn: int = 3
    user_service_base_url: str = "http://localhost:8081"

    # Voice authenticity (app/voice_auth/) - heuristic human-vs-synthetic voice classifier,
    # useful for recordings of Teams/Meet sessions where a TTS bot may be a participant.
    voice_authenticity_enabled: bool = False

    # Text-to-speech (app/tts/) - on-device Supertonic model (ONNX, CPU, 44.1kHz). Used by
    # english-service's dictation AI-practice section to voice Gemini-suggested practice sentences.
    # The model is loaded lazily on the first /api/v1/tts/synthesize call, so leaving this true
    # costs nothing until TTS is actually requested.
    tts_enabled: bool = True
    tts_default_voice: str = "F1"
    tts_default_lang: str = "en"

    # ai-service's own Postgres database (reme_ai), schema "ai" - kept separate from the
    # default "public" schema per-service convention every Java service uses. Env var names
    # for the credentials intentionally match the Java services' (DB_USERNAME/DB_PASSWORD) so
    # both sides share the same docker-compose network/env without translation.
    ai_db_host: str = "localhost"
    ai_db_port: int = 5432
    ai_db_name: str = "reme_ai"
    ai_db_schema: str = "ai"
    db_username: str = "postgres"
    db_password: str = "postgres"

    # Speaking/pronunciation GOP scoring (app/pronunciation/) - defaults off, same pattern as
    # KAFKA_ENABLED/vision_enabled: loading the wav2vec2 acoustic model costs real memory/CPU that
    # shouldn't be paid on every ai-service instance until the feature is actually used (see
    # project memory on this machine's tight free-RAM headroom).
    pronunciation_enabled: bool = False
    pronunciation_model: str = "facebook/wav2vec2-lv-60-espeak-cv-ft"

    api_host: str = "0.0.0.0"
    api_port: int = 8000

    @property
    def ai_database_url(self) -> str:
        return (
            f"postgresql+psycopg2://{self.db_username}:{self.db_password}"
            f"@{self.ai_db_host}:{self.ai_db_port}/{self.ai_db_name}"
        )


settings = Settings()
