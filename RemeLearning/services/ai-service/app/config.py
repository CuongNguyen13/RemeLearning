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

    whisper_model_size: str = "small"
    device: str = "cpu"
    hf_token: str = ""

    api_host: str = "0.0.0.0"
    api_port: int = 8000


settings = Settings()
