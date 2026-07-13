# ai-service

Python AI service for RemeLearning. Responsibilities:

1. Consume `recording.uploaded` (published by `recording-service`), download the audio/video from S3,
   transcribe it (faster-whisper) and diarize speakers (pyannote.audio), publish `transcript.ready`.
2. Consume `learning.gap.analysis.requested` (published by a backend service bundling the transcript with
   the learner's historical mistake stats), rank recurring/forgotten weak points, publish `learning.gap.analyzed`.
3. Expose `POST /api/v1/transcribe` and `POST /api/v1/analyze` as synchronous equivalents of the two stages
   above, for manual/ad-hoc calls.
4. Expose `POST /api/v1/upload` (multipart/form-data: `file`, optional `language_code`) to accept a video/audio
   file directly and run STT + diarization synchronously — for environments where Kafka/S3 aren't wired up yet.

See `RemeLearning/common/src/main/java/com/remelearning/common/constants/KafkaTopics.java` for the shared
topic-name contract with the Java services.

## Run locally — detailed steps (Windows / PowerShell)

Máy hiện chỉ có Python 3.14 cài sẵn (`py -0p`). `torch`/`pyannote.audio` có thể chưa có wheel cho
3.14, nên **cài Python 3.11 hoặc 3.12 riêng** cho service này trước khi tạo virtualenv.

1. Cài Python 3.12 (nếu chưa có) — tải từ https://www.python.org/downloads/ và tick "Add to PATH",
   hoặc dùng winget:
   ```powershell
   winget install Python.Python.3.12
   ```
2. Kiểm tra đã có Python 3.12 chưa (mở terminal MỚI sau khi cài để PATH cập nhật):
   ```powershell
   py -0p
   py -3.12 --version
   ```
3. Di chuyển vào thư mục service:
   ```powershell
   cd "D:\Personal Project\RemeLearning_Project\RemeLearning\services\ai-service"
   ```
4. Tạo virtualenv riêng bằng Python 3.12:
   ```powershell
   py -3.12 -m venv .venv
   ```
5. Kích hoạt virtualenv:
   ```powershell
   .venv\Scripts\Activate.ps1
   ```
   (Nếu PowerShell báo lỗi "chưa được ký/execution policy", chạy một lần:
   `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`)
6. Nâng cấp pip rồi cài dependencies (mất vài phút vì `torch` khá nặng):
   ```powershell
   python -m pip install --upgrade pip
   pip install -e ".[dev]"
   ```
7. Tạo file `.env` từ mẫu và điền `HF_TOKEN` (HuggingFace token có quyền truy cập
   `pyannote/speaker-diarization-3.1`):
   ```powershell
   Copy-Item .env.example .env
   notepad .env
   ```
8. Chạy test (không cần Kafka, không cần model đã tải):
   ```powershell
   pytest
   ```
9. Chạy app — có 2 cách, cả hai đều dùng chung `app/main.py`:
   ```powershell
   uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
   ```
   hoặc chạy trực tiếp bằng IDE/`python`:
   ```powershell
   python app\main.py
   ```
   (không hỗ trợ `--reload` khi chạy theo cách này; dùng lệnh `uvicorn` ở trên nếu cần auto-reload).

   Lưu ý về Kafka: mặc định `KAFKA_ENABLED=true`, app sẽ kết nối Kafka lúc startup (xem `app/main.py`)
   và **lỗi ngay khi khởi động** nếu chưa có Kafka chạy ở `KAFKA_BOOTSTRAP_SERVERS` (mặc định
   `localhost:9092`). Có 2 lựa chọn:
   - Dựng Kafka bằng docker-compose ở dưới, hoặc
   - Đặt `KAFKA_ENABLED=false` trong `.env` để **bỏ qua Kafka hoàn toàn** — app chạy được ngay, chỉ
     các endpoint REST (`/api/v1/upload`, `/api/v1/transcribe`, `/api/v1/analyze`) hoạt động, còn
     luồng Kafka (`recording.uploaded` → `transcript.ready`, `learning.gap.analysis.requested` →
     `learning.gap.analyzed`) sẽ không chạy.
10. Kiểm tra app đã chạy:
    ```powershell
    curl http://localhost:8000/health
    ```

pyannote.audio requires a HuggingFace token with access to `pyannote/speaker-diarization-3.1` — set `HF_TOKEN`.

## Required Python libraries

`pip install -e ".[dev]"` cài tất cả cùng lúc theo đúng version đã pin trong `pyproject.toml`
(**bắt buộc dùng đúng các version pin dưới đây** — xem lý do ở bảng bên dưới). Nếu muốn cài
từng thư viện riêng lẻ, dùng các lệnh sau, mỗi thư viện một lệnh, đúng thứ tự:

```powershell
pip install fastapi
pip install "uvicorn[standard]"
pip install python-multipart
pip install pydantic
pip install pydantic-settings
pip install aiokafka
pip install boto3
pip install faster-whisper
pip install "torch==2.2.2"
pip install "torchaudio==2.2.2"
pip install "numpy<2"
pip install "scipy<1.13"
pip install "huggingface_hub<0.26"
pip install "speechbrain==1.0.3"
pip install "pyannote.audio==3.4.0"
```

| Library | Purpose |
|---|---|
| `fastapi` | REST API framework (`/health`, `/api/v1/transcribe`, `/api/v1/analyze`, `/api/v1/upload`) |
| `uvicorn[standard]` | ASGI server to run the FastAPI app |
| `python-multipart` | Required by FastAPI to parse `multipart/form-data` (file uploads) |
| `pydantic` | Request/response/event schemas (`app/schemas/events.py`) |
| `pydantic-settings` | Loads config/env vars (`app/config.py`) |
| `aiokafka` | Async Kafka consumer/producer for the event pipeline (`app/kafka/`) |
| `boto3` | S3/MinIO client to download uploaded audio/video (`app/storage/s3_client.py`) |
| `faster-whisper` | Speech-to-text engine (`app/stt/whisper_engine.py`) — also decodes video containers (mp4...) |
| `torch==2.2.2` | Backend required by `faster-whisper`/`pyannote.audio`. **Pin cứng**: bản mới hơn không tương thích `pyannote.audio` 3.x |
| `torchaudio==2.2.2` | Đọc/ghi audio cho `pyannote.audio`. **Pin cứng**: bản >2.2.x đã xóa API cũ (`list_audio_backends`, `AudioMetaData`, `info`/`load`) mà `pyannote.audio` 3.x gọi trực tiếp |
| `numpy<2` | `torch`/`torchaudio` 2.2.2 được build với numpy<2, dùng numpy 2.x sẽ lỗi runtime |
| `scipy<1.13` | scipy≥1.13 yêu cầu numpy≥2 — xung đột với pin numpy<2 ở trên |
| `huggingface_hub<0.26` | Bản ≥0.26 đã bỏ tham số `use_auth_token` mà `pyannote.audio` 3.4.0 gọi nội bộ khi tải model |
| `speechbrain==1.0.3` | Dependency của `pyannote.audio`. Bản 1.1.0 có bug lazy-import `k2_fsa` làm crash khi load pipeline (không liên quan tới việc có dùng k2 hay không) |
| `pyannote.audio==3.4.0` | Speaker diarization (`app/stt/diarization.py`) — needs `HF_TOKEN`. **Pin cứng**: bản ≥4.0 bắt buộc tải thêm model gated `pyannote/speaker-diarization-community-1` (cần được HuggingFace duyệt quyền riêng, không tự cài được) |

Dev-only (chạy test):

```powershell
pip install pytest
pip install pytest-asyncio
```

| Library | Purpose |
|---|---|
| `pytest` | Test runner |
| `pytest-asyncio` | Support for async test functions |

Sau khi cài xong (bằng cách nào cũng vậy), cài chính package `ai-service` ở dạng editable để Python
nhận diện được package `app`:

```powershell
pip install -e . --no-deps
```

**Lưu ý:** `torch`/`torchaudio`/`pyannote.audio`/`numpy`/`scipy`/`huggingface_hub`/`speechbrain` phải
đúng version pin ở trên — đây là kết quả sau khi gỡ hàng loạt lỗi tương thích thực tế giữa các bản mới
nhất của những package này trên Windows. Chạy trên **Python 3.11 hoặc 3.12** trong virtualenv riêng
(không dùng Python 3.13+/3.14 — một số bản pin ở trên không có wheel cho Python quá mới).

## Run with docker-compose (Kafka, Redis, MinIO, ai-service)

```bash
docker compose up kafka redis minio ai-service
curl localhost:8000/health
```
