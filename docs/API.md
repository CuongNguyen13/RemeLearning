# RemeLearning API Documentation

Tài liệu tổng hợp toàn bộ API (REST + Kafka event) đang thực sự tồn tại trong codebase tại thời điểm viết
tài liệu này. Các service Java còn ở dạng skeleton (chưa có controller nào) được liệt kê ở cuối để dễ theo dõi
tiến độ.

> Nguồn: tổng hợp trực tiếp từ code (`@RestController`, FastAPI routes, `@KafkaListener`, Kafka handlers) và
> đối chiếu với từng `openapi.yaml` theo service. Cập nhật tài liệu này khi thêm/sửa endpoint hoặc topic.

`vocabulary-service`, `grammar-service`, `pronunciation-service` đã được gộp thành một service duy nhất
**`english-service`** (modular monolith — mỗi domain một package con: `vocabulary/`, `grammar/`,
`pronunciation/`), vì cả ba đều chỉ phân tích các `category` khác nhau của cùng một event
`learning.gap.analyzed`. Port (8085), tên bảng/migration/mapper của domain `vocabulary` giữ nguyên; chỉ
package Java đổi (`com.remelearning.vocabulary.*` → `com.remelearning.english.vocabulary.*`).
Cả ba domain (`vocabulary`, `grammar`, `pronunciation`) nay đều có controller/entity/mapper riêng (xem
mục 1, 6); chỉ `vocabulary` sở hữu `TranscriptReadyConsumer`/`TranscriptService` — bảng `transcripts`/
`transcript_segments` là dữ liệu dùng chung, `grammar`/`pronunciation` chỉ đọc lại qua
`GET /api/v1/transcripts/{recordingId}`, không tự ingest lần hai.

`english-service` nay có thêm package thứ tư, **`practice`** (redo-exercise), cắt ngang cả 3 domain thay vì
thuộc riêng domain nào: chấm điểm các câu trả lời khi learner làm lại bài tập, duy trì `mistake_history`
(số lần sai + lần gần nhất) cho từng item, rồi publish `learning.gap.analysis.requested` để `ai-service`
tính lại `forgettingScore` — đây chính là producer còn thiếu được nhắc ở mục 14 (bản cũ). Xem mục 1, 8.

`english-service` có package thứ năm, **`dictation`** (nghe chép chính tả) — **không** nghe Kafka, được
FE kích hoạt trực tiếp qua bff-service. Nay được **làm lại** thành hai phần: (A) **thư viện audio thu
âm thật** (nạp từ ổ đĩa qua `common.storage.StorageClient` — trừu tượng đọc/ghi file mới, local giờ,
cloud sau) vào `dictation_clips`, gắn taxonomy skill/level (CEFR)/topic/examType; và (B) **"Luyện nghe
với AI"** — Gemini sinh câu luyện từ các từ hay sai, **Supertonic** (chạy trong `ai-service`, thay Google
Cloud TTS) đọc thành audio. Cả hai chấm điểm chung bằng WER (`DictationScorer`), gọi Gemini gợi ý tức
thời, và publish `learning.gap.analyzed` để pipeline gợi ý học tập sẵn có biến lỗi thành đề xuất.
Xem mục 1, 6.

**Rev 2 (chép chính tả theo từng câu)**: bổ sung mô hình duyệt bài **folder → file** (3 endpoint mới:
`/folders`, `/folders/{folderId}/lessons`, `/clips/{clipId}` chi tiết) cộng `minListensForHint` trên
`/facets`, để FE luyện chép chính tả từng câu (nghe câu, gõ đúng tự next) thay vì cả clip một Textarea.
Script được tách câu (`dictation_clip_sentences`) ngay khi import; mốc `startMs`/`endMs` cho từng câu
được AI-align **lazy** ngay trong `GET /api/v1/dictation/clips/{clipId}`: nếu bất kỳ câu nào còn thiếu
`startMs`/`endMs`, service đọc audio của clip qua `StorageClient` rồi gọi `ai-service`
`POST /api/v1/dictation/align-sentences` (Whisper word-timestamps + khớp câu tuần tự theo thứ tự, xem
mục 7) để lấy mốc thời gian, lưu ngay xuống `dictation_clip_sentences` (`updateSentenceTimestamps`) rồi
mới trả response — nên request đó có thể chậm hơn bình thường (transcribe cả audio clip). Nếu
`ai-service` không gọi được hoặc một câu không khớp được, câu đó vẫn giữ `null` (không chặn response),
và sẽ được thử lại ở lần `GET` kế tiếp. Chấm điểm vẫn **FE-only**: BE chỉ trả `scriptText`/`sentences`
khi mở chi tiết 1 bài, FE tự so khớp rồi gọi nguyên `POST /attempts` hiện có. Facet-filter/`/sessions`
cũ **không bị xoá**, chỉ không còn là luồng chính của FE.

`recording-service`, `recommendation-service`, `dashboard-service` nay cũng đã có API/Kafka thật (trước đây
chỉ là skeleton) — hoàn thiện đầu và cuối của pipeline: `recording-service` là điểm vào (upload → S3 →
`recording.uploaded`), `recommendation-service`/`dashboard-service` là điểm ra (tổng hợp `learning.gap.analyzed`
thành gợi ý học tập và một dashboard tổng hợp), toàn bộ theo hướng event-driven giữa các service backend.

`user-service` (8081) nay cũng có API thật — đăng ký/đăng nhập (phát hành JWT thật bằng
`common.security.JwtTokenProvider`, lần đầu tiên class này thực sự được gọi) và profile cơ bản. **Lưu ý
quan trọng**: user-service chỉ *phát hành* token — chưa có service nào (kể cả `bff-service`) *xác thực*
JWT trên request, toàn repo vẫn chưa có `SecurityConfig`/Spring Security nào cả (xem `CLAUDE.md`). Coi
như chưa có auth thật cho tới khi có filter xác thực được thêm vào.

`bff-service` (8080, reactive/WebFlux, không có DB) là điểm vào REST duy nhất dành cho client
web/mobile — compose (fan-out song song qua `WebClient`) các domain service phía trên và gộp thành response
theo hình dạng UI cần, thay vì client phải tự gọi từng service. Nay đã dùng tới `userServiceClient` (proxy
đăng ký/đăng nhập/profile, và gộp thêm profile vào `GET /api/v1/learners/{userId}/overview`).

## Mục lục

- [1. english-service (Java) — REST](#1-english-service-java--rest)
- [2. user-service (Java) — REST](#2-user-service-java--rest)
- [3. recording-service (Java) — REST](#3-recording-service-java--rest)
- [4. recommendation-service (Java) — REST](#4-recommendation-service-java--rest)
- [5. dashboard-service (Java) — REST](#5-dashboard-service-java--rest)
- [6. bff-service (Java) — REST](#6-bff-service-java--rest)
- [7. ai-service (Python) — REST](#7-ai-service-python--rest)
- [8. Kafka — english-service (consumer/producer)](#8-kafka--english-service-consumerproducer)
- [9. Kafka — recording-service (producer)](#9-kafka--recording-service-producer)
- [10. Kafka — recommendation-service (consumer/producer)](#10-kafka--recommendation-service-consumerproducer)
- [11. Kafka — dashboard-service (consumer)](#11-kafka--dashboard-service-consumer)
- [12. Kafka — ai-service (consumer/producer)](#12-kafka--ai-service-consumerproducer)
- [13. Bảng tổng hợp](#13-bảng-tổng-hợp)
- [14. Các service/domain chưa có API](#14-các-servicedomain-chưa-có-api)

---

## 1. `english-service` (Java) — REST

Base path: `http://localhost:8085`. Toàn bộ response bọc trong `ApiResponse<T>`
(`common/src/main/java/com/remelearning/common/response/ApiResponse.java`):

```json
{
  "success": true,
  "data": { },
  "errorCode": null,
  "message": null,
  "timestamp": "2026-07-13T10:00:00Z"
}
```

Spec đầy đủ (OpenAPI): `RemeLearning/services/english-service/openapi.yaml`. Swagger UI khi service chạy:
`http://localhost:8085/swagger-ui.html`.

### Transcripts (shared, package `vocabulary`)

### GET `/api/v1/transcripts/{recordingId}`

Trả về transcript đã lưu (nhận qua Kafka topic `transcript.ready`) của một recording, kèm các segment đã gán
speaker, sắp theo thứ tự thời gian.

- **Path param**: `recordingId` (string) — ví dụ `rec-0001`
- **Response `data`** — `TranscriptResponse`:

| Field | Type | Mô tả |
|---|---|---|
| `recordingId` | string | |
| `userId` | string | |
| `fullText` | string | Toàn bộ transcript |
| `segments` | `TranscriptSegment[]` | Danh sách segment theo `segmentOrder` |

`TranscriptSegment`:

| Field | Type | Mô tả |
|---|---|---|
| `id` | long | |
| `transcriptId` | long | |
| `speaker` | string | |
| `content` | string | |
| `startSeconds` | double | |
| `endSeconds` | double | |
| `segmentOrder` | int | |
| `language` | string | Ngôn ngữ ai-service dùng để transcribe segment này (auto-detect hoặc ép theo `language_code`, xem mục 7) |

- **Lỗi**: `404` nếu chưa có transcript cho `recordingId` → `ApiResponse{success:false, errorCode:"NOT_FOUND", message:"Transcript not found for recordingId=..."}`

### Vocabulary weak points (package `vocabulary`)

### GET `/api/v1/vocabulary/weak-points/{userId}`

Danh sách các "điểm yếu" từ vựng (từ/cụm hay bị quên) của một learner, sắp giảm dần theo `forgettingScore`.

- **Path param**: `userId` (string)
- **Query param** (tùy chọn): `type` — `VocabularyType` enum: `NOUN | VERB | ADJECTIVE | ADVERB | PHRASAL_VERB | COLLOCATION | IDIOM | OTHER`
- **Response `data`** — `VocabularyWeakPoint[]`:

| Field | Type | Mô tả |
|---|---|---|
| `id` | long | |
| `recordingId` | string | |
| `userId` | string | |
| `itemId` | string | |
| `label` | string | Từ/cụm từ |
| `vocabularyType` | `VocabularyType` | Phân loại (rule-based hoặc LLM) |
| `forgettingScore` | double | Điểm yếu tổng hợp — xem mục "Cơ chế chấm điểm" bên dưới |
| `recommendation` | string | Gợi ý học lại |
| `masteryLevel` | double, nullable | Xác suất đã nắm vững (BKT), null nếu item chưa từng được Java engine chấm |
| `nextReviewAt` | Instant, nullable | Lịch ôn tiếp theo (Leitner), null nếu item chưa từng được Java engine chấm |
| `scoreSource` | `"PYTHON_LEGACY" \| "JAVA_ENGINE"` | Điểm hiện tại đến từ ai-service (Kafka) hay từ practice/redo (Java trực tiếp) |
| `updatedAt` | Instant | |

### GET `/api/v1/vocabulary/weak-points/{userId}/grouped`

Giống endpoint trên nhưng nhóm theo `VocabularyType` (không có query param `type`).

- **Path param**: `userId` (string)
- **Response `data`**: `Map<VocabularyType, VocabularyWeakPoint[]>`, ví dụ:

```json
{
  "ADJECTIVE": [ { "...": "VocabularyWeakPoint" } ],
  "PHRASAL_VERB": [ { "...": "VocabularyWeakPoint" } ]
}
```

### Grammar weak points (package `grammar`)

### GET `/api/v1/grammar/weak-points/{userId}`

Danh sách các lỗi ngữ pháp lặp lại/hay quên của một learner, sắp giảm dần theo `forgettingScore`.

- **Path param**: `userId` (string)
- **Query param** (tùy chọn): `type` — `GrammarType` enum: `TENSE | SUBJECT_VERB_AGREEMENT | ARTICLE | PREPOSITION | WORD_ORDER | PLURAL | PUNCTUATION | OTHER`
- **Response `data`** — `GrammarWeakPoint[]`:

| Field | Type | Mô tả |
|---|---|---|
| `id` | long | |
| `recordingId` | string | |
| `userId` | string | |
| `itemId` | string | |
| `label` | string | Mô tả lỗi ngữ pháp |
| `grammarType` | `GrammarType` | Phân loại (rule-based hoặc LLM) |
| `forgettingScore` | double | Điểm yếu tổng hợp — xem mục "Cơ chế chấm điểm" bên dưới |
| `recommendation` | string | Gợi ý học lại |
| `masteryLevel` | double, nullable | Xác suất đã nắm vững (BKT), null nếu item chưa từng được Java engine chấm |
| `nextReviewAt` | Instant, nullable | Lịch ôn tiếp theo (Leitner), null nếu item chưa từng được Java engine chấm |
| `scoreSource` | `"PYTHON_LEGACY" \| "JAVA_ENGINE"` | Điểm hiện tại đến từ ai-service (Kafka) hay từ practice/redo (Java trực tiếp) |
| `updatedAt` | Instant | |

### GET `/api/v1/grammar/weak-points/{userId}/grouped`

Giống endpoint trên nhưng nhóm theo `GrammarType` (không có query param `type`).

- **Path param**: `userId` (string)
- **Response `data`**: `Map<GrammarType, GrammarWeakPoint[]>`

### Pronunciation weak points (package `pronunciation`)

### GET `/api/v1/pronunciation/weak-points/{userId}`

Danh sách các lỗi phát âm lặp lại/hay quên của một learner, sắp giảm dần theo `forgettingScore`.

- **Path param**: `userId` (string)
- **Query param** (tùy chọn): `type` — `PronunciationType` enum: `VOWEL | CONSONANT | STRESS | INTONATION | LINKING | RHYTHM | OTHER`
- **Response `data`** — `PronunciationWeakPoint[]`:

| Field | Type | Mô tả |
|---|---|---|
| `id` | long | |
| `recordingId` | string | |
| `userId` | string | |
| `itemId` | string | |
| `label` | string | Mô tả lỗi phát âm |
| `pronunciationType` | `PronunciationType` | Phân loại (rule-based hoặc LLM) |
| `forgettingScore` | double | Điểm yếu tổng hợp — xem mục "Cơ chế chấm điểm" bên dưới |
| `recommendation` | string | Gợi ý luyện lại |
| `masteryLevel` | double, nullable | Xác suất đã nắm vững (BKT), null nếu item chưa từng được Java engine chấm |
| `nextReviewAt` | Instant, nullable | Lịch ôn tiếp theo (Leitner), null nếu item chưa từng được Java engine chấm |
| `scoreSource` | `"PYTHON_LEGACY" \| "JAVA_ENGINE"` | Điểm hiện tại đến từ ai-service (Kafka) hay từ practice/redo (Java trực tiếp) |
| `updatedAt` | Instant | |

### GET `/api/v1/pronunciation/weak-points/{userId}/grouped`

Giống endpoint trên nhưng nhóm theo `PronunciationType` (không có query param `type`).

- **Path param**: `userId` (string)
- **Response `data`**: `Map<PronunciationType, PronunciationWeakPoint[]>`

### Practice / redo-exercise (package `practice`)

### POST `/api/v1/practice/redo`

Nhận kết quả chấm điểm của một lần learner **làm lại bài tập** (redo exercise) — các câu hỏi có thể trộn
cả 3 domain (vocabulary/grammar/pronunciation) trong cùng một lần submit.

- **Request body** — `PracticeRedoRequest`:

| Field | Type | Mô tả |
|---|---|---|
| `userId` | string | bắt buộc |
| `attempts` | `PracticeAttemptRequest[]` | bắt buộc, ít nhất 1 phần tử |

`PracticeAttemptRequest`: `itemId` (string, bắt buộc), `category` (string, bắt buộc — `"vocabulary" |
"grammar" | "pronunciation"`, phải khớp category gốc của item), `label` (string, bắt buộc), `correct`
(boolean).

- **Xử lý** (`PracticeServiceImpl` + `WeakPointScoringOrchestrator`, mỗi attempt xử lý tuần tự, không
  song song — bắt buộc để khóa `SELECT ... FOR UPDATE` bên dưới hoạt động đúng):
  1. Ghi log vào `practice_attempts` (audit, không đọc lại).
  2. **Chấm điểm trực tiếp bằng Java** (không cần chờ `ai-service`/Kafka):
     - Khóa và đọc trạng thái chấm điểm hiện tại của item trong `mistake_history`
       (`SELECT ... FOR UPDATE`, đọc **trước khi** cập nhật `lastSeenAt`, nếu không độ "mới quên" sẽ
       luôn bị reset về ~0 ngay tại lần trả lời đó).
     - Upsert `mistake_history` như cũ — `occurrenceCount` chỉ tăng khi `correct=false`, `lastSeenAt`
       luôn refresh về `now()`.
     - Tính `weakScore` mới bằng `common.scoring.WeakPointScoringEngine` (công thức xem bên dưới),
       cập nhật lại các cột chấm điểm trong `mistake_history` (`ease_factor`, `half_life_days`,
       `mastery`, `leitner_box`, `next_review_at`, `last_weak_score`), cập nhật bảng thống kê độ khó
       toàn cục `item_difficulty_stats` (khóa `(category, label_key)`), rồi **upsert thẳng** vào bảng
       weak-point của domain tương ứng (`vocabulary_weak_points`/`grammar_weak_points`/
       `pronunciation_weak_points`) với `scoreSource = JAVA_ENGINE`.
  3. Sau khi xử lý xong toàn bộ batch, vẫn đọc lại **toàn bộ** `mistake_history` hiện tại của `userId`,
     build một `AnalysisRequestedEvent` (`recordingId` sinh 1 lần dùng chung cho cả batch, dạng
     `"practice-<uuid>"`, `segments` rỗng vì không có transcript) rồi publish
     `learning.gap.analysis.requested` (mục 8) — để `ai-service` (mục 7, 12) vẫn tính lại
     `forgettingScore` bằng `RuleBasedAnalyzer` như cũ, và `recommendation-service`/`dashboard-service`
     (chỉ nghe `learning.gap.analyzed`, không biết gì về đường Java trực tiếp) vẫn được cập nhật.
     Vì bước 2 đã ghi điểm mới ngay lập tức, bước 3 giờ chỉ còn mang tính "đảm bảo các consumer khác
     đồng bộ" chứ không phải đường duy nhất để có điểm mới.
- **Response `data`**: `null` (chỉ trả `success: true`).

**Chống ghi đè điểm cũ (`score_source` guard):** vì giờ có 2 nguồn ghi vào cùng một bảng weak-point
(consumer Kafka của `learning.gap.analyzed`, và đường Java trực tiếp ở trên), câu `upsert` của cả 3
domain có thêm điều kiện: một bản ghi `PYTHON_LEGACY` bị bỏ qua (không ghi đè) nếu dòng hiện tại đang
là `JAVA_ENGINE` — một chiều, không đảo ngược. Nhờ vậy kết quả tính chậm hơn của `ai-service` không thể
âm thầm ghi đè điểm mới hơn vừa tính trực tiếp bằng Java.

`mistake_history` được seed lần đầu (occurrenceCount=1) bởi `MistakeHistorySeedConsumer` (mục 8) ngay khi
một item lần đầu xuất hiện trong `learning.gap.analyzed`, kể cả trước khi learner từng redo — nên
`GET .../weak-points` (đã sắp theo `forgettingScore`) luôn có sẵn dữ liệu để chọn ra bộ câu hỏi cần làm lại.

### GET `/api/v1/practice/review-queue/{userId}`

Danh sách các item **đến hạn ôn lại** (theo lịch Leitner mà Java scoring engine duy trì), sắp tăng dần
theo `nextReviewAt` (gần nhất trước).

- **Path param**: `userId` (string)
- **Response `data`** — `ReviewQueueItem[]`:

| Field | Type | Mô tả |
|---|---|---|
| `itemId` | string | |
| `category` | string | `vocabulary \| grammar \| pronunciation` |
| `label` | string | |
| `lastWeakScore` | double, nullable | `weakScore` lần chấm gần nhất |
| `nextReviewAt` | Instant | |

Nguồn dữ liệu là trực tiếp `mistake_history` (không cần join sang 3 bảng weak-point vì `last_weak_score`
và `label_key` đã được lưu sẵn ở đó), lọc `next_review_at <= now()`.

### Dictation practice — nghe chép chính tả (package `dictation`)

`dictation` **không** nghe Kafka — được FE kích hoạt trực tiếp qua bff-service. Có **hai phần**:

- **A. Thư viện cố định** — audio thu âm thật (thư mục `D:/Personal Project/Audio`, gồm
  `english-conversations`, `toeic`, …) được `DictationLibraryImporter` nạp vào `dictation_clips` lúc
  khởi động qua `StorageClient` của `common` (mặc định đọc từ ổ đĩa local). Mỗi clip gắn taxonomy cố
  định: **skill** (4 kỹ năng), **level** (CEFR A1–C2), **topic**, **exam_type** (TOEIC/IELTS/…) — suy
  ra từ đường dẫn thư mục + tên file, **không dùng AI**.
- **B. "Luyện nghe với AI"** — từ những từ/câu learner hay nghe sai, Gemini sinh **một đoạn văn luyện
  tập duy nhất** (độc thoại hoặc hội thoại nhiều người, tuỳ Gemini chọn) lồng ghép tự nhiên các từ đó,
  mỗi speaker được gán ngẫu nhiên một giọng Supertonic khác nhau, từng câu thoại được Supertonic
  (chạy trong `ai-service`) đọc riêng rồi ghép lại (`WavAudioMerger`) thành một file audio duy nhất.

Cả hai phần chấm điểm chung một luồng (`POST /attempts`): dùng lại `DictationScorer` (WER) đã có, gọi
`LlmClient` (Gemini) cho gợi ý tức thời, và **publish `learning.gap.analyzed`** để pipeline gợi ý học
tập sẵn có (recommendation-service, dashboard-service, consumer vocabulary) biến các lỗi thành đề xuất.

TTS đổi từ Google Cloud sang **Supertonic** (`reme.tts.provider=supertonic`, gọi `ai-service`
`POST /api/v1/tts/synthesize`); lưu/đọc file qua `StorageClient` của `common` (local → cloud sau này).

### GET `/api/v1/dictation/facets`

Các giá trị lọc phân biệt trong thư viện (skills/levels/topics/examTypes), để dựng bộ lọc FE.
- **Response `data`** — `DictationFacetsDto`: `{skills[], levels[], topics[], examTypes[],
  minListensForHint}` (`minListensForHint`: ngưỡng số lần nghe tối thiểu trước khi FE mở gợi ý — đếm
  ở FE theo từng câu, cấu hình qua `dictation.hint.min-listens`, mặc định 3).

### GET `/api/v1/dictation/clips`

Duyệt clip trong thư viện, lọc theo bất kỳ tập con nào của `skill`/`level`/`topic`/`examType`.
- **Query params**: `skill?`, `level?`, `topic?`, `examType?`, `limit` (mặc định 50).
- **Response `data`** — `DictationClipDto[]`: `{clipId, code, title, skill, level, topic, examType,
  audioUrl}` (không có script — tránh lộ đáp án).

### GET `/api/v1/dictation/folders` (rev 2)

Danh sách folder (chủ đề) trong thư viện — `folder` = tên thư mục cha trực tiếp của file audio trong
storage (khác với `topic`/`skill`/`level`/`examType`, vốn suy ra theo taxonomy riêng của
`DictationLibraryImporter`; 2 khái niệm song song, không thay thế nhau).
- **Response `data`** — `DictationFolderDto[]`: `{folderId, name, lessonCount}`.

### GET `/api/v1/dictation/folders/{folderId}/lessons` (rev 2)

Danh sách bài tập (clip) trong 1 folder — nhẹ, **không** có `scriptText`/`sentences`, chỉ đủ để render
danh sách; script chỉ tải khi mở chi tiết 1 bài (endpoint dưới).
- **Response `data`** — `DictationLessonSummaryDto[]`: `{clipId, code, title, audioUrl}`.

### GET `/api/v1/dictation/clips/{clipId}` (rev 2)

Chi tiết 1 clip — script đầy đủ + câu đã tách — dùng khi learner mở 1 bài để luyện chép chính tả từng
câu. Ngược lại với `/clips` (bulk-list) và `/folders/{folderId}/lessons`, endpoint này **có** trả script
vì chỉ tải cho đúng 1 bài đang mở, không phải danh sách bulk.
- **Xử lý**: nếu bất kỳ câu nào của clip còn thiếu `startMs`/`endMs`, gọi `ai-service`
  `POST /api/v1/dictation/align-sentences` trước khi trả response (xem mục 7 + phần mô tả ở đầu file)
  — lazy, chạy ngay trong request này, không phải một job nền riêng.
- **Query param** — `translationLang?` (mới): ngôn ngữ UI hiện tại của learner (vd `"vi"`). Nếu có và
  khác `"en"`, bất kỳ câu nào còn thiếu `translation` sẽ được dịch lazy bằng LLM (`ensureSentencesTranslated`)
  ngay trong request này (một lệnh gọi LLM cho cả clip), lưu lại, rồi trả về cùng response — bỏ qua
  hoàn toàn nếu param rỗng hoặc bằng `"en"` (ngôn ngữ gốc của nội dung).
- **Response `data`** — `DictationClipDetailDto`: `{clipId, code, title, audioUrl, scriptText,
  sentences[]}`, mỗi phần tử `sentences` là `DictationSentenceDto`: `{index, text, startMs, endMs,
  translation}` (`startMs`/`endMs` **null** nếu AI-align chưa chạy được, hoặc không khớp được câu đó
  trong audio; `translation` **null** nếu không có `translationLang` hoặc bằng `"en"`).
- **Lỗi**: `404` nếu `clipId` không tồn tại. Lỗi gọi `ai-service` **không** làm hỏng response — câu đó
  chỉ giữ `null` và được thử lại ở lần gọi sau.

### GET `/api/v1/dictation/clips/{clipId}/audio`

Stream audio của một clip thư viện (đọc từ `StorageClient`; `audio/mpeg` hoặc `audio/wav`).

### POST `/api/v1/dictation/sessions/{userId}`

Bắt đầu một phiên: lấy ngẫu nhiên một loạt clip khớp facets trong request.
- **Request body** — `StartDictationSessionRequest`: `{skill?, level?, topic?, examType?, count=5}`
  (facet nào null = không lọc theo chiều đó).
- **Response `data`** — `DictationClipDto[]` (như trên).

### POST `/api/v1/dictation/attempts`

Chấm điểm bản gõ so với script bằng Word Error Rate (WER, Levenshtein cấp từ), cho **clip thư viện**
hoặc **clip AI-practice**.

- **Request body** — `DictationAttemptRequest`: `userId` (bắt buộc), `userTranscript` (bắt buộc), và
  **đúng một trong** `clipId` / `practiceItemId`; kèm `sentenceMistakes?: DictationSentenceMistake[]`
  (mới) — `{sentenceIndex, expectedText, attemptedText}`, chỉ FE sentence-mode gửi (xem mục "Chép
  chính tả từng câu" bên dưới), null/rỗng với AI-practice hoặc clip không dùng sentence-mode.
- **Xử lý** (`DictationServiceImpl`):
  1. Chấm bằng `DictationScorer` (WER + diff từng từ) trên **toàn bộ `userTranscript`**, lưu
     `dictation_attempts` + từng lỗi vào `dictation_misses`.
  2. Nếu có `sentenceMistakes`, chấm riêng từng cặp `expectedText`/`attemptedText` bằng cùng
     `DictationScorer`, gộp các từ sai vào **cùng danh sách `dictation_misses`** ở bước 1 — đây là
     cách các lần gõ sai khi luyện từng câu (buộc phải gõ đúng mới được sang câu tiếp, xem rev 3 bên
     dưới) vẫn được ghi nhận cho AI dù `userTranscript` cuối cùng đã đúng 100%.
  3. Gọi `DictationAnalyzer` (Gemini nếu `dictation.analyzer.mode=llm`, mặc định rule-based) →
     gợi ý học tập + câu luyện tập, lưu câu luyện vào `dictation_practice_items`.
  4. Publish `learning.gap.analyzed` (các từ sai thành `WeakPointPayload` category `vocabulary`,
     `forgettingScore` bão hòa theo số lần sai) để pipeline gợi ý xử lý.
- **Response `data`** — `DictationAttemptResultDto`: `{referenceText, accuracy, wer, diff[],
  aiSuggestions[], practiceSentences[]}` — không đổi; `accuracy`/`wer`/`diff` vẫn tính trên
  `userTranscript`, không tính `sentenceMistakes`.
- **Lỗi**: `400` nếu thiếu cả `clipId` lẫn `practiceItemId`; `404` nếu id không tồn tại.

### GET `/api/v1/dictation/history/{userId}`

Lịch sử chấm điểm, mới nhất trước — join `dictation_attempts` với `dictation_clips`.
- **Response `data`** — `DictationHistoryEntryDto[]`: `{attemptId, clipId, title, skill, level,
  examType, accuracy, wer, attemptedAt, attemptCount, practiceType}`.
- `attemptCount` — số lần người học đã làm clip này (tính cả lần này), tính bằng window function
  `COUNT(*) OVER (PARTITION BY user_id, clip_id ORDER BY created_at)` trên tất cả các attempts cho
  cùng clip đó; `null` cho các entry AI-practice (không có `clipId`).
- `practiceType` — `LIBRARY` (có `clipId`) hoặc `AI_PRACTICE` (không có, tức là chép từ một câu do
  Gemini sinh ra), tính ngay trong Java (`DictationServiceImpl.getHistory`) từ `clipId`, không cần
  cột DB mới — để FE gắn nhãn "Thư viện" / "Luyện tập với AI" cho từng dòng lịch sử.

### GET `/api/v1/dictation/history/{userId}/{attemptId}`

Chi tiết đầy đủ một lần chấm điểm trong lịch sử: lời thoại gốc, bản gõ của người học, danh sách từ
sai (lấy thẳng từ `dictation_misses`, không tái tạo diff theo vị trí — xem ghi chú bên dưới), và các
gợi ý AI đã sinh ra tại thời điểm đó (`ai_suggestions`, cột mới trên `dictation_attempts`).
- **Response `data`** — `DictationAttemptDetailDto`: `{attemptId, title, skill, level, examType,
  referenceText, userTranscript, accuracy, wer, mistakes: [{expectedWord, actualWord, tag}],
  aiSuggestions[], attemptedAt}`.
- **Lỗi**: `404` nếu `attemptId` không tồn tại hoặc không thuộc về `userId` (scoped ownership check).
- **Vì sao không phải diff đầy đủ theo vị trí**: `dictation_misses` không lưu vị trí từ trong câu, nên
  không dựng lại được đúng mảng diff xen kẽ CORRECT/SUBSTITUTED/MISSING như màn kết quả chấm điểm tức
  thời. Với bài chép từng câu (sentence-mode), bản gõ cuối cùng luôn đúng 100% (bắt buộc sửa đúng mới
  qua câu) nên chấm lại từ đầu (`DictationScorer.score`) sẽ cho ra 0 lỗi — che mất đúng những chỗ người
  học từng sai. Vì vậy màn chi tiết hiển thị danh sách từ sai dạng phẳng từ `dictation_misses`, đúng cho
  cả hai luồng chấm điểm, đổi lại không phải màn hình gạch chân từng từ trong câu.

### GET `/api/v1/dictation/ai-practice/{userId}` · POST `.../ai-practice/{userId}/generate` · GET `.../ai-practice/items/{practiceItemId}/audio`

Phần "Luyện nghe với AI": liệt kê các câu luyện (`DictationPracticeItemDto` = `{practiceItemId,
audioUrl, level, examType, topic}` — `audioUrl` null cho tới khi tổng hợp xong; `level`/`examType`/
`topic` null cho các item sinh ra không qua chọn facet, vd từ một history attempt). `generate` (nút
"Tạo bài luyện") lấy nội dung các practice item còn thiếu audio (`findPracticeItemsWithoutAudio`), hoặc
nếu chưa có item nào thì lấy các từ sai nhiều nhất trong `missWindow` (mặc định 8) attempt gần nhất
(`findTopMissedWords`).
- **Request body** — `GenerateAiPracticeRequest` (mới, optional; thân request có thể bỏ trống hoàn
  toàn để giữ hành vi mặc định cũ): `{level?, examType?, translationLang?}`.
  - `level`/`examType`: một giá trị cụ thể (vd `"B1"`, `"TOEIC"`), chuỗi đặc biệt `"RANDOM"` (server tự
    chọn ngẫu nhiên — `level` từ pool CEFR cố định `A1, A2, B1, B2, C1`; `examType` từ danh sách exam
    type **đang có thật trong thư viện** (`findDistinctExamTypes`), nếu thư viện chưa có exam type nào
    thì fallback về `TOEIC, IELTS, TOEFL, General`), hoặc bỏ trống/null (không ràng buộc — LLM tự chọn
    tự do, đúng hành vi mặc định trước đây). Giá trị đã resolve (không còn `"RANDOM"`) được trả về
    trong `level`/`examType` của mỗi item, để FE luôn biết chính xác cái gì đã được chọn.
  - `translationLang`: ngôn ngữ UI hiện tại; nếu có và khác `"en"`, mỗi dòng thoại được sinh kèm bản dịch.
- **Xử lý**: gọi `LlmDictationDialogueGenerator` (luôn dùng Gemini, không phụ thuộc `dictation.analyzer.mode`)
  để sinh **một đoạn văn luyện tập duy nhất** (độc thoại hoặc hội thoại nhiều speaker) lồng ghép các
  từ/câu mục tiêu, kèm **nhãn chủ đề (`topic`)** do LLM tự đặt và (nếu có `translationLang` hợp lệ) bản
  dịch từng dòng; gán ngẫu nhiên một giọng Supertonic khác nhau cho mỗi speaker; tổng hợp audio từng câu
  thoại qua Supertonic (ai-service) từ **đúng cùng văn bản** sẽ được lưu làm câu chấm điểm/hiển thị (kể
  cả tiền tố `"Speaker: "` nếu hội thoại nhiều speaker — trước đây audio chỉ đọc phần lời thoại còn văn
  bản chấm điểm lại có thêm tiền tố tên, khiến audio hội thoại nhiều speaker không đọc đúng tên người nói
  học sinh được chấm theo; nay đã sửa bằng cách dùng chung một `lineText` cho cả hai), ghép các đoạn WAV
  lại (`WavAudioMerger`) thành một file duy nhất, lưu qua `StorageClient`, rồi **thay thế** các practice
  item còn thiếu audio trước đó bằng đúng một item mới này. Nếu sinh/tổng hợp audio lỗi ở bất kỳ bước
  nào, hệ thống log cảnh báo và giữ nguyên các item cũ để lần gọi sau thử lại. Chép chính tả một clip
  AI-practice cũng dùng chung `POST /attempts`.

### GET `/api/v1/dictation/ai-practice/items/{practiceItemId}/detail`

Mở một bài luyện AI để chép chính tả **theo từng câu**, giống hệt trải nghiệm chép một lesson trong
thư viện (`SentenceDictationRunner` phía FE dùng chung cho cả hai). Trả về `DictationPracticeItemDetailDto
= {practiceItemId, audioUrl, scriptText, level, examType, topic, sentences[]}` — `sentences` được tách
từ `sentenceText` gốc và mang theo bản dịch đã lưu của cả đoạn (nếu có): nếu là hội thoại nhiều speaker
(mỗi dòng dạng `"Speaker: câu"`, xem `synthesizeDialoguePracticeItem`) thì tách theo dòng (`\n`), giữ
nguyên cấu trúc lượt nói; nếu là độc thoại một giọng (không có `\n`) thì tách theo dấu câu kết thúc
(`.`/`!`/`?`) — bản dịch được zip song song theo cùng cách tách câu. Vì audio của cả đoạn đã được ghép
thành **một file WAV duy nhất** (không có timestamp riêng cho từng câu), mọi `sentence.startMs`/`endMs`
luôn là `null` — FE tự ước lượng khoảng thời gian mỗi câu theo tỷ lệ số từ so với tổng thời lượng audio,
đúng cơ chế fallback đã có sẵn cho một clip thư viện chưa chạy AI-alignment (`estimateSentenceRange`).
- **Lỗi**: `404` nếu `practiceItemId` không tồn tại.

### POST `/api/v1/dictation/history/{userId}/{attemptId}/ai-practice`

Nút "Luyện tập với AI" trên một dòng lịch sử cụ thể — khác `generate` ở trên (dùng toàn bộ lịch sử),
endpoint này chỉ phân tích **đúng một attempt** (`attemptId`): lấy các từ sai riêng của attempt đó từ
`dictation_misses` (`findMissesByAttemptId`, khử trùng lặp không phân biệt hoa/thường), rồi gọi
**cùng** `LlmDictationDialogueGenerator` mà `generate` ở trên dùng (trước đây luồng này gọi một
analyzer riêng, `DictationAnalyzer.generatePracticeSentences`, sinh ra nhiều item một-câu-một-item;
nay đã hợp nhất thành một đoạn văn luyện tập duy nhất giống hệt luồng `generate`) để Gemini viết một
đoạn hội thoại/độc thoại nhắm đúng vào những từ đó, tổng hợp audio Supertonic, lưu, rồi trả về toàn bộ
danh sách AI-practice đã làm mới. Khác biệt duy nhất so với `generate`: endpoint này **không có** tham
số chọn `level`/`examType` — cả hai luôn để `null` (generator tự áp dụng mặc định của nó).
- **Query param** — `translationLang?` (mới): giống hệt ý nghĩa ở `generate` — nếu có và khác `"en"`,
  đoạn văn sinh ra kèm bản dịch từng dòng.
- **Response `data`** — `DictationPracticeItemDto[]` (như `generate`).
- **Lỗi**: `404` nếu `attemptId` không tồn tại hoặc không thuộc về `userId`.

### Cơ chế chấm điểm (Java scoring engine, package `common.scoring`)

Từ bản cập nhật này, `forgettingScore`/`weakScore` không còn chỉ là một công thức Ebbinghaus đơn lẻ tính
riêng ở `ai-service` (Python) — nó là kết quả kết hợp 3 mô hình khoa học, tính được ngay trong Java cho
luồng practice/redo (không cần đợi vòng Kafka):

```
forgetting     = 1 - e^(-daysSincePriorReview / halfLifeDays)   # Ebbinghaus, half-life thích ứng kiểu SM-2
                                                                  # thay vì hằng số 7 ngày cố định
masteryGap     = clamp(1 - mastery, 0.15, 1.0)                   # mastery cập nhật bằng Bayesian Knowledge
                                                                  # Tracing (Corbett & Anderson, 1994)
difficultyWeight = clamp(raschWeight(populationStats), 0.5, 2.0) # độ khó kiểu Rasch (1-parameter IRT),
                                                                  # tính từ thống kê đúng/sai toàn bộ learner
recurrenceBoost  = 1.5 nếu item xuất hiện lại (sai) ngay trong cùng batch redo, ngược lại 1.0

weakScore = difficultyWeight × forgetting × masteryGap × recurrenceBoost
```

Lịch ôn tập tiếp theo (`nextReviewAt`, `leitnerBox`) được tính riêng bằng thuật toán Leitner 5 hộp
(đúng → hộp xa hơn, sai → về hộp 1) — trả lời câu hỏi "khi nào cần ôn lại", khác với `weakScore` trả lời
"item nào đang yếu nhất". Chi tiết công thức và các class liên quan: xem `Business.md` mục 10 và
`RemeLearning/common/src/main/java/com/remelearning/common/scoring/`.

`ai-service` nay đã **port công thức tổng hợp này sang Python** (`app/scoring/`, cùng hằng số) và có
`ScoringEngineAnalyzer`. Bật bằng `ANALYSIS_SCORER=scoring-engine` để luồng Kafka `learning.gap.analyzed`
cho điểm nhất quán với luồng Java (trạng thái từng item được gửi kèm trong `learning.gap.analysis.requested`);
mặc định vẫn `rule-based` (Ebbinghaus đơn lẻ) để tương thích ngược. Engine cũng có **nhánh chấm liên tục**
(điểm 0–1, cho `pronunciation`/luyện nói): `scoreAfterContinuousAttempt` + `updateMasteryContinuous`/
`updateHalfLifeContinuous`, quy về đúng bằng nhánh nhị phân tại 0.0/1.0. Ước lượng tham số BKT/Rasch từ
dữ liệu thật: `app/scoring/fit.py` (bộ ước lượng offline đã có test; còn nối vào review log thật).

---

## 2. `user-service` (Java) — REST

Base path: `http://localhost:8081`. Response wrapper giống hệt mục 1 (`ApiResponse<T>`). Spec đầy đủ:
`RemeLearning/services/user-service/openapi.yaml`. **Chưa có enforcement**: các endpoint dưới đây phát
hành JWT thật nhưng không endpoint nào (ở service này hay service khác) kiểm tra token — xem lưu ý ở
đầu tài liệu.

### POST `/api/v1/auth/register`

Đăng ký user mới.

- **Request body** — `RegisterRequest`: `email` (bắt buộc, định dạng email), `password` (bắt buộc, tối
  thiểu 8 ký tự), `name` (bắt buộc).
- **Xử lý**: kiểm tra email trùng, hash password (BCrypt), sinh `userId` (UUID), insert bảng `users`
  (role mặc định `LEARNER`), phát hành JWT qua `common.security.JwtTokenProvider`.
- **Response `data`** — `AuthResponse`:

| Field | Type | Mô tả |
|---|---|---|
| `token` | string | JWT ký HMAC, hết hạn sau `reme.jwt.expiration-minutes` (mặc định 60 phút) |
| `user` | `UserResponse` | `userId, email, name, role, createdAt` |

- **Lỗi**: `409` nếu email đã tồn tại.

### POST `/api/v1/auth/login`

- **Request body** — `LoginRequest`: `email`, `password`.
- **Response `data`**: `AuthResponse` (giống hệt register).
- **Lỗi**: `401` nếu email không tồn tại HOẶC sai password — **cùng một thông báo lỗi cho cả hai
  trường hợp** (không tiết lộ email có tồn tại hay không, đây là chủ đích bảo mật chứ không phải thiếu sót).

### GET `/api/v1/users/{userId}`

Lấy profile theo `userId` (nay có thêm `photoUrl`). **Lỗi**: `404` nếu không tồn tại.

### PATCH `/api/v1/users/{userId}`

Cập nhật `name`. **Request body** — `UpdateProfileRequest`: `name` (bắt buộc). **Lỗi**: `404` nếu
`userId` không tồn tại.

### POST `/api/v1/users/{userId}/photo`

Upload/thay thế ảnh đại diện của một user (`multipart/form-data`) — đây là nguồn ảnh mà `ai-service`
(mục 7) fetch về để enroll khuôn mặt tham chiếu cho tính năng nhận diện người nói.

- **Path param**: `userId` (string)
- **Request** (multipart form): `file` (bắt buộc)
- **Xử lý**: lưu file vào S3 tại key `"{userId}/photo/{originalFilename}"` (bucket cấu hình qua
  `reme.s3.user-photo-bucket`, mặc định `reme-user-photos`), cập nhật `photo_s3_key`/`photo_url` trên
  bảng `users` (migration `V2__user_photo.sql`).
- **Response `data`**: `UserResponse` (kèm `photoUrl` mới).
- **Lỗi**: `400` nếu file rỗng/thiếu, `404` nếu `userId` không tồn tại, `502` nếu upload S3 lỗi.

---

## 3. `recording-service` (Java) — REST

Base path: `http://localhost:8082`. Response wrapper giống hệt mục 1 (`ApiResponse<T>`). Spec đầy đủ:
`RemeLearning/services/recording-service/openapi.yaml`. Đây là điểm vào của toàn bộ pipeline: nhận file ghi âm/
video, lưu lên S3 (MinIO cục bộ), lưu metadata, publish `recording.uploaded` cho `ai-service` (xem mục 7).

### POST `/api/v1/recordings`

Upload một file ghi âm/video (`multipart/form-data`).

- **Request** (multipart form): `file` (bắt buộc), `userId` (bắt buộc), `languageCode` (tùy chọn, mặc định `"en"`)
- **Xử lý**: sinh `recordingId` (UUID), lưu file vào S3 tại key `"{userId}/{recordingId}/{originalFilename}"`
  (bucket cấu hình qua `reme.s3.recording-bucket`, mặc định `reme-recordings`), insert bảng `recordings`
  (status `UPLOADED`), publish Kafka `recording.uploaded`.
- **Response `data`** — `RecordingResponse`:

| Field | Type | Mô tả |
|---|---|---|
| `recordingId` | string | UUID sinh bởi service |
| `userId` | string | |
| `status` | string | `UPLOADED` |
| `s3Bucket` | string | |
| `s3Key` | string | |
| `createdAt` | Instant | |

- **Lỗi**: `400` nếu `userId` rỗng/thiếu hoặc file rỗng.

### GET `/api/v1/recordings/{recordingId}`

Lấy metadata một recording theo id. **Lỗi**: `404` nếu không tồn tại.

### GET `/api/v1/recordings/user/{userId}`

Danh sách tất cả recording của một user — `RecordingResponse[]`.

---

## 4. `recommendation-service` (Java) — REST

Base path: `http://localhost:8086`. Response wrapper giống mục 1. Spec đầy đủ:
`RemeLearning/services/recommendation-service/openapi.yaml`. Service này consume `learning.gap.analyzed`
(mục 8) không lọc theo category (khác `english-service`, vốn mỗi domain chỉ giữ category của mình) — mọi
weak point từ mọi domain (vocabulary/grammar/pronunciation) đều được lưu thành một "recommendation".

Với mỗi weak point, service còn gọi `ExerciseGenerator` (package `recommendation.exercise`) để sinh
danh sách bài tập cụ thể (`exercises`) — một component duy nhất, không phân biệt category, dùng
chung cho vocabulary/grammar/pronunciation và bất kỳ category nào thêm sau này. Mặc định
(`recommendation.exercise-generator.mode=rule-based`) trả về template tĩnh theo từng category
(tương tự `recommendation` text của ai-service). Khi bật `mode=llm` (+ `GEMINI_API_KEY` thật), gọi
Gemini qua `common`'s `LlmClient` để sinh 3-5 bài tập cụ thể bằng tiếng Việt; nếu lời gọi LLM lỗi
hoặc trả JSON không hợp lệ, tự động fallback về template tĩnh (không làm hỏng luồng ingest).

### GET `/api/v1/recommendations/{userId}`

Danh sách gợi ý học tập của một learner, sắp giảm dần theo `forgettingScore`.

- **Path param**: `userId` (string)
- **Query param** (tùy chọn): `category` — `"vocabulary" | "grammar" | "pronunciation"` (string thô, không phải enum riêng — giá trị đến trực tiếp từ `WeakPointPayload.category` của `learning.gap.analyzed`)
- **Response `data`** — `Recommendation[]`:

| Field | Type | Mô tả |
|---|---|---|
| `id` | long | |
| `userId` | string | |
| `recordingId` | string | recording gần nhất sinh ra gợi ý này |
| `itemId` | string | |
| `category` | string | `vocabulary`/`grammar`/`pronunciation` |
| `label` | string | |
| `forgettingScore` | double | |
| `recommendationText` | string | |
| `exercises` | string[] | bài tập cụ thể, sinh bởi `ExerciseGenerator` (rule-based hoặc Gemini, xem trên) |
| `updatedAt` | Instant | |

### GET `/api/v1/recommendations/{userId}/grouped`

Giống endpoint trên nhưng nhóm theo `category` — `Map<String, Recommendation[]>`.

---

## 5. `dashboard-service` (Java) — REST

Base path: `http://localhost:8087`. Response wrapper giống mục 1. Spec đầy đủ:
`RemeLearning/services/dashboard-service/openapi.yaml`. Service này KHÔNG gọi REST sang service khác — toàn
bộ dữ liệu đến từ hai Kafka topic nó tự consume (`learning.gap.analyzed`, `recommendation.generated` — mục 9).

### GET `/api/v1/dashboard/{userId}`

Tổng hợp tiến độ học tập của một learner trên cả 3 domain + các gợi ý gần nhất.

- **Path param**: `userId` (string)
- **Response `data`** — `DashboardSummaryResponse`:

| Field | Type | Mô tả |
|---|---|---|
| `userId` | string | |
| `categoryProgress` | `CategoryProgress[]` | Tính tại thời điểm đọc (SQL `GROUP BY category`), không phải counter duy trì sẵn |
| `recentRecommendations` | `RecommendationSnapshot[]` | Top 10 theo `receivedAt` giảm dần |

`CategoryProgress`:

| Field | Type | Mô tả |
|---|---|---|
| `category` | string | |
| `weakPointCount` | long | `COUNT(*)` từ `weak_points_snapshot` |
| `avgForgettingScore` | double | `AVG(forgetting_score)` |
| `lastUpdated` | Instant | `MAX(updated_at)` |

`RecommendationSnapshot`:

| Field | Type | Mô tả |
|---|---|---|
| `userId` | string | |
| `itemId` | string | |
| `category` | string | |
| `label` | string | |
| `recommendationText` | string | |
| `exercises` | string[] | copy từ `RecommendationPayload.exercises` của `recommendation.generated` |
| `forgettingScore` | double | |
| `receivedAt` | Instant | |

---

## 6. `bff-service` (Java) — REST

Base path: `http://localhost:8080`. Reactive (WebFlux) — mọi endpoint trả `Mono<ApiResponse<T>>`, response
wrapper giống hệt mục 1 (`ApiResponse<T>`, tái dùng thẳng class trong `common`). Spec đầy đủ:
`RemeLearning/services/bff-service/openapi.yaml`. bff-service không có DB, không tự lưu trạng thái — mọi
endpoint bên dưới gọi sang service khác qua `WebClient` (cấu hình base URL ở `reme.services.*`,
`WebClientConfig`/`DownstreamServicesProperties`). Với các endpoint fan-out (gọi song song nhiều service),
mỗi nhánh gọi được bọc `.onErrorResume(...)` — nếu một downstream service down, response vẫn trả về đầy đủ
với phần dữ liệu của nhánh lỗi để rỗng/mặc định thay vì làm hỏng toàn bộ request.

### POST `/api/v1/auth/register` và POST `/api/v1/auth/login`

Proxy thuần túy sang `POST /api/v1/auth/register`/`login` của `user-service` (mục 2) — client chỉ cần
biết một base URL (8080) cho toàn bộ luồng auth. Request/response body giống hệt user-service
(`RegisterRequest`/`LoginRequest` → `AuthResponse`). bff-service không tự validate lại (validation xảy
ra ở user-service).

### GET `/api/v1/users/{userId}` và PATCH `/api/v1/users/{userId}`

Proxy thuần túy sang `user-service` (mục 2) — cùng field, cùng lỗi (404).

### POST `/api/v1/recordings`

Proxy thuần túy (multipart streaming, không buffer file trong bff-service) sang `POST /api/v1/recordings`
của `recording-service` (mục 3) — client chỉ cần biết một base URL (8080) cho toàn bộ luồng upload.

- **Request** (multipart form): `file`, `userId`, `languageCode` (tùy chọn) — giống hệt recording-service.
- **Response `data`**: `RecordingDto` — giống `RecordingResponse` của recording-service.

### GET `/api/v1/learners/{userId}/overview`

Composite: fan-out song song 3 chiều sang `user-service` (mục 2, profile), `GET /api/v1/dashboard/{userId}`
(dashboard-service, mục 5), và `GET /api/v1/recordings/user/{userId}` (recording-service, mục 3), gộp
thành một response cho màn hình tổng quan của learner. Đây là lần đầu tiên `userServiceClient` (tồn tại
từ lúc bff-service còn là skeleton) được dùng thật.

- **Response `data`** — `LearnerOverviewResponse`:

| Field | Type | Mô tả |
|---|---|---|
| `userId` | string | |
| `user` | `UserDto` (nullable) | profile từ user-service; `null` nếu user-service down — không làm hỏng cả response |
| `categoryProgress` | `CategoryProgressDto[]` | từ dashboard-service |
| `recentRecommendations` | `RecommendationSnapshotDto[]` | từ dashboard-service |
| `recentRecordings` | `RecordingDto[]` | từ recording-service |

### GET `/api/v1/learners/{userId}/weak-points`

Composite: fan-out song song sang cả 3 endpoint weak-points của `english-service` (mục 1) —
`/api/v1/vocabulary/weak-points/{userId}`, `/grammar/...`, `/pronunciation/...` — gộp theo `category` thành
một view duy nhất (english-service không tự gộp giữa 3 domain của chính nó; đây là nơi đầu tiên có view đó).

- **Response `data`**: `Map<string category, WeakPointDto[]>` — key là `"vocabulary"`/`"grammar"`/`"pronunciation"`.
  `WeakPointDto`: `itemId, label, category, forgettingScore, recommendation` (`category` được bff-service tự
  gán theo endpoint nguồn, vì response gốc của english-service không có field này).

### GET `/api/v1/learners/{userId}/recommendations`

Proxy thuần túy (không cần gộp, recommendation-service đã group sẵn) sang
`GET /api/v1/recommendations/{userId}/grouped` (recommendation-service, mục 4).

- **Response `data`**: `Map<string category, RecommendationDto[]>`.

### GET `/api/v1/learners/{userId}/practice/next`

Bộ "bài tập cần làm lại": gộp 3 endpoint weak-points của english-service (giống
`GET .../weak-points` ở trên, tái dùng cùng `WeakPointAggregationService`), rồi sắp giảm dần theo
`forgettingScore` trên toàn bộ 3 domain và lấy top `limit` — đây là danh sách item học lại có mức độ
quên cao nhất, dùng để dựng bài tập redo cho learner.

- **Query param** (tùy chọn): `limit` — mặc định `10`
- **Response `data`**: `WeakPointDto[]` (giống cấu trúc mục trên, đã gộp `category`), sắp giảm dần theo
  `forgettingScore`.

### POST `/api/v1/learners/{userId}/practice/redo`

Proxy thuần túy sang `POST /api/v1/practice/redo` của `english-service` (mục 1) — client chỉ cần biết
một base URL (8080) cho toàn bộ luồng làm lại bài tập. `userId` trong path được gán đè lên body trước khi
proxy, nên client không cần lặp lại nó trong `PracticeRedoRequestDto`.

- **Request body** — `PracticeRedoRequestDto`: `attempts: PracticeAttemptDto[]` (`itemId`, `category`,
  `label`, `correct`) — giống hệt `PracticeRedoRequest` của english-service.
- **Response `data`**: `null`.

### Dictation — proxy sang `english-service` (mục 1)

Tất cả là proxy thuần túy; endpoint audio **relay nguyên** luồng bytes (status/headers/body) từ
english-service qua `WebClient.toEntityFlux(DataBuffer)`.

- **GET `/api/v1/learners/{userId}/dictation/facets`** → `DictationFacetsDto` (nay có thêm
  `minListensForHint`).
- **GET `/api/v1/learners/{userId}/dictation/clips`** (query `skill?/level?/topic?/examType?/limit`) →
  `DictationClipDto[]`.
- **GET `/api/v1/learners/{userId}/dictation/folders`** (rev 2) → `DictationFolderDto[]`
  `{folderId, name, lessonCount}`.
- **GET `/api/v1/learners/{userId}/dictation/folders/{folderId}/lessons`** (rev 2) →
  `DictationLessonSummaryDto[]` `{clipId, code, title, audioUrl}` (không script).
- **GET `/api/v1/learners/{userId}/dictation/clips/{clipId}`** (rev 2, query `translationLang?` mới) →
  `DictationClipDetailDto` `{clipId, code, title, audioUrl, scriptText, sentences[]}` (mỗi `sentence`
  nay có thêm `translation?`, dịch lazy khi `translationLang` khác `"en"`) — `404` nếu không tồn tại.
- **GET `/api/v1/learners/{userId}/dictation/clips/{clipId}/audio`** → stream audio.
- **POST `/api/v1/learners/{userId}/dictation/sessions`** — body `StartDictationSessionRequestDto`
  `{skill?, level?, topic?, examType?, count=5}` → `DictationClipDto[]`.
- **POST `/api/v1/learners/{userId}/dictation/attempts`** — `userId` gán đè lên body; body
  `DictationAttemptRequestDto` `{clipId?, practiceItemId?, userTranscript, sentenceMistakes?}` (proxy
  nguyên vẹn, xem mục sentence-mode bên dưới) → `DictationAttemptResultDto`
  `{referenceText, accuracy, wer, diff[], aiSuggestions[], practiceSentences[]}`.
- **GET `/api/v1/learners/{userId}/dictation/history`** → `DictationHistoryEntryDto[]`
  `{attemptId, clipId, title, skill, level, examType, accuracy, wer, attemptedAt, attemptCount, practiceType}`.
  `attemptCount` — số lần đã làm bài này (tính cả lần này), `null` cho bài AI-practice. `practiceType`
  — `LIBRARY`/`AI_PRACTICE`, để FE gắn nhãn "Thư viện"/"Luyện tập với AI" từng dòng lịch sử.
- **GET `/api/v1/learners/{userId}/dictation/history/{attemptId}`** → `DictationAttemptDetailDto`
  (như trên) — thin proxy sang `GET /api/v1/dictation/history/{userId}/{attemptId}`.
- **POST `/api/v1/learners/{userId}/dictation/history/{attemptId}/ai-practice`** (query
  `translationLang?` mới) — nút "Luyện tập với AI" trên một dòng lịch sử — thin proxy sang
  `POST /api/v1/dictation/history/{userId}/{attemptId}/ai-practice` → `DictationPracticeItemDto[]`.
- **GET `/api/v1/learners/{userId}/dictation/ai-practice`** · **POST `.../ai-practice/generate`**
  (body `GenerateAiPracticeRequestDto` `{level?, examType?, translationLang?}` mới, optional) →
  `DictationPracticeItemDto[]` `{practiceItemId, audioUrl, level, examType, topic}`.
- **GET `/api/v1/learners/{userId}/dictation/ai-practice/items/{practiceItemId}/audio`** → stream WAV.
- **GET `/api/v1/learners/{userId}/dictation/ai-practice/items/{practiceItemId}/detail`** — thin proxy
  sang `GET /api/v1/dictation/ai-practice/items/{practiceItemId}/detail` → `DictationPracticeItemDetailDto`
  `{practiceItemId, audioUrl, scriptText, level, examType, topic, sentences[]}` (mỗi `sentence` nay có
  thêm `translation?`), mở bài luyện AI để chép chính tả theo từng câu (xem mục english-service ở trên).

---

## 7. `ai-service` (Python) — REST

Base path: `http://localhost:8000` (mặc định FastAPI/uvicorn). Không có OpenAPI tĩnh check-in — Swagger tự
sinh tại `/docs` khi service chạy (`uvicorn app.main:app --reload`).

`ai-service` nay có DB riêng lần đầu tiên: Postgres `reme_ai`, schema `ai` (không dùng schema mặc định
`public`, theo đúng convention của feature này) — SQLAlchemy + Alembic (`ai-service/migrations/`), tương
đương Flyway ở phía Java. Ba bảng: `known_faces` (embedding khuôn mặt đã enroll), `face_recognition_results`,
`voice_authenticity_results` (kết quả theo từng recording, xem chi tiết ở
[flow/ai-service-data-flow.md](flow/ai-service-data-flow.md)).

### GET `/health`

Liveness check. Response: `{"status": "ok"}`.

### POST `/api/v1/transcribe`

Chạy STT + speaker diarization đồng bộ cho một recording đã có trong S3 (bản đồng bộ của luồng Kafka
`recording.uploaded` → `transcript.ready`).

- **Request body** — `RecordingUploadedEvent`:

| Field | Type | Mô tả |
|---|---|---|
| `recording_id` | string | |
| `user_id` | string | |
| `s3_bucket` | string | |
| `s3_key` | string | |
| `language_code` | string \| null | mặc định `"en"`. `null`/bỏ trống = tự động nhận diện ngôn ngữ theo từng speaker turn (xem bên dưới) |

- **Response** — `TranscriptionResult`:

| Field | Type | Mô tả |
|---|---|---|
| `full_text` | string | |
| `segments` | `Segment[]` | `speaker`, `text`, `start_seconds`, `end_seconds`, `language` |
| `recording_id` | string \| null | echo lại `recording_id` của request |
| `speaker_identities` | `SpeakerIdentity[]` | rỗng trừ khi `FACE_RECOGNITION_ENABLED=true`; xem bên dưới |
| `voice_authenticity` | `SegmentAuthenticity[]` | rỗng trừ khi `VOICE_AUTHENTICITY_ENABLED=true`; xem bên dưới |

Diarize (`DiarizationEngine.diarize`) chạy **trước**, sau đó mỗi speaker turn được cắt riêng
(`slice_wav`) và transcribe **song song** qua `ThreadPoolExecutor`
(`app/stt/pipeline.py::transcribe_turns_multilingual`, số worker cấu hình qua
`STT_MAX_CONCURRENT_TRANSCRIPTIONS`, mặc định 4). Nếu `language_code` là `null`/bỏ trống, mỗi turn tự
động nhận diện ngôn ngữ riêng (`FasterWhisperEngine.transcribe_auto`) — nhờ vậy một recording có nhiều
speaker nói các ngôn ngữ khác nhau vẫn được phân tích đúng cho từng người, thay vì ép toàn bộ file theo
một ngôn ngữ duy nhất. Nếu truyền `language_code` cụ thể, ngôn ngữ đó được ép dùng cho mọi turn (bỏ qua
bước tự nhận diện, vẫn chạy song song). Nếu diarization không phát hiện speech nào, toàn bộ file được
xử lý như một turn duy nhất.

Khi `VISION_ENABLED=true` (mặc định `false`, xem `app/config.py`), sau STT + diarization, service còn
sample frame từ video gốc mỗi `VISION_FRAME_INTERVAL_SECONDS` giây (mặc định 10s, `app/vision/
frame_extractor.py`, dùng PyAV) và gọi Gemini `generateContent` (image inline, `app/vision/
gemini_vision_engine.py`) để sinh caption mô tả nội dung hình ảnh (vật thể, hành động, chữ xuất hiện
trên màn hình). Mỗi caption được gộp vào `segments` như một `Segment` với `speaker == "vision"`
(`app/vision/pipeline.py`), theo thứ tự `start_seconds`, để `RuleBasedAnalyzer`/`MistakeAnalyzer` phân
tích từ vựng hay quên có thể tham chiếu cả nội dung nói lẫn nội dung hình ảnh xuất hiện trong video.
Cấu hình Gemini vision qua `GEMINI_API_KEY` / `GEMINI_VISION_MODEL` (mặc định `gemini-2.0-flash`).

Khi `FACE_RECOGNITION_ENABLED=true` (mặc định `false`), sau diarization, mỗi `SpeakerTurn` được lấy
mẫu vài frame video (`FACE_FRAMES_PER_TURN`, `app/vision/frame_extractor.py::extract_frames_in_range`),
detect khuôn mặt + tính embedding ArcFace (`app/face/insightface_engine.py`, model `buffalo_l` qua
`insightface`/`onnxruntime`), so khớp (cosine similarity, ngưỡng `FACE_MATCH_SIMILARITY_THRESHOLD`,
mặc định 0.45) với danh sách khuôn mặt đã enroll (`app/face/enrollment.py`, bảng `ai.known_faces`) —
kết quả (`speaker_identities`) vừa được trả về trong response vừa lưu vào `ai.face_recognition_results`.
**Lưu ý về độ chính xác**: đây là "khuôn mặt nào xuất hiện nhiều nhất khi SPEAKER_XX đang nói", KHÔNG
phải active-speaker detection thật (không phân tích cử động môi) — hoạt động tốt với layout
single-face/speaker-view, yếu hơn với layout gallery-view nhiều khuôn mặt cùng lúc (xem
[flow/ai-service-data-flow.md](flow/ai-service-data-flow.md)).

Khi `VOICE_AUTHENTICITY_ENABLED=true` (mặc định `false`), mỗi `SpeakerTurn` còn được chấm bằng
`HeuristicVoiceAuthenticityAnalyzer` (`app/voice_auth/heuristic_analyzer.py`) — phân biệt giọng người
thật với giọng máy/tổng hợp (TTS bot, phát lại ghi âm — hữu ích khi ghi âm buổi học qua Microsoft
Teams/Google Meet có thể có bot tham gia) dựa trên đặc trưng âm học thô (pitch jitter, shimmer,
spectral flatness, độ đều của khoảng lặng — qua `librosa`), **KHÔNG phải model đã huấn luyện** — xem
docstring của module đó để biết giới hạn. Kết quả (`voice_authenticity`) vừa trả về vừa lưu vào
`ai.voice_authenticity_results`.

### POST `/api/v1/upload`

Nhận trực tiếp file audio/video qua `multipart/form-data` — dùng khi chưa wire Kafka/S3.

- **Request** (multipart form):
  - `file`: `UploadFile` (bắt buộc)
  - `language_code`: string \| null, mặc định `null` (bỏ trống = tự động nhận diện ngôn ngữ theo
    từng speaker turn, song song — xem chi tiết ở `/api/v1/transcribe` bên trên; endpoint này dùng
    chung logic `transcribe_turns_multilingual`)
- **Response**: `TranscriptionResult` (giống endpoint trên, cũng gộp caption hình ảnh/nhận diện khuôn
  mặt/kiểm tra giọng nói nếu các flag tương ứng bật). `recording_id` được **sinh mới** (UUID) vì upload
  ad-hoc không có sẵn recordingId — dùng để tra lại `ai.face_recognition_results`/
  `ai.voice_authenticity_results`.

File upload được đọc theo chunk (`UPLOAD_CHUNK_SIZE_BYTES`, mặc định 1 MiB) qua `UploadFile.read(size)`
và ghi tuần tự xuống temp file, thay vì gọi `file.read()` không tham số (buffer toàn bộ file vào RAM
một lần) — nhờ vậy dung lượng RAM dùng không phụ thuộc kích thước file upload.

### POST `/api/v1/dictation/align-sentences`

Khớp các câu trong script (thứ tự cố định) với mốc thời gian trong audio của chính clip đó — dùng bởi
`english-service`'s `GET /api/v1/dictation/clips/{clipId}` (mục 1) khi phát hiện câu nào còn thiếu
`startMs`/`endMs`.

- **Request** (multipart form):
  - `audio`: `UploadFile` (bắt buộc) — file audio của clip
  - `sentences`: string (bắt buộc) — mảng JSON các câu theo đúng thứ tự `seq` (multipart không có
    kiểu list gốc nên phải JSON-encode)
- **Xử lý**: transcribe `audio` bằng `FasterWhisperEngine.transcribe_words` (Whisper với
  `word_timestamps=True`) để lấy danh sách từ kèm mốc thời gian, rồi `app/align/sentence_aligner.py`
  khớp tuần tự từng câu với dòng từ đó theo thứ tự (câu sau không được dùng lại từ câu trước đã khớp).
  Câu nào Whisper không nhận ra được (từ đầu câu không xuất hiện trong phần audio còn lại) trả về
  `null`/`null` thay vì đoán bừa.
- **Response**: mảng `SentenceTimingResponse` — cùng thứ tự/độ dài với `sentences` — mỗi phần tử
  `{start_ms, end_ms}` (mili-giây, `null` nếu không khớp được).

### POST `/api/v1/faces/enroll`

Enroll một `user_id` làm khuôn mặt đã biết, dùng để nhận diện người nói ở `/api/v1/upload`/
`/api/v1/transcribe` khi `FACE_RECOGNITION_ENABLED=true`.

- **Request** (multipart form): `user_id` (bắt buộc), `name` (tùy chọn — nếu bỏ trống và fetch được từ
  user-service thì dùng `name` của user-service), `image` (tùy chọn — file ảnh trực tiếp, dùng để test
  cục bộ không cần user-service đang chạy)
- **Xử lý**: nếu không truyền `image`, gọi `GET /api/v1/users/{userId}` (mục 2) lấy `photoUrl` rồi tải
  ảnh về — đây là luồng chính, đúng như yêu cầu "ảnh lấy từ user-service". Detect khuôn mặt (giữ khuôn
  mặt có độ tin cậy detect cao nhất nếu ảnh có nhiều khuôn mặt), tính embedding, upsert vào `ai.known_faces`
  (khóa `user_id`).
- **Response `data`** — `EnrolledFaceResponse`: `user_id`, `name` (không trả embedding thô).
- **Lỗi**: `422` nếu không detect được khuôn mặt nào trong ảnh; `404` nếu `user_id` không tồn tại ở
  user-service (và không truyền `image` trực tiếp).

### GET `/api/v1/faces`

Danh sách toàn bộ khuôn mặt đã enroll — dùng để xác nhận enrollment trước khi test `/api/v1/upload`.

- **Response `data`**: `EnrolledFaceResponse[]`.

### POST `/api/v1/analyze`

Chạy phân tích lỗi/quên (forgetting pattern) đồng bộ (bản đồng bộ của luồng Kafka
`learning.gap.analysis.requested` → `learning.gap.analyzed`).

- **Request body** — `AnalysisRequestedEvent`:

| Field | Type | Mô tả |
|---|---|---|
| `recording_id` | string | |
| `user_id` | string | |
| `segments` | `Segment[]` | |
| `history` | `MistakeHistoryItem[]` | `item_id`, `category`, `label`, `occurrence_count`, `last_seen_days_ago` |

- **Response** — `LearningGapAnalyzedEvent`:

| Field | Type | Mô tả |
|---|---|---|
| `recording_id` | string | |
| `user_id` | string | |
| `weak_points` | `WeakPoint[]` | `item_id`, `category`, `label`, `forgetting_score`, `recommendation` |

Xử lý bằng `RuleBasedAnalyzer` (`app/analysis/rule_based_analyzer.py`), không publish Kafka.

---

## 8. Kafka — `english-service` (consumer/producer)

Payload từ ai-service là JSON snake_case thô (pydantic `model_dump()`, không có envelope) — decode bằng
`EventCodec` (Jackson snake_case dedicated mapper, một bản riêng cho mỗi domain package), không dùng
`ObjectMapper` mặc định (camelCase) của REST.

Cả ba domain đều lắng nghe topic `learning.gap.analyzed`, mỗi domain filter theo `category` của riêng
mình và bỏ qua các category khác. **Quan trọng**: ba `@KafkaListener` này dùng ba `groupId` khác nhau
(`english-service`, `english-service-grammar`, `english-service-pronunciation`) dù cùng consume một
topic — nếu dùng chung một `groupId`, Kafka sẽ chia partition cho từng consumer trong group thay vì
phát cho tất cả, khiến mỗi domain chỉ nhận được một phần message thay vì toàn bộ. `recommendation-service`
(mục 10) và `dashboard-service` (mục 11) cũng consume topic này, mỗi bên với `groupId` riêng của mình.

Chỉ `vocabulary` có `TranscriptReadyConsumer`; `grammar`/`pronunciation` không tự ingest `transcript.ready`
lần hai — bảng `transcripts`/`transcript_segments` là dữ liệu dùng chung, được `vocabulary` lưu một lần
và cả ba domain đều đọc lại qua `GET /api/v1/transcripts/{recordingId}` khi cần.

### `TranscriptReadyConsumer` — topic `transcript.ready` (domain `vocabulary`)

- File: `english-service/.../vocabulary/kafka/TranscriptReadyConsumer.java`
- `groupId`: `english-service`
- Payload — `TranscriptReadyEvent`: `recordingId`, `userId`, `fullText`, `segments: SegmentPayload[]`
  (`speaker`, `text`, `startSeconds`, `endSeconds`, `language` — ngôn ngữ được ai-service tự nhận
  diện/ép dùng cho từng speaker turn, xem mục 7)
- Xử lý: `TranscriptService.saveTranscript(event)` — idempotent (bỏ qua nếu `recordingId` đã tồn tại, do
  Kafka at-least-once). Lỗi decode/xử lý được log, không rethrow.

### `LearningGapAnalyzedConsumer` — topic `learning.gap.analyzed` (domain `vocabulary`)

- File: `english-service/.../vocabulary/kafka/LearningGapAnalyzedConsumer.java`
- `groupId`: `english-service`
- Payload — `LearningGapAnalyzedEvent`: `recordingId`, `userId`, `weakPoints: WeakPointPayload[]`
  (`itemId`, `category`, `label`, `forgettingScore`, `recommendation`)
- Xử lý: `VocabularyWeakPointService.saveWeakPoints(event)` — chỉ giữ item có `category == "vocabulary"`;
  phân loại `VocabularyType` qua `VocabularyClassifier` (rule-based hoặc LLM). Lỗi được log, không rethrow.

### `LearningGapAnalyzedConsumer` — topic `learning.gap.analyzed` (domain `grammar`)

- File: `english-service/.../grammar/kafka/LearningGapAnalyzedConsumer.java`
- `groupId`: `english-service-grammar`
- Payload: giống hệt bản của `vocabulary` (bản sao DTO riêng trong package `grammar.event`)
- Xử lý: `GrammarWeakPointService.saveWeakPoints(event)` — chỉ giữ item có `category == "grammar"`;
  phân loại `GrammarType` qua `GrammarClassifier` (rule-based hoặc LLM). Lỗi được log, không rethrow.

### `LearningGapAnalyzedConsumer` — topic `learning.gap.analyzed` (domain `pronunciation`)

- File: `english-service/.../pronunciation/kafka/LearningGapAnalyzedConsumer.java`
- `groupId`: `english-service-pronunciation`
- Payload: giống hệt bản của `vocabulary` (bản sao DTO riêng trong package `pronunciation.event`)
- Xử lý: `PronunciationWeakPointService.saveWeakPoints(event)` — chỉ giữ item có
  `category == "pronunciation"`; phân loại `PronunciationType` qua `PronunciationClassifier` (rule-based
  hoặc LLM). Lỗi được log, không rethrow.

### `MistakeHistorySeedConsumer` — topic `learning.gap.analyzed` (domain `practice`)

- File: `english-service/.../practice/kafka/MistakeHistorySeedConsumer.java`
- `groupId`: `english-service-practice` (riêng, giống 2 domain kia — nếu không sẽ chia sẻ partition thay
  vì nhận toàn bộ message)
- Payload: giống hệt các consumer khác của topic này
- Xử lý: **không lọc category** (khác 3 domain kia, giống recommendation-service/dashboard-service) —
  mọi `WeakPointPayload` được `INSERT ... ON CONFLICT (user_id, item_id) DO NOTHING` vào `mistake_history`
  qua `MistakeHistoryMapper.seedIfAbsent`, chỉ khi item chưa từng có lịch sử (seed một lần duy nhất; các
  lần sau chỉ được cập nhật bởi `PracticeService.redo` khi learner thực sự làm lại bài, không bị consumer
  này ghi đè/đếm trùng). Lỗi được log, không rethrow.

### `AnalysisRequestedProducer` — topic `learning.gap.analysis.requested` (domain `practice`)

- File: `english-service/.../practice/kafka/AnalysisRequestedProducer.java`
- **Publish khi**: `PracticeService.redo(...)` xử lý xong một `POST /api/v1/practice/redo` (mục 1) — một
  lần publish cho cả batch attempt vừa chấm, mang theo toàn bộ `mistake_history` hiện tại của learner đó
  (không chỉ các item vừa submit).
- **Key**: `recordingId` (giá trị sinh dạng `"practice-<uuid>"`, không gắn với recording thật nào)
- Payload — `AnalysisRequestedEvent`, serialize snake_case qua `EventCodec` (giống
  `RecordingUploadedProducer`, mục 9 — KHÔNG dùng `common.EventPublisher`/`BaseEvent`, vì event này băng
  qua biên Java↔Python, phải khớp đúng pydantic model mà `ai-service` parse, mục 7/12):

| Field JSON | Field Java | Mô tả |
|---|---|---|
| `recording_id` | `recordingId` | |
| `user_id` | `userId` | |
| `segments` | `segments` | luôn rỗng — không có transcript trong luồng redo-exercise |
| `history` | `history` | `MistakeHistoryItemPayload[]`: `itemId`, `category`, `label`, `occurrenceCount`, `lastSeenDaysAgo` (tính từ `mistake_history.last_seen_at` tới thời điểm publish) |

Đây chính là producer còn thiếu của `learning.gap.analysis.requested` (xem mục 12, 14) — trước đây chỉ có
consumer (`ai-service`) mà không service Java nào publish topic này.

---

## 9. Kafka — `recording-service` (producer)

### `RecordingUploadedProducer` — topic `recording.uploaded`

- File: `recording-service/.../recording/kafka/RecordingUploadedProducer.java`
- **Publish khi**: `POST /api/v1/recordings` upload thành công lên S3 và insert xong bảng `recordings`.
- **Key**: `recordingId`
- Payload — `RecordingUploadedEvent`, serialize snake_case qua `EventCodec` riêng của service (KHÔNG dùng
  `common`'s `EventPublisher`/`BaseEvent` — event này băng qua biên Java↔Python nên phải là JSON snake_case
  phẳng, không có envelope, để khớp đúng pydantic model `RecordingUploadedEvent` mà `ai-service` parse
  (mục 7)):

| Field JSON | Field Java | Mô tả |
|---|---|---|
| `recording_id` | `recordingId` | |
| `user_id` | `userId` | |
| `s3_bucket` | `s3Bucket` | |
| `s3_key` | `s3Key` | |
| `language_code` | `languageCode` | |

---

## 10. Kafka — `recommendation-service` (consumer/producer)

### `LearningGapAnalyzedConsumer` — topic `learning.gap.analyzed` (consume)

- File: `recommendation-service/.../recommendation/kafka/LearningGapAnalyzedConsumer.java`
- `groupId`: `recommendation-service` (riêng, không dùng chung với các consumer khác của cùng topic — xem
  lưu ý ở mục 6)
- Payload — `LearningGapAnalyzedEvent` (bản sao DTO snake_case riêng, giống hệt `vocabulary.event.*`)
- Xử lý: `RecommendationService.handleLearningGapAnalyzed(event)` — **không lọc theo category** (khác
  `english-service`), mọi `WeakPointPayload` đều được upsert vào bảng `recommendations` (khóa
  `(user_id, item_id)`). Trước khi upsert, gọi `ExerciseGenerator.generate(category, label,
  forgettingScore)` **một lần** cho mỗi weak point để sinh `exercises`, rồi dùng chung kết quả đó cho
  cả row lưu DB lẫn payload publish ra (gọi LLM hai lần cho cùng một weak point có thể ra hai danh
  sách bài tập khác nhau, vì Gemini không xác định — xem mục 4). Sau khi upsert xong, publish
  `recommendation.generated`. Lỗi được log, không rethrow.

### `RecommendationGeneratedEvent` — topic `recommendation.generated` (produce)

- File: `recommendation-service/.../recommendation/event/RecommendationGeneratedEvent.java`
- **Publish khi**: sau khi xử lý xong một `learning.gap.analyzed` event (một lần publish cho cả batch weak
  point vừa nhận, không phải một event/item).
- **Key**: `userId`
- Đây là event Java↔Java thuần túy (producer và consumer — dashboard-service, mục 9 — đều là Java), nên
  dùng thẳng `common`'s `EventPublisher`/`BaseEvent`/`KafkaEventPublisher` (lần đầu tiên hạ tầng này thực sự
  được dùng trong repo) — JSON camelCase mặc định, có envelope:

| Field | Type | Mô tả |
|---|---|---|
| `eventId` | string | từ `BaseEvent`, UUID tự sinh |
| `eventType` | string | `"recommendation.generated"` |
| `occurredAt` | Instant | từ `BaseEvent`, tự sinh |
| `recordingId` | string | |
| `userId` | string | |
| `recommendations` | `RecommendationPayload[]` | `itemId`, `category`, `label`, `recommendationText`, `exercises[]`, `forgettingScore` |

---

## 11. Kafka — `dashboard-service` (consumer)

Cả hai consumer dưới đây dùng `groupId`: `dashboard-service` (riêng, không dùng chung với consumer nào khác).
Không có producer — dashboard-service chỉ là read model.

### `LearningGapAnalyzedConsumer` — topic `learning.gap.analyzed`

- File: `dashboard-service/.../dashboard/kafka/LearningGapAnalyzedConsumer.java`
- Payload — `LearningGapAnalyzedEvent` (bản sao DTO snake_case, giống `vocabulary.event.*`), decode qua
  `EventCodec` riêng của service.
- Xử lý: mọi `WeakPointPayload` (không lọc category) được upsert vào bảng `weak_points_snapshot` (khóa
  `(user_id, item_id)`) — nguồn cho `categoryProgress` ở `GET /api/v1/dashboard/{userId}` (mục 5).

### `RecommendationGeneratedConsumer` — topic `recommendation.generated`

- File: `dashboard-service/.../dashboard/kafka/RecommendationGeneratedConsumer.java`
- Payload — `RecommendationGeneratedEvent` (bản sao camelCase, khớp với event Java↔Java ở mục 8), decode
  bằng `ObjectMapper` camelCase mặc định (KHÔNG dùng `EventCodec` snake_case, vì event này không băng qua
  Python).
- Xử lý: mọi `RecommendationPayload` được upsert vào bảng `recent_recommendations` (khóa
  `(user_id, item_id)`) — nguồn cho `recentRecommendations` ở `GET /api/v1/dashboard/{userId}`.

---

## 12. Kafka — `ai-service` (consumer/producer)

Chỉ chạy khi `KAFKA_ENABLED=true` (mặc định `false`, xem `app/config.py`).

### `handle_recording_uploaded`

- File: `ai-service/app/kafka/handlers/recording_uploaded.py`
- **Consume**: `recording.uploaded` — `RecordingUploadedEvent` (`recording_id`, `user_id`, `s3_bucket`,
  `s3_key`, `language_code` — `null`/bỏ trống nghĩa là tự nhận diện ngôn ngữ theo từng speaker turn)
  — nay được publish thật bởi `recording-service` (mục 3)
- **Xử lý**: tải audio từ S3 → convert WAV (PyAV) → diarize trước (`DiarizationEngine.diarize`) → cắt
  từng speaker turn (`slice_wav`) và transcribe **song song** qua `ThreadPoolExecutor`
  (`transcribe_turns_multilingual`, worker count qua `STT_MAX_CONCURRENT_TRANSCRIPTIONS`) — mỗi turn tự
  nhận diện ngôn ngữ riêng nếu `language_code` là `null`; nếu `VISION_ENABLED=true`, còn sample frame từ
  video gốc (`app/vision/frame_extractor.py`) và gọi Gemini vision
  (`app/vision/gemini_vision_engine.py`) để caption, gộp thành các `Segment` (`speaker == "vision"`) vào
  `segments` trước khi publish (`app/vision/pipeline.py`)
- **Publish**: `transcript.ready` (key = `recording_id`) — `TranscriptReadyEvent` (`recording_id`, `user_id`,
  `full_text`, `segments` — mỗi segment kèm `language`)

### `handle_analysis_requested`

- File: `ai-service/app/kafka/handlers/analysis_requested.py`
- **Consume**: `learning.gap.analysis.requested` — `AnalysisRequestedEvent` (`recording_id`, `user_id`,
  `segments`, `history`)
- **Xử lý**: `RuleBasedAnalyzer().analyze(...)`
- **Publish**: `learning.gap.analyzed` (key = `recording_id`) — `LearningGapAnalyzedEvent` (`recording_id`,
  `user_id`, `weak_points`)

### Danh sách topic (nguồn: `common/.../constants/KafkaTopics.java`, mirror ở `ai-service/app/kafka/topics.py`)

| Topic | Producer | Consumer | Trạng thái |
|---|---|---|---|
| `recording.uploaded` | recording-service | ai-service | ✅ hoạt động |
| `transcript.ready` | ai-service | english-service/vocabulary | ✅ hoạt động |
| `learning.gap.analysis.requested` | english-service (`practice`, khi learner làm lại bài tập) | ai-service | ✅ hoạt động |
| `learning.gap.analyzed` | ai-service | english-service (vocabulary/grammar/pronunciation), recommendation-service, dashboard-service | ✅ hoạt động (5 consumer, 5 `groupId` riêng) |
| `pronunciation.analyzed` | english-service/pronunciation (dự kiến) | — | ⚠️ chỉ tồn tại tên hằng số, chưa có producer/consumer |
| `grammar.analyzed` | english-service/grammar (dự kiến) | — | ⚠️ chỉ tồn tại tên hằng số, chưa có producer/consumer |
| `vocabulary.analyzed` | english-service/vocabulary (dự kiến) | — | ⚠️ chỉ tồn tại tên hằng số, chưa có producer/consumer |
| `recommendation.generated` | recommendation-service | dashboard-service | ✅ hoạt động |

---

## 13. Bảng tổng hợp

| Service | Loại | Method/Topic | Path/Tên | Ghi chú |
|---|---|---|---|---|
| bff-service | REST | POST | `/api/v1/auth/register` | proxy sang user-service |
| bff-service | REST | POST | `/api/v1/auth/login` | proxy sang user-service |
| bff-service | REST | GET | `/api/v1/users/{userId}` | proxy sang user-service |
| bff-service | REST | PATCH | `/api/v1/users/{userId}` | proxy sang user-service |
| bff-service | REST | POST | `/api/v1/recordings` | proxy multipart streaming sang recording-service |
| bff-service | REST | GET | `/api/v1/learners/{userId}/overview` | fan-out user-service + dashboard-service + recording-service |
| bff-service | REST | GET | `/api/v1/learners/{userId}/weak-points` | fan-out 3 endpoint weak-points của english-service |
| bff-service | REST | GET | `/api/v1/learners/{userId}/recommendations` | proxy `/recommendations/{userId}/grouped` |
| bff-service | REST | GET | `/api/v1/learners/{userId}/practice/next` | fan-out 3 endpoint weak-points, top N theo `forgettingScore` |
| bff-service | REST | POST | `/api/v1/learners/{userId}/practice/redo` | proxy sang english-service `/api/v1/practice/redo` |
| bff-service | REST | GET | `/api/v1/learners/{userId}/dictation/facets` | proxy facets thư viện dictation (nay có `minListensForHint`) |
| bff-service | REST | GET | `/api/v1/learners/{userId}/dictation/clips` | proxy duyệt clip theo skill/level/topic/examType |
| bff-service | REST | GET | `/api/v1/learners/{userId}/dictation/folders` | proxy `/api/v1/dictation/folders` (rev 2, folder→file) |
| bff-service | REST | GET | `/api/v1/learners/{userId}/dictation/folders/{folderId}/lessons` | proxy `/api/v1/dictation/folders/{folderId}/lessons` (rev 2) |
| bff-service | REST | GET | `/api/v1/learners/{userId}/dictation/clips/{clipId}` | proxy chi tiết 1 clip (script + sentences, rev 2; `?translationLang=` dịch lazy) |
| bff-service | REST | GET | `/api/v1/learners/{userId}/dictation/clips/{clipId}/audio` | relay stream audio clip |
| bff-service | REST | POST | `/api/v1/learners/{userId}/dictation/sessions` | proxy `/api/v1/dictation/sessions/{userId}` (batch clip theo facets) |
| bff-service | REST | POST | `/api/v1/learners/{userId}/dictation/attempts` | proxy `/api/v1/dictation/attempts` |
| bff-service | REST | GET | `/api/v1/learners/{userId}/dictation/history` | proxy `/api/v1/dictation/history/{userId}` |
| bff-service | REST | GET | `/api/v1/learners/{userId}/dictation/history/{attemptId}` | proxy `/api/v1/dictation/history/{userId}/{attemptId}` |
| bff-service | REST | POST | `/api/v1/learners/{userId}/dictation/history/{attemptId}/ai-practice` | proxy `/api/v1/dictation/history/{userId}/{attemptId}/ai-practice` (nay dùng chung generator với `generate`; `?translationLang=`) |
| bff-service | REST | GET | `/api/v1/learners/{userId}/dictation/ai-practice` | proxy danh sách AI-practice (nay có `level`/`examType`/`topic`) |
| bff-service | REST | POST | `/api/v1/learners/{userId}/dictation/ai-practice/generate` | proxy tổng hợp audio AI-practice, body `{level?, examType?, translationLang?}` |
| bff-service | REST | GET | `/api/v1/learners/{userId}/dictation/ai-practice/items/{practiceItemId}/audio` | relay stream audio AI-practice |
| bff-service | REST | GET | `/api/v1/learners/{userId}/dictation/ai-practice/items/{practiceItemId}/detail` | proxy chi tiết 1 bài luyện AI (passage + sentences, cho chép chính tả từng câu) |
| user-service | REST | POST | `/api/v1/auth/register` | hash password, phát JWT, `409` nếu email trùng |
| user-service | REST | POST | `/api/v1/auth/login` | phát JWT, `401` chung cho email sai/không tồn tại |
| user-service | REST | GET | `/api/v1/users/{userId}` | 404 nếu không tồn tại |
| user-service | REST | PATCH | `/api/v1/users/{userId}` | cập nhật `name` |
| user-service | REST | POST | `/api/v1/users/{userId}/photo` | upload ảnh đại diện → S3, nguồn ảnh cho ai-service enroll khuôn mặt |
| english-service (vocabulary) | REST | GET | `/api/v1/transcripts/{recordingId}` | 404 nếu chưa có transcript |
| english-service (vocabulary) | REST | GET | `/api/v1/vocabulary/weak-points/{userId}` | filter `?type=` tùy chọn |
| english-service (vocabulary) | REST | GET | `/api/v1/vocabulary/weak-points/{userId}/grouped` | nhóm theo `VocabularyType` |
| english-service (grammar) | REST | GET | `/api/v1/grammar/weak-points/{userId}` | filter `?type=` tùy chọn |
| english-service (grammar) | REST | GET | `/api/v1/grammar/weak-points/{userId}/grouped` | nhóm theo `GrammarType` |
| english-service (pronunciation) | REST | GET | `/api/v1/pronunciation/weak-points/{userId}` | filter `?type=` tùy chọn |
| english-service (pronunciation) | REST | GET | `/api/v1/pronunciation/weak-points/{userId}/grouped` | nhóm theo `PronunciationType` |
| english-service (practice) | REST | POST | `/api/v1/practice/redo` | chấm điểm redo-exercise, chấm điểm trực tiếp bằng Java scoring engine, refresh mistake history, trigger re-analysis |
| english-service (practice) | REST | GET | `/api/v1/practice/review-queue/{userId}` | item đến hạn ôn lại theo lịch Leitner |
| english-service (dictation) | REST | GET | `/api/v1/dictation/facets` | facets thư viện (skill/level/topic/examType) + `minListensForHint` |
| english-service (dictation) | REST | GET | `/api/v1/dictation/clips` | duyệt clip thư viện theo facets |
| english-service (dictation) | REST | GET | `/api/v1/dictation/folders` | (rev 2) danh sách folder (chủ đề) + `lessonCount` |
| english-service (dictation) | REST | GET | `/api/v1/dictation/folders/{folderId}/lessons` | (rev 2) danh sách bài trong 1 folder, không script |
| english-service (dictation) | REST | GET | `/api/v1/dictation/clips/{clipId}` | (rev 2) chi tiết 1 clip: script + sentences; lazy-align qua ai-service nếu còn thiếu startMs/endMs; `?translationLang=` dịch lazy từng câu; `404` |
| english-service (dictation) | REST | GET | `/api/v1/dictation/clips/{clipId}/audio` | stream audio clip từ StorageClient |
| english-service (dictation) | REST | POST | `/api/v1/dictation/sessions/{userId}` | batch clip thư viện ngẫu nhiên theo facets |
| english-service (dictation) | REST | POST | `/api/v1/dictation/attempts` | chấm WER + ghi misses + AI gợi ý + publish `learning.gap.analyzed`; `400`/`404` |
| english-service (dictation) | REST | GET | `/api/v1/dictation/history/{userId}` | lịch sử chấm điểm dictation, mới nhất trước |
| english-service (dictation) | REST | GET | `/api/v1/dictation/history/{userId}/{attemptId}` | chi tiết 1 lần chấm điểm: lời thoại gốc, bản gõ, danh sách từ sai, gợi ý AI đã lưu; `404` |
| english-service (dictation) | REST | POST | `/api/v1/dictation/history/{userId}/{attemptId}/ai-practice` | nay gọi cùng `LlmDictationDialogueGenerator` mà `generate` dùng (một đoạn duy nhất, không còn nhiều item một-câu) từ misses của đúng 1 attempt + Supertonic tổng hợp audio; `?translationLang=`; `404` |
| english-service (dictation) | REST | GET | `/api/v1/dictation/ai-practice/{userId}` | danh sách câu luyện AI (nay có `level`/`examType`/`topic`) |
| english-service (dictation) | REST | POST | `/api/v1/dictation/ai-practice/{userId}/generate` | body `GenerateAiPracticeRequest` `{level?, examType?, translationLang?}` (hỗ trợ `"RANDOM"`); Gemini sinh một đoạn hội thoại/độc thoại duy nhất kèm `topic` + bản dịch tùy chọn + Supertonic tổng hợp audio từng câu rồi ghép lại |
| english-service (dictation) | REST | GET | `/api/v1/dictation/ai-practice/items/{practiceItemId}/audio` | stream audio AI-practice |
| english-service (dictation) | REST | GET | `/api/v1/dictation/ai-practice/items/{practiceItemId}/detail` | chi tiết 1 bài luyện AI: passage tách thành sentences (dòng nếu hội thoại, câu nếu độc thoại) kèm bản dịch; sentences luôn `startMs`/`endMs` null; `404` |
| recording-service | REST | POST | `/api/v1/recordings` | multipart upload → S3 + publish `recording.uploaded` |
| recording-service | REST | GET | `/api/v1/recordings/{recordingId}` | 404 nếu không tồn tại |
| recording-service | REST | GET | `/api/v1/recordings/user/{userId}` | danh sách theo user |
| recommendation-service | REST | GET | `/api/v1/recommendations/{userId}` | filter `?category=` tùy chọn |
| recommendation-service | REST | GET | `/api/v1/recommendations/{userId}/grouped` | nhóm theo `category` |
| dashboard-service | REST | GET | `/api/v1/dashboard/{userId}` | tổng hợp category progress + gợi ý gần nhất |
| ai-service | REST | GET | `/health` | liveness |
| ai-service | REST | POST | `/api/v1/transcribe` | STT đồng bộ từ S3 event |
| ai-service | REST | POST | `/api/v1/upload` | STT đồng bộ từ multipart upload |
| ai-service | REST | POST | `/api/v1/analyze` | phân tích lỗi/quên đồng bộ |
| ai-service | REST | POST | `/api/v1/tts/synthesize` | tổng hợp giọng nói bằng Supertonic (ONNX/CPU, 44.1kHz), trả base64 WAV — dùng cho AI-practice của dictation |
| ai-service | REST | POST | `/api/v1/dictation/align-sentences` | Whisper word-timestamps + khớp câu tuần tự — trả startMs/endMs cho từng câu dictation |
| ai-service | REST | POST | `/api/v1/faces/enroll` | enroll khuôn mặt (ảnh từ user-service hoặc upload trực tiếp) |
| ai-service | REST | GET | `/api/v1/faces` | danh sách khuôn mặt đã enroll |
| english-service (vocabulary) | Kafka in | `transcript.ready` | `TranscriptReadyConsumer` | lưu Transcript + Segments |
| english-service (vocabulary) | Kafka in | `learning.gap.analyzed` | `LearningGapAnalyzedConsumer` (groupId `english-service`) | lưu weak points (chỉ category vocabulary) |
| english-service (grammar) | Kafka in | `learning.gap.analyzed` | `LearningGapAnalyzedConsumer` (groupId `english-service-grammar`) | lưu weak points (chỉ category grammar) |
| english-service (pronunciation) | Kafka in | `learning.gap.analyzed` | `LearningGapAnalyzedConsumer` (groupId `english-service-pronunciation`) | lưu weak points (chỉ category pronunciation) |
| english-service (practice) | Kafka in | `learning.gap.analyzed` | `MistakeHistorySeedConsumer` (groupId `english-service-practice`) | seed `mistake_history` lần đầu, không lọc category |
| english-service (practice) | Kafka out | `learning.gap.analysis.requested` | `AnalysisRequestedProducer` | key `recordingId` giả (`practice-<uuid>`), snake_case, không envelope |
| recording-service | Kafka out | `recording.uploaded` | `RecordingUploadedProducer` | key `recordingId`, snake_case, không envelope |
| recommendation-service | Kafka in | `learning.gap.analyzed` | `LearningGapAnalyzedConsumer` (groupId `recommendation-service`) | upsert `recommendations`, không lọc category |
| recommendation-service | Kafka out | `recommendation.generated` | `RecommendationGeneratedEvent` via `common.EventPublisher` | key `userId`, camelCase + envelope |
| dashboard-service | Kafka in | `learning.gap.analyzed` | `LearningGapAnalyzedConsumer` (groupId `dashboard-service`) | upsert `weak_points_snapshot` |
| dashboard-service | Kafka in | `recommendation.generated` | `RecommendationGeneratedConsumer` (groupId `dashboard-service`) | upsert `recent_recommendations` |
| ai-service | Kafka in/out | `recording.uploaded` → `transcript.ready` | `handle_recording_uploaded` | pipeline STT + diarization |
| ai-service | Kafka in/out | `learning.gap.analysis.requested` → `learning.gap.analyzed` | `handle_analysis_requested` | pipeline phân tích lỗi/quên |

---

## 14. Các service/domain chưa có API

Không còn service Java nào ở dạng skeleton — cả 7 service (`english-service` 8085 cả 3 domain,
`user-service` 8081, `recording-service` 8082, `recommendation-service` 8086, `dashboard-service` 8087,
`bff-service` 8080) đều đã có controller/route thật (xem mục 1–6, 8–11).

Producer cho `learning.gap.analysis.requested` trước đây còn thiếu nay đã có: `english-service`'s
`practice` package publish topic này mỗi khi learner làm lại bài tập (`POST /api/v1/practice/redo`, xem
mục 1, 8) — bó lịch sử sai sót (`mistake_history`) của learner thay vì transcript (transcript vẫn là
nguồn duy nhất khi phân tích một recording thật, nhưng luồng đó chưa có producer transcript+history kết
hợp — vẫn cần nếu muốn tự động re-analyze ngay sau một recording mới thay vì chỉ khi learner chủ động
redo). Còn thiếu: enforcement JWT ở bất kỳ đâu (`user-service` mới chỉ phát hành token — xem lưu ý
đầu mục 2).
