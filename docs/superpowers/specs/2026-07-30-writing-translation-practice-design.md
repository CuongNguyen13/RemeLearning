# Thiết kế: Luyện viết & Luyện dịch (kỹ năng `writing`)

- Ngày: 2026-07-30
- Trạng thái: đã được người dùng chấp thuận (chờ chuyển sang implementation plan)
- Phạm vi: `english-service`, `bff-service`, `recommendation-service`, `RemeLearning_FE`, tài liệu

## 1. Mục tiêu

Thêm kỹ năng luyện viết cho người học, gồm 3 chế độ:

1. **Viết theo đề** (`COMPOSE`) — AI đưa đề bài, người học viết bài tiếng Anh.
2. **Dịch Việt → Anh** (`TRANSLATE_VI_EN`).
3. **Dịch Anh → Việt** (`TRANSLATE_EN_VI`).

Khi nộp bài, AI chấm điểm theo tiêu chí (ngữ pháp, từ vựng, tính mạch lạc, độ sát nghĩa/bám đề) và
trả về danh sách lỗi **có nhãn**. Các nhãn lỗi ngữ pháp/từ vựng hay sai được tổng hợp vào weak-point
sẵn có, từ đó tự động xuất hiện trong mục **luyện tập** (review queue) và **recommendation**.

Trong lúc viết, người học có nút **"Gợi ý câu tiếp theo"** trả về 2–3 hướng triển khai.

## 2. Các quyết định thiết kế đã chốt

| # | Quyết định | Lý do |
|---|---|---|
| D1 | **Một** domain `writing`, phân biệt nội bộ bằng `taskType` (`COMPOSE` / `TRANSLATE_VI_EN` / `TRANSLATE_EN_VI`) | Tránh nhân đôi mapper/controller/service/test; mọi lỗi ngữ pháp/từ vựng đổ về cùng một chỗ |
| D2 | Đề bài: **AI sinh** (nhắm vào weak point) **và** có **thư viện nội dung** (`library`) duyệt theo topic | Bám pattern `listening`/`grammar` đã có đầy đủ cả hai |
| D3 | Chấm điểm: LLM trả **điểm theo tiêu chí + danh sách lỗi có nhãn** (JSON) | Nhãn + category chính là dữ liệu nạp vào weak-point/recommendation; feedback văn xuôi thuần không làm được việc này |
| D4 | Gợi ý câu tiếp: **bấm nút**, request/response thường, **không** ghost-text, **không** streaming | Kiểm soát chi phí token; repo hiện chưa có hạ tầng SSE; ghost-text làm giảm hiệu quả luyện tập |
| D5 | Lỗi bắt được **route theo `category` thật của lỗi** (`grammar` → `grammar_weak_points`, `vocabulary` → `vocabulary_weak_points`) — **không** tạo bảng `writing_weak_points` | Đúng yêu cầu "tổng hợp ngữ pháp/từ vựng hay sai": lỗi từ bài viết hợp nhất với lỗi từ dictation/listening trên cùng nhãn. Dùng `WeakPointDispatcher` sẵn có, không cần Kafka consumer/bảng mới |
| D6 | Toàn bộ logic LLM nằm trong `english-service` (Java, qua `common`'s `LlmClient`); **không** đụng `ai-service` | Nhất quán với 6 domain hiện có (`LlmListeningPracticeGenerator`, `LlmOpenAnswerGrader`); không thêm cross-service call đồng bộ |
| D7 | `library` có **3 trục taxonomy**: `grammar` (60 topic ngữ pháp), `genre` (thể loại văn bản), `vocab_theme` (chủ đề từ vựng) | Người dùng yêu cầu cả 3; gating tiến độ độc lập theo từng trục |
| D8 | Có đủ: lịch sử bài làm + xem lại chi tiết, retry (sinh đề mới từ lỗi bài cũ), frontend hoàn chỉnh, mẫu exercise cho recommendation | Người dùng chọn cả 4 |

### Phương án đã cân nhắc và loại bỏ

- **Đẩy chấm bài sang `ai-service` (Python)**: lệch khỏi pattern hiện tại (không domain nào gọi
  `ai-service` đồng bộ), thêm một điểm chết, `ai-service` chưa có `LlmClient`.
- **Chấm bài async qua Kafka (kiểu `dictation`)**: người học phải chờ không xác định, không trả kết
  quả ngay — không phù hợp trải nghiệm nộp bài.
- **Tạo `writing_weak_points` riêng**: lỗi "past perfect" trong bài viết sẽ tách khỏi "past perfect"
  đã tích lũy ở `grammar`, phá vỡ chính mục tiêu tổng hợp.

## 3. Kiến trúc

### 3.1 Package mới (`english-service`, port 8085)

Clone cấu trúc `com.remelearning.english.listening` — domain đầy đủ nhất hiện có.

```
com/remelearning/english/writing/
├── controller/   WritingLearnController
├── domain/       WritingTaskType (enum), WritingPracticeItem, WritingAttempt,
│                 WritingErrorItem, WritingCriteriaScores, WritingSuggestion,
│                 WritingAttemptHistoryRow, WritingAttemptDetailRow
├── dto/          GenerateWritingPracticeRequest, WritingPracticeItemDto,
│                 SubmitWritingAttemptRequest, WritingAttemptResultDto,
│                 WritingErrorDto, SuggestNextSentenceRequest, WritingSuggestionDto,
│                 WritingAttemptHistoryEntryDto, WritingAttemptDetailDto
├── generator/    WritingPracticeGenerator (interface) + LlmWritingPracticeGenerator
│                 GeneratedWritingPractice (record)
│                 WritingMistakeAnalyzer (static, thuần — cho retry)
├── grading/      WritingGrader (interface) + LlmWritingGrader
│                 WritingGrade (record)
├── suggestion/   NextSentenceSuggester (interface) + LlmNextSentenceSuggester
├── history/      dto/ + service/ (WritingHistoryService)
├── library/      controller/ service/ domain/ dto/ mapper/ generator/
├── mapper/       WritingMapper (+ resources/mapper/writing/WritingMapper.xml)
└── service/      WritingLearnService (interface) + WritingLearnServiceImpl
```

Không có sub-package `weakpoint/` (xem D5). `EnglishServiceApplication`'s `@MapperScan` phải được bổ
sung package mapper của `writing` và `writing.library`.

`LearningCategories` (module `common`) thêm hằng `WRITING = "writing"` — dùng để đánh dấu nguồn gốc
và cho `ExerciseTemplates`, **không** dùng làm category của weak-point.

### 3.2 Migration V26 — `writing_practice.sql`

`writing_practice_items`

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `user_id` | VARCHAR(100) NOT NULL | |
| `task_type` | VARCHAR(24) NOT NULL | `COMPOSE` / `TRANSLATE_VI_EN` / `TRANSLATE_EN_VI`; enum validate ở Java, không ở schema (theo tiền lệ `listening_topic_progress.status`) |
| `level` | VARCHAR(16) | CEFR, nullable |
| `exam_type` | VARCHAR(32) | nullable |
| `topic` | VARCHAR(255) | |
| `prompt_text` | TEXT NOT NULL | Đề bài (kèm chỉ dẫn tiếng Việt) hoặc văn bản nguồn cần dịch |
| `source_lang` | VARCHAR(8) NOT NULL | |
| `target_lang` | VARCHAR(8) NOT NULL | |
| `reference_answer` | TEXT | Bài mẫu / bản dịch tham chiếu. **Không** trả về FE trước khi nộp bài |
| `target_labels_json` | TEXT | Các nhãn weak-point mà đề này nhắm vào |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT now() | |

Index: `idx_writing_practice_items_user` trên `(user_id)`.

`writing_attempts`

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `practice_item_id` | BIGINT NOT NULL REFERENCES `writing_practice_items(id)` | |
| `user_id` | VARCHAR(100) NOT NULL | |
| `submitted_text` | TEXT NOT NULL | |
| `corrected_text` | TEXT | Bản sửa của AI |
| `overall_score` | DOUBLE PRECISION NOT NULL | 0..1 |
| `criteria_json` | TEXT NOT NULL | Điểm từng tiêu chí |
| `errors_json` | TEXT NOT NULL | Danh sách lỗi có nhãn — dữ liệu then chốt |
| `feedback` | TEXT | Nhận xét tiếng Việt |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT now() | |

Index: `idx_writing_attempts_user` trên `(user_id)`.

Hình dạng một phần tử `errors_json`:

```json
{
  "wrong": "I have went to Hanoi",
  "corrected": "I went to Hanoi",
  "label": "past simple vs present perfect",
  "category": "grammar",
  "explanationVi": "Có mốc thời gian xác định trong quá khứ nên dùng past simple.",
  "severity": "major"
}
```

`category` chỉ nhận `grammar` hoặc `vocabulary`. Giá trị khác (LLM trả sai) bị **bỏ qua khi đổ về
weak-point** và ghi `log.warn`, nhưng vẫn được lưu trong `errors_json` và hiển thị cho người học.

### 3.3 Migration V27 — `writing_library.sql`

| Bảng | Cột chính |
|---|---|
| `writing_library_topics` | `id`, `taxonomy` VARCHAR(20) NOT NULL (`grammar`\|`genre`\|`vocab_theme`), `code`, `name`, `description`, `level`, `sequence_order`; `UNIQUE(taxonomy, code)`, `UNIQUE(taxonomy, sequence_order)` |
| `writing_library_prompts` | `id`, `topic_id` FK, `task_type`, `prompt_text`, `reference_answer`, `min_words`, `explanation`, `created_at` |
| `writing_topic_progress` | `id`, `user_id`, `topic_id` FK, `status` VARCHAR(20), `unlocked_at`, `passed_at`, `updated_at`; `UNIQUE(user_id, topic_id)` — hình dạng y hệt `listening_topic_progress` để mapper `ON CONFLICT` hoạt động |
| `writing_library_attempts` | `id`, `user_id`, `prompt_id` FK, `score`, `started_at`, `completed_at` |

Seed:
- trục `grammar`: 60 topic copy nguyên bộ từ V19 (`listening_library_topics`) — cùng `code`/`name`/`level`/`sequence_order`;
- trục `genre`: đúng 12 thể loại, chốt danh sách (`code` / `name` / `level`):
  `personal_message` Tin nhắn / email cá nhân (beginner),
  `formal_email` Email công việc (beginner),
  `descriptive_paragraph` Đoạn văn miêu tả (beginner),
  `narrative_paragraph` Đoạn văn kể chuyện (beginner),
  `opinion_essay` Bài luận nêu quan điểm (intermediate),
  `pros_cons_essay` Bài luận lợi ích – hạn chế (intermediate),
  `ielts_task1_chart` IELTS Writing Task 1 – mô tả biểu đồ (intermediate),
  `ielts_task2_essay` IELTS Writing Task 2 (intermediate),
  `complaint_letter` Thư khiếu nại (intermediate),
  `cover_letter` Thư xin việc (intermediate),
  `report` Báo cáo ngắn (advanced),
  `argumentative_essay` Bài luận tranh luận (advanced);
- trục `vocab_theme`: copy taxonomy chủ đề của `vocabulary_library`.

Gating tiến độ tính **độc lập theo từng trục**: `sequence_order` chỉ so sánh trong cùng `taxonomy`.

Index: `idx_writing_library_prompts_topic`, `idx_writing_topic_progress_user`,
`idx_writing_library_attempts_user`.

## 4. API

### 4.1 `english-service` — `/api/v1/learn/writing` (nội bộ)

| Method | Path | Mục đích |
|---|---|---|
| POST | `/generate` | `{userId, taskType, level, examType, focusItems[]}` → `WritingPracticeItemDto`. `focusItems` rỗng ⇒ lấy top weak point `grammar` + `vocabulary` của người học qua `getTopWeakPoints` |
| GET | `/items/{itemId}` | Một đề (không kèm `referenceAnswer`) |
| GET | `/items?userId=` | Danh sách đề của người học |
| POST | `/suggest` | `{practiceItemId, draftText}` → `{suggestions[]}` (2–3 phần tử) |
| POST | `/attempts` | `{userId, practiceItemId, submittedText}` → `WritingAttemptResultDto` (chấm ngay, đồng bộ) |
| GET | `/history/{userId}` | Lịch sử bài làm |
| GET | `/attempts/{attemptId}?userId=` | Chi tiết bài đã chấm |
| POST | `/attempts/{attemptId}/practice` | Sinh đề mới nhắm vào lỗi bài cũ (retry) → danh sách đề đã cập nhật |

### 4.2 `english-service` — `/api/v1/learn/writing/library`

| Method | Path | Mục đích |
|---|---|---|
| GET | `/topics?userId=&taxonomy=` | Topic của một trục + trạng thái gating của người học |
| GET | `/topics/{topicId}/prompts` | Các đề trong topic |
| POST | `/prompts/{promptId}/submit` | Nộp bài theo đề của thư viện; chấm và cập nhật tiến độ topic |

Khớp hợp đồng `ListeningLibraryController`.

### 4.3 `bff-service`

Thêm vào `LearnerController` dưới `/api/v1/learners/{userId}/learn/writing/...`, proxy 1-1. Client
downstream đi theo đúng cách `listening` đang làm (mở rộng `EnglishServiceClient`, không tạo
`WebClient` bean mới vì `english` đã có entry ở `reme.services.*`).

### 4.4 `recommendation-service`

`ExerciseTemplates.TEMPLATES` thêm entry `"writing"`, và bổ sung luôn entry `"listening"` hiện đang
thiếu (đang rơi vào `DEFAULT_TEMPLATE` chung chung `"Ôn lại nội dung: \"%s\"."`). Sửa nhỏ, cùng file.

## 5. Luồng chính

### 5.1 Chấm bài — `POST /api/v1/learn/writing/attempts`

```
WritingLearnServiceImpl.submit(request)              [@Transactional]
 ├─ requireItem(practiceItemId)                       — nạp đề + referenceAnswer
 ├─ WritingGrader.grade(taskType, promptText, referenceAnswer, submittedText)
 │    → LLM trả JSON: { overallScore, criteria{...}, correctedText, feedbackVi, errors[] }
 │    → never throws: LLM/parse lỗi ⇒ điểm trung tính + feedback giải thích (hợp đồng
 │      giống OpenAnswerGrader)
 ├─ writingMapper.insertAttempt(...)                  — lưu criteria_json + errors_json
 ├─ feedWeakPoints(userId, errors)                    — xem 5.2
 └─ trả WritingAttemptResultDto (lúc này mới lộ referenceAnswer)
```

Tiêu chí chấm theo `taskType`:

| `taskType` | Tiêu chí |
|---|---|
| `COMPOSE` | `grammar`, `vocabulary`, `coherence`, `taskResponse` (bám đề) |
| `TRANSLATE_VI_EN` | `grammar`, `vocabulary`, `coherence`, `accuracy` (độ sát nghĩa) |
| `TRANSLATE_EN_VI` | `grammar`, `vocabulary`, `coherence`, `accuracy` |

`overallScore` **do Java tự tính** là trung bình 4 tiêu chí, không dùng giá trị `overallScore` LLM
trả về (LLM thường tự cho điểm lệch khỏi chính các tiêu chí nó vừa chấm). Từng điểm tiêu chí được kẹp
về khoảng [0, 1] trước khi lấy trung bình. Tiêu chí thiếu trong JSON ⇒ coi là 0 và ghi `log.warn`.

### 5.2 `feedWeakPoints` — nối vào luyện tập & recommendation

Theo khuôn `ListeningLearnServiceImpl.feedWeakPoints`, khác ở chỗ **`category` lấy từ chính lỗi**,
không hardcode:

```java
// Quy ước prefix itemId trong repo KHÔNG đồng nhất với tên category:
//   grammar    -> "grammar:"
//   vocabulary -> "vocab:"     (GHI CHÚ: không phải "vocabulary:")
// Dùng đúng prefix này để lỗi từ bài viết hợp nhất với weak-point sẵn có,
// thay vì tạo dòng mới song song.
private static final Map<String, String> ITEM_ID_PREFIXES = Map.of(
        LearningCategories.GRAMMAR, "grammar:",
        LearningCategories.VOCABULARY, "vocab:");

attempt.setItemId(ITEM_ID_PREFIXES.get(error.getCategory()) + error.getLabel().toLowerCase());
attempt.setCategory(error.getCategory());
attempt.setLabel(error.getLabel());
attempt.setCorrect(false);
```

- Dedupe theo `(category, label.toLowerCase())` trong cùng một bài — lặp cùng một lỗi 3 lần không
  tạo 3 weak point.
- Lỗi có `category` không nằm trong map ⇒ bỏ qua + `log.warn`.
- Danh sách rỗng ⇒ **không** gọi `redo` (tránh publish event vô nghĩa).
- Gọi **một lần** `practiceService.redo(request)` cho cả bài.

Hiệu ứng lan truyền, hoàn toàn dùng đường ống sẵn có:

| Đích | Cơ chế |
|---|---|
| `grammar_weak_points` / `vocabulary_weak_points` | `WeakPointDispatcher` route theo `category` → `applyJavaComputedScore` |
| Mục **luyện tập** | `mistake_history` được cập nhật ⇒ item xuất hiện trong `GET /api/v1/practice/review-queue/{userId}` theo lịch Leitner |
| Mục **recommendation** | `redo` publish `learning.gap.analysis.requested` → `ai-service` → `learning.gap.analyzed` → `recommendation-service` upsert dòng recommendation + publish `recommendation.generated` → `dashboard-service` |

Không có Kafka topic mới, không có consumer/producer mới, không có bảng weak-point mới.

### 5.3 Sinh đề — `LlmWritingPracticeGenerator`

Interface `WritingPracticeGenerator`, hợp đồng **never null, never throws** — fallback template tĩnh
khi LLM lỗi (giống `ListeningPracticeGenerator`).

Đầu vào prompt: `taskType`, `targetLabels`, `level`, `examType`. Đầu ra theo `taskType`:

| `taskType` | LLM sinh |
|---|---|
| `COMPOSE` | `topic` + `promptText` = đề bài tiếng Việt + yêu cầu (số từ tối thiểu, cấu trúc bắt buộc dùng) + `referenceAnswer` = bài mẫu tiếng Anh |
| `TRANSLATE_VI_EN` | `promptText` = đoạn tiếng Việt chứa đúng cấu trúc/từ vựng đang yếu; `referenceAnswer` = bản dịch tiếng Anh tham chiếu |
| `TRANSLATE_EN_VI` | `promptText` = đoạn tiếng Anh; `referenceAnswer` = bản dịch tiếng Việt tham chiếu |

`promptText` **luôn** kèm phần chỉ dẫn/yêu cầu viết bằng tiếng Việt, theo quy tắc chung của dự án
(mọi câu luyện tập phải hiển thị yêu cầu tiếng Việt cho người học).

### 5.4 Gợi ý câu tiếp — `LlmNextSentenceSuggester`

```java
public interface NextSentenceSuggester {
    /** Never throws — trả list rỗng khi LLM/parse lỗi; FE hiển thị "chưa có gợi ý". */
    List<WritingSuggestion> suggest(WritingTaskType taskType, String promptText,
                                    String draftText, String level);
}
```

`WritingSuggestion`: `{ideaVi, structureHint, usefulPhrases[]}` — ý tưởng bằng tiếng Việt + gợi ý mẫu
câu/cụm từ tiếng Anh. **Không viết sẵn cả câu hoàn chỉnh**, để người học vẫn phải tự viết.

Hai giới hạn bắt buộc:

1. **Với `TRANSLATE_VI_EN` / `TRANSLATE_EN_VI`**: chỉ được gợi ý *dạng cấu trúc* cần dùng và nghĩa
   của 1–2 từ khó; tuyệt đối không dịch nguyên câu tiếp theo. `referenceAnswer` **không** được đưa
   vào prompt gợi ý (nếu đưa vào thì gợi ý = đáp án).
2. Việc dùng gợi ý **không** được ghi vào weak-point — dùng gợi ý không phải là "sai".

Không debounce, không streaming, không job nền: không bấm nút thì không phát sinh chi phí.

### 5.5 Retry — `POST /attempts/{attemptId}/practice`

`WritingMistakeAnalyzer` là class tĩnh thuần (không Spring bean), giống `ListeningMistakeAnalyzer`:
đọc `errors_json` của bài cũ → rút danh sách `label` bị sai → gọi lại đúng
`generatePracticeForLabels(...)` mà `/generate` dùng, giữ nguyên `taskType`/`level`/`examType` của
bài cũ. Đề mới vào cùng bank `writing_practice_items`.

Trước khi đọc, xác thực attempt thuộc về đúng `userId` (như
`findAttemptDetailByIdAndUserId`), không thì ném `BusinessException.notFound`.

Vì analyzer là hàm thuần, nó test được mà không cần LLM hay DB.

## 6. Frontend (`RemeLearning_FE`)

```
src/features/learn/writing/
├── WritingLearnPage.tsx          — tab "Học thường" (AI sinh đề) | "Thư viện" | "Lịch sử"
├── WritingEditor.tsx             — textarea + đếm từ + nút "Gợi ý câu tiếp theo"
├── SuggestionPanel.tsx           — 2–3 gợi ý (ý tưởng VI + mẫu câu/cụm từ EN)
├── WritingResultPanel.tsx        — điểm từng tiêu chí, bản sửa của AI, danh sách lỗi có nhãn
├── WritingAttemptDetailDialog.tsx
├── hooks.ts                      — React Query hooks
└── library/
    ├── TaxonomyTabs.tsx          — 3 trục: Ngữ pháp | Thể loại | Chủ đề từ vựng
    ├── TopicLibraryPanel.tsx
    └── hooks.ts
```

- Route mới trong `AppRouter.tsx`, lazy-load như các trang khác: `/learn/writing`,
  `/learn/writing/library/topics/:topicId`.
- Chọn `taskType` bằng segmented control ngay trên trang — quyết định placeholder editor và cách
  hiển thị đề (đề bài vs văn bản nguồn cần dịch).
- `WritingResultPanel` hiển thị lỗi dạng danh sách có nhãn + badge `category`, kèm nút **"Luyện lại
  những lỗi này"** gọi `/attempts/{id}/practice`.
- Dùng lại `features/learn/shared/`: `GenerateDialog`, `useFocusItem`, `AttemptResultShell` — không
  viết lại.
- i18n: thêm khóa vào `src/i18n/locales`.
- Tuân thủ skill `frontend-standards`: Tailwind + shadcn/ui, React Query cho server state (không
  dùng Zustand ở feature này), `react-hook-form` + `zod` cho form.

## 7. Tests

Theo đúng pattern repo: JUnit 5 + AssertJ + `Mockito.mock(...)`, không Spring context, không
`@SpringBootTest`.

| Test | Nội dung |
|---|---|
| `WritingLearnServiceImplTest` | chấm bài lưu đúng `errors_json`; `feedWeakPoints` gọi `PracticeService.redo` với `category` lấy từ lỗi (không hardcode); `itemId` dùng đúng prefix `grammar:`/`vocab:`; dedupe nhãn lặp; bài không lỗi ⇒ **không** gọi `redo`; lỗi có category lạ bị bỏ qua |
| `LlmWritingGraderTest` | parse JSON hợp lệ; LLM throw ⇒ điểm trung tính, không ném ngoại lệ; JSON rác ⇒ fallback; strip code-fence; điểm ngoài [0,1] bị kẹp |
| `LlmWritingPracticeGeneratorTest` | fallback template khi LLM lỗi; `targetLabels` có mặt trong prompt; `promptText` fallback vẫn có chỉ dẫn tiếng Việt |
| `LlmNextSentenceSuggesterTest` | trả list rỗng khi lỗi; **`referenceAnswer` không xuất hiện trong prompt khi `taskType` là `TRANSLATE_*`** |
| `WritingMistakeAnalyzerTest` | thuần, không mock — rút đúng nhãn lỗi từ `errors_json`; JSON rỗng/không hợp lệ ⇒ list rỗng |
| `WritingLibraryServiceImplTest` | gating tiến độ độc lập theo từng trục taxonomy; nộp bài đạt ⇒ mở topic kế tiếp *trong cùng trục* |
| FE `hooks.test.ts` | Vitest + RTL cho các hook chính |

## 8. Tài liệu phải cập nhật trong cùng change

Theo `CLAUDE.md` — không để lại làm follow-up:

1. `RemeLearning/services/english-service/openapi.yaml` và `bff-service/openapi.yaml`
2. `docs/API.md` — mục lục, bảng tổng hợp, phần chi tiết
3. `docs/sequence/EnglishService/*.md` — sequence diagram cho generate / submit / suggest / retry +
   cập nhật `overview.md`
4. `docs/flow/english-service-data-flow.md` — flowchart + bảng data shape
5. `english-service/README.md`, `bff-service/README.md`, `recommendation-service/README.md`
6. `D:\Personal Project\RemeLearning_BA\Business.md` — ý nghĩa nghiệp vụ (repo khác)

## 9. Phân đoạn triển khai

Spec này bao trùm nhiều lớp, nên chia thành 4 mốc; mỗi mốc build + test xanh được độc lập.

| Mốc | Nội dung | Tiêu chí hoàn thành |
|---|---|---|
| M1 | V26 + `domain`/`dto`/`mapper` + `WritingLearnService.generate`/`getItem`/`listItems` + `generator` + fallback template | `./mvnw -pl services/english-service -am test` xanh; sinh được đề cho cả 3 `taskType` |
| M2 | `grading` + `POST /attempts` + `feedWeakPoints` + `history` + retry | Nộp bài trả điểm & lỗi; weak-point `grammar`/`vocabulary` được cập nhật đúng prefix; review-queue có item mới |
| M3 | `suggestion` + V27 + `library` (3 trục) + `ExerciseTemplates` | Nút gợi ý trả 2–3 kết quả; duyệt/gating thư viện theo từng trục hoạt động |
| M4 | `bff-service` routes + frontend + toàn bộ tài liệu mục 8 | Chạy được end-to-end từ UI |

Tài liệu ở mục 8 cập nhật **kèm theo mốc tạo ra thay đổi tương ứng** (không dồn hết vào M4) — chỉ
phần frontend README và `Business.md` tổng kết là ở M4.

## 10. Rủi ro & lưu ý

- **Prefix `itemId` không đồng nhất** với tên category (`vocabulary` → `"vocab:"`). Nếu dùng máy móc
  `category + ":"` thì lỗi từ vựng sẽ tạo dòng weak-point song song thay vì hợp nhất — chính là thứ
  thiết kế này muốn tránh. Đã xử lý bằng map tra cứu ở 5.2.
- **`WeakPointDispatcher` không cần case `"writing"`**, vì lỗi luôn mang category `grammar` hoặc
  `vocabulary`. Chỉ khi nào muốn theo dõi riêng tiêu chí đặc thù của viết (`coherence`, `accuracy`)
  thì mới cần bảng/case riêng — ngoài phạm vi lần này.
- **Không có Kafka topic hay consumer mới** ⇒ rủi ro tích hợp thấp.
- Seed 3 trục taxonomy khiến V27 dài (~200 dòng seed) — chấp nhận, giống V19.
- `reference_answer` không được rò rỉ ra FE trước khi nộp bài: `WritingPracticeItemDto` **không** có
  field này; chỉ `WritingAttemptResultDto` và `WritingAttemptDetailDto` mang nó.
- Chi phí LLM: mỗi lần nộp bài là 1 call chấm; mỗi lần bấm gợi ý là 1 call. Sinh đề 1 call. Không có
  call nền tự động.

## 11. Sai khác giữa thiết kế và bản đã triển khai

Ghi lại để spec không mâu thuẫn với code. Mọi sai khác đều theo hướng bám sát pattern có sẵn trong
repo, không đổi quyết định thiết kế nào ở mục 2.

| # | Thiết kế ghi | Thực tế | Lý do |
|---|---|---|---|
| 1 | Route `POST /generate`, `GET /items?userId=`, `GET /history/{userId}` | `POST /{userId}/generate`, `GET /{userId}/items`, `GET /history/{userId}`, `POST /history/{userId}/{attemptId}/ai-practice` | Khớp đúng convention `ListeningLearnController` đang dùng, thay vì đặt convention thứ hai |
| 2 | Có sub-package `history/` | Không tạo | `WritingMapper` đã đủ; `listening.history` tồn tại chỉ vì nó gộp lịch sử learn + library, writing chưa cần |
| 3 | Domain `WritingCriterion` | `WritingCriteriaScores` | Là một object 4–5 điểm, không phải một tiêu chí đơn lẻ |
| 4 | Cột `criteria_json` / `errors_json` / `target_labels_json` | Cột `criteria` / `errors` / `target_labels` (alias sang field `*Json` trong mapper XML) | Khớp cách `listening_practice_items.questions` alias thành `questions_json` |
| 5 | (không có) | Thêm `grading/WritingErrorPipeline` | Phát sinh khi làm M3: tab thư viện cần đúng logic đổ weak-point của tab học thường. Trích ra component dùng chung thay vì nhân đôi — đây là phần dễ sai nhất (prefix `itemId`), không được để hai bản |
| 6 | `library` có `domain/dto/mapper/...` với `WritingTaxonomy` là enum trong domain class | `WritingLibraryTopic.taxonomy` là `String`, convert qua `WritingTaxonomy.fromCode(...)` | Cột lưu chữ thường (`"vocab_theme"`) còn `EnumTypeHandler` mặc định của MyBatis map theo `name()` (chữ hoa) ⇒ sẽ vỡ khi đọc |
| 7 | Test FE `hooks.test.ts` bằng Vitest + RTL | **Không viết** | `RemeLearning_FE/package.json` chỉ có `dev`/`build`/`lint`/`preview`, không có `vitest`/`@testing-library` — repo chưa wire test runner nào. Đã kiểm tra `tsc -b`, `npm run build`, `npm run lint` thay thế |
| 8 | `.claude/skills/code-standards/SKILL.md` (theo `CLAUDE.md`) | File không tồn tại (`.claude/skills/` rỗng) | Đã áp checklist ghi trực tiếp trong `CLAUDE.md`; skill `frontend-standards` (ở repo FE) thì có thật và đã chạy |
| 9 | Số đề mỗi topic thư viện: không nêu | 3–6, suy ra từ `topicId` | Mượn đúng cách `ListeningLibraryServiceImpl.targetSectionCount` làm (không cần thêm cột) |
| 10 | `ExerciseTemplates` thêm entry `writing` | Thêm `writing` **và** `listening` | `listening` vẫn đang rơi vào template mặc định chung chung; sửa cùng lúc vì cùng một file, cùng một vấn đề |

Kết quả kiểm chứng: `english-service` 365 test, `recommendation-service` 14 test,
`bff-service` 30 test — tất cả xanh. FE: `tsc -b` sạch, `npm run build` thành công, `npm run lint` chỉ
còn 3 warning có sẵn ở file shadcn `components/ui/*`.

## 12. Bổ sung sau khi triển khai: dạng đề quyết định hình dạng bài tập

Yêu cầu bổ sung của người dùng, làm sau khi bản đầu đã xong:

> - việt-anh thêm đoạn văn tiếng việt viết người dùng dịch sang tiếng anh. nếu chọn mục tiếng anh - việt
>   thì đưa ra đoạn văn tiếng anh để dịch sang tiếng việt. (số lượng câu sẽ được random theo chủ đề của
>   toeic hoặc ielts)
> - phần viết theo đề sẽ cho chọn toeic hoặc ielts. mục luyện tập với AI sẽ cho chọn theo dạng đề

Đã làm rõ với người dùng: "mục luyện tập với AI" là **cả ba** chỗ (nút "Tạo bài luyện" ở trang Luyện
viết, nút "Luyện lại những lỗi này", và trang Luyện tập `/practice`), và dạng đề gồm **các dạng phổ
biến** chứ không chỉ TOEIC/IELTS.

### Đã làm

| # | Thay đổi | Ghi chú |
|---|---|---|
| 1 | `common/ExamTypes` | Bộ dạng đề phổ biến (TOEIC, IELTS, TOEFL, VSTEP, General) + `normalize()`. Trước đó `examType` là chuỗi tự do, nên `"toeic"`/`"TOEIC"` lưu thành hai giá trị khác nhau và lookup profile trượt một trong hai |
| 2 | `writing/generator/WritingExamProfile` | Mỗi dạng đề → khoảng số câu, bộ chủ đề, văn phong, và (cho `COMPOSE`) các thể loại văn bản. TOEIC 2–4 câu/chủ đề công việc; IELTS 4–6 câu/chủ đề học thuật; TOEFL/VSTEP/General 3–5 |
| 3 | Số câu **random mỗi lần sinh** trong khoảng của dạng đề | Trước đó prompt ghi cứng "3-5 sentences" nên mọi đoạn văn đều cùng độ dài |
| 4 | Số câu/chủ đề/văn phong do **Java quyết định** rồi truyền xuống prompt dưới dạng chỉ thị | Chỉ đưa nhãn "TOEIC" thì mô hình trả về đoạn văn gần như giống nhau cho mọi dạng đề — đây là lý do tồn tại của `WritingExamProfile` |
| 5 | `LlmWritingLibraryContentGenerator` nhận `examType` | Trước đó bỏ qua hoàn toàn, nên bài thư viện không phản ánh dạng đề |
| 6 | Retry (`/ai-practice`, cả learn và library) nhận query param `examType` | Ghi đè dạng đề của bài cũ; bỏ trống thì giữ nguyên |
| 7 | `practice.session`: thêm kỹ năng `writing` | Không có bảng weak-point riêng nên xếp hạng theo `max(grammar, vocabulary)` và nhận nhãn của **cả hai**; `taskType` random 1 trong 3 mode; cold start trải đều **5** kỹ năng |
| 8 | `practice.session`: `examType` trong request body | Chuẩn hóa một lần, truyền xuống **mọi** domain generator để cả buổi nhất quán |
| 9 | FE: `GenerateDialog` thêm prop `examTypeOptions` | Có prop ⇒ render `Select` các dạng đề; không có ⇒ giữ ô text tự do như cũ, nên 4 skill kia không bị đổi hành vi |
| 10 | FE: trang Luyện viết dùng `GenerateDialog` | Trước đó bấm "Tạo bài luyện" là sinh luôn, không cho chọn trình độ/dạng đề. Dạng đề vừa chọn được nhớ lại và dùng cho tab Thư viện + retry |
| 11 | FE: trang `/practice` thêm picker dạng đề trước nút bắt đầu | |

### Sai khác so với mục 11 (ghi chú triển khai bản đầu)

- Mục 11 ghi retry "giữ nguyên `taskType`/`level`/`examType` của bài cũ" — nay `examType` **có thể**
  ghi đè qua query param, `taskType`/`level` vẫn giữ nguyên.
- `WritingLibraryContentGenerator.generatePrompt` và `WritingLibraryService.startOrResumePrompt` đổi
  signature (thêm `examType`); `PracticeSessionService.startSession` cũng vậy.

### Kiểm chứng

`english-service` 380 test, `bff-service` 30 test, `recommendation-service` 14 test — tất cả xanh
(27 test mới/cập nhật, trong đó `WritingExamProfileTest` giữ cho khoảng số câu TOEIC luôn ngắn hơn
IELTS và cho số câu thật sự thay đổi giữa các lần sinh). FE: `tsc -b` sạch, `npm run build` thành
công, `npm run lint` chỉ còn 3 warning có sẵn ở `components/ui/*`.
