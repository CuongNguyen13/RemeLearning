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
5. Expose `POST /api/v1/dictation/align-sentences` (multipart: `audio` file, `sentences` as a JSON-encoded
   array of strings) — transcribes `audio` with Whisper word-level timestamps and matches the given script
   sentences against that timeline in order, returning each sentence's `{start_ms, end_ms}` (null if a
   sentence couldn't be located). Called by `english-service`'s dictation `getClipDetail` the first time a
   clip's sentences are read without timestamps — see `app/align/sentence_aligner.py`.
6. Expose `POST /api/v1/pronunciation/score` — Goodness-of-Pronunciation (GOP) scoring for the
   Nói/Phát âm (speaking/pronunciation) practice feature, given a learner's recorded attempt at a
   known target sentence. See `app/pronunciation/` below.

See `RemeLearning/common/src/main/java/com/remelearning/common/constants/KafkaTopics.java` for the shared
topic-name contract with the Java services.

### Dictation sentence alignment algorithm (`app/align/sentence_aligner.py`)

`transcribe_words` (`app/stt/whisper_engine.py`) runs faster-whisper with `word_timestamps=True` and
flattens every segment's words into one chronological list (`WordTiming`: word, start/end seconds,
`probability`). `align_sentences` then matches each script sentence against that timeline, in order,
using a cursor-based greedy match — **not** a full forced-alignment model, since the per-word
timestamps are already Whisper's own decoder-attention estimate rather than a phoneme-level alignment:

1. For each sentence, tokenize (lowercase, strip punctuation) and find the sentence's first token at
   or after the running cursor, skipping any word below `_MIN_WORD_CONFIDENCE` (0.35) and using a
   1-edit fuzzy match (`_tokens_match`) for tokens of 4+ letters, to tolerate Whisper mishearing a
   single letter of an otherwise-correct word.
2. Walk forward from there, matching the sentence's remaining tokens in order (tolerating
   extra/misheard words in between).
3. **Reject the match** (leave `start_ms`/`end_ms` as `None`) unless the sentence's *last* token was
   reached AND at least `_MIN_MATCH_RATIO` (0.8) of its tokens matched overall — a partial match
   anchored on an earlier word would truncate the clip mid-sentence, which is worse than leaving it
   unaligned for a later retry.
4. On acceptance, pad the boundary by `_PADDING_MS` (100ms) each side, clamped so it never reaches
   into a neighboring word's span — Whisper's word timestamps are frequently clipped tight against
   the actual phoneme boundary, so an unpadded cut audibly clips the first/last sound.
5. The cursor only advances past sentences that were actually matched, so a rejected sentence can't
   cause the next sentence to match against words that belong to it (no cascading drift).

**Known limitation / next step if cutting is still imprecise:** this is still fundamentally bounded
by Whisper's own word-timestamp quality (decoder cross-attention, not forced alignment). If sentence
boundaries are still noticeably off after the above, the next step is to replace the timestamp source
itself — either WhisperX-style CTC (wav2vec2) refinement of Whisper's output, or a direct
forced-alignment pass of the *known* script text against the audio (e.g. `torchaudio`'s
`MMS_FA`/Wav2Vec2 CTC pipelines, already compatible with this service's pinned `torch`/`torchaudio`
versions) — since dictation already knows the exact target text, this is a forced-alignment problem,
not free transcription. A VAD pass (Silero VAD/webrtcvad) to snap boundaries to actual silence could
also replace the fixed `_PADDING_MS` heuristic with something acoustically grounded.

### Pronunciation GOP scoring (`app/pronunciation/`)

Goodness-of-Pronunciation (GOP) scoring for the "Nói/Phát âm" (speaking/pronunciation) practice
skill: given a learner's recorded attempt at a known target sentence, scores how well an acoustic
model's posteriors support each expected phone having actually occurred.

Pipeline (`app/pronunciation/service.py` orchestrates all three stages, kept separate from the
FastAPI route so it's callable/testable without an HTTP layer):

1. **G2P** (`app/pronunciation/g2p.py`) — `G2pEnProvider` (the `g2p_en` backend, pure-Python,
   CMUdict + a small seq2seq model for out-of-vocabulary words — no `espeak-ng` system dependency)
   converts the expected English text into its ARPAbet phoneme sequence, split by word. Defined
   behind a `G2pProvider` interface so a future switch to `phonemizer`/`espeak-ng` (better
   out-of-CMUdict coverage, real IPA output) doesn't touch the scoring code.
2. **Acoustic model** (`app/pronunciation/gop_model.py`) — `Wav2Vec2GopModel` lazily loads
   `facebook/wav2vec2-lv-60-espeak-cv-ft` (default; overridable via `PRONUNCIATION_MODEL`) and runs
   a forward pass to get per-frame (~20ms) log-probabilities over the model's own IPA/espeak phoneme
   vocabulary. Deliberately bypasses `Wav2Vec2Processor`/`Wav2Vec2PhonemeCTCTokenizer` (which needs
   the `phonemizer` package and its `espeak-ng` binary) — only `Wav2Vec2FeatureExtractor` plus a raw
   `vocab.json` load is needed for the acoustic output this stage uses.
3. **ARPAbet → IPA mapping** (`app/pronunciation/arpabet_ipa.py`) — maps `g2p_en`'s CMUdict/ARPAbet
   phone symbols to the espeak/IPA symbols the acoustic model was fine-tuned on; checked against the
   model's actual `vocab.json`, covers all 39 CMUdict phones.
4. **GOP scorer** (`app/pronunciation/gop_scorer.py`) — greedy-decodes the model's most-likely phone
   per frame and collapses repeats, edit-distance-aligns (Wagner-Fischer, the same algorithm
   `DictationScorer.java` uses for words, applied to phones here) that recognized sequence against
   the expected sequence, then scores each aligned expected phone by the mean posterior *of that
   expected phone* over the frames the alignment assigned it (a deleted/unmatched expected phone
   scores 0). Phones scoring below `WEAK_PHONEME_THRESHOLD` (0.4) are surfaced in `weak_phonemes`.

**Endpoint:** `POST /api/v1/pronunciation/score` (multipart/form-data):

- Request: `audio` (file, the learner's recording), `expected_text` (form field, the target
  sentence), `language_code` (form field, default `"en"`, used only for the plain Whisper transcript
  returned alongside the score).
- Response (`PronunciationScoreResponse`):
  ```json
  {
    "overall": 0.0,
    "words": [
      {
        "word": "string",
        "score": 0.0,
        "phonemes": [{"ipa": "string", "score": 0.0}]
      }
    ],
    "transcript": "string",
    "weak_phonemes": ["string"]
  }
  ```
- Returns `503` with a message to set `PRONUNCIATION_ENABLED=true` when the feature is disabled —
  same gating pattern as `TTS_ENABLED`/`KAFKA_ENABLED`.

**Env vars** (`app/config.py`):

| Var | Default | Purpose |
|---|---|---|
| `PRONUNCIATION_ENABLED` | `false` | Gates the whole feature. Off by default, same pattern as `KAFKA_ENABLED`/`VISION_ENABLED`: loading the wav2vec2 acoustic model costs real memory/CPU that shouldn't be paid on every ai-service instance until the feature is actually used (this machine's free-RAM headroom is tight — see project notes). |
| `PRONUNCIATION_MODEL` | `facebook/wav2vec2-lv-60-espeak-cv-ft` | HuggingFace model id for the acoustic model (`app/pronunciation/gop_model.py`). |

**New dependencies** (`pyproject.toml`) — `transformers==4.38.2`, `tokenizers>=0.14,<0.19`,
`g2p_en>=2.1`. Pinned tight per the in-file comment: `transformers>=4.39` requires
`tokenizers>=0.19`, but `huggingface_hub<0.26` (already pinned for `pyannote.audio`, see below) only
ships releases compatible with `tokenizers<0.19` up to `huggingface_hub` 0.25.2's own tokenizers
ceiling — `transformers==4.38.2` + `tokenizers==0.15.2` is the newest combination verified to
coexist with the `pyannote.audio`/`huggingface_hub<0.26` pin without any package needing an
upgrade. Bump only together with a re-check of both ceilings.

**Known limitations (from `app/pronunciation/gop_scorer.py`'s module docstring):** this is a
**simplified GOP, not the textbook forced-alignment version** (Viterbi/CTC forced segmentation of
the acoustic model against the expected sequence, then log-posterior-ratio per aligned phone).
Instead it (1) greedy-decodes the model's own most-likely phone per frame and collapses repeats
("recognition", not alignment-to-expected), (2) edit-distance-aligns that recognized sequence
against the expected sequence, (3) scores each aligned expected phone by the mean posterior of that
expected phone over the frames the alignment assigned it, and (4) scores a deleted/unmatched
expected phone as 0. For tightly-packed or reordered speech, this recognition-then-align approach
can mis-segment frame ranges that true forced alignment would place correctly. This PoC has been
verified against clean TTS-generated audio; accuracy against noisy/natural speech has not yet been
validated. Upgrade path: replace the greedy-decode + edit-distance steps with proper CTC forced
alignment (`torchaudio.functional.forced_align`, available in newer `torchaudio` — not the `2.2.2`
pinned here) once accuracy against real graded audio shows this approximation isn't good enough.

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
pip install "transformers==4.38.2"
pip install "tokenizers>=0.14,<0.19"
pip install "g2p_en>=2.1"
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
| `transformers==4.38.2` | Loads the `facebook/wav2vec2-lv-60-espeak-cv-ft` acoustic model for GOP scoring (`app/pronunciation/gop_model.py`), gated by `PRONUNCIATION_ENABLED`. **Pin cứng**: `transformers>=4.39` yêu cầu `tokenizers>=0.19`, nhưng `huggingface_hub<0.26` (pin ở trên cho `pyannote.audio`) chỉ tương thích `tokenizers<0.19` — `4.38.2` + `tokenizers==0.15.2` là combo mới nhất đã verify không xung đột |
| `tokenizers>=0.14,<0.19` | Dependency của `transformers`, giữ trần `<0.19` để không xung đột với `huggingface_hub<0.26` (xem dòng trên) |
| `g2p_en>=2.1` | Grapheme-to-phoneme cho GOP scoring (`app/pronunciation/g2p.py`) — pure-Python (CMUdict + seq2seq cho từ ngoài từ điển), không cần binary hệ thống `espeak-ng` |

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

**Lưu ý:** `torch`/`torchaudio`/`pyannote.audio`/`numpy`/`scipy`/`huggingface_hub`/`speechbrain`/
`transformers`/`tokenizers` phải đúng version pin ở trên — đây là kết quả sau khi gỡ hàng loạt lỗi tương thích thực tế giữa các bản mới
nhất của những package này trên Windows. Chạy trên **Python 3.11 hoặc 3.12** trong virtualenv riêng
(không dùng Python 3.13+/3.14 — một số bản pin ở trên không có wheel cho Python quá mới).

## Run with docker-compose (Kafka, Redis, MinIO, ai-service)

```bash
docker compose up kafka redis minio ai-service
curl localhost:8000/health
```
