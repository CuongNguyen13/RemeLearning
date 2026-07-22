# Thư viện từ vựng theo chủ đề + Luyện tập theo Section (spaced-repetition trong phiên)

Date: 2026-07-22
Status: Approved for planning

## Bối cảnh

`english-service` đã có package `vocabulary.learn` ("Học & Luyện tập với AI"):
mỗi lần learner bấm "generate", LLM sinh **tạm thời** một bộ 6-8 câu hỏi
(CLOZE/MCQ/MATCHING) nhắm vào weak-point hiện tại hoặc từ chỉ định thủ công —
không có khái niệm "thư viện từ vựng" cố định theo chủ đề, và không có cơ chế
lặp lại nhiều lần trong một phiên để ghi nhớ. Mastery dài hạn (xuyên ngày) đã
có sẵn qua `common.scoring.WeakPointScoringEngine` (Leitner box + BKT mastery +
half-life forgetting), lưu trong `vocabulary_weak_points` (khoá theo
`(user_id, item_id)`, `item_id = "vocab:" + word`).

Tính năng này **mở rộng** `vocabulary.learn` (không tách module riêng): thêm
một thư viện từ vựng cố định theo chủ đề, và một chế độ luyện tập mới —
"Section" — trong đó nhiều từ cùng chủ đề được đưa vào một hàng đợi
(queue) và lặp lại theo kiểu Leitner-lite cho tới khi mọi từ trong section đạt
ngưỡng "thạo" trong phiên đó. Kết quả từng lần trả lời vẫn được đẩy vào đúng
pipeline weak-point/spaced-repetition xuyên ngày đã có (không xây pipeline
thứ hai).

## 1. Data model

Bảng mới, package `com.remelearning.english.vocabulary.library` (Flyway
migration mới trong `english-service`):

- **`vocabulary_topics`**: `id, code (unique slug), name, description,
  level (CEFR, nullable), created_at`. Một danh sách chủ đề cố định (Travel,
  Business, Daily Life, Food, Technology, Health, Education, Environment)
  được seed **chỉ tạo hàng topic** trong chính migration — không gọi LLM
  trong migration (migration phải deterministic).
- **`vocabulary_library_words`**: `id, topic_id (FK), word, word_type
  (tái dùng enum `VocabularyType` có sẵn), meaning_vi, example_en, ipa
  (nullable), audio_storage_key (nullable), created_at`.

**Không có bảng mastery riêng** — tái dùng thẳng `vocabulary_weak_points`
bằng cách dùng đúng quy ước `item_id = "vocab:" + word.toLowerCase()` mà
`vocabulary.learn` hiện tại đã dùng. Một từ học qua "Học & Luyện tập" (AI sinh
theo weak-point) và học qua "Thư viện theo chủ đề" chia sẻ chung một mastery
record, không trùng lặp. % mastery của một topic (cho màn hình danh sách chủ
đề) tính tại read-time bằng JOIN `vocabulary_library_words` với
`vocabulary_weak_points` theo `item_id` — cùng phong cách "tính tại thời điểm
đọc" mà `dashboard-service` đang dùng cho progress theo category.

**Bảng session state** (stateful, cho phép resume):
- **`vocabulary_section_attempts`**: `id, user_id, topic_id, status
  (IN_PROGRESS/COMPLETED/ABANDONED), section_size, library_word_ids_json,
  queue_state_json, correct_count, total_answers, started_at, completed_at`.
- **`vocabulary_section_answers`**: `id, section_attempt_id, library_word_id,
  exercise_type, submitted_answer, score, correct, answered_at` — lịch sử
  từng lần trả lời trong section, dùng để build batch feed vào weak-point
  pipeline khi section kết thúc và để hiển thị lại chi tiết một attempt.

## 2. Sinh nội dung thư viện

- **Topic**: seed cố định (migration, chỉ tạo hàng topic rỗng từ).
- **Từ vựng**: sinh bằng LLM (tái dùng `AiContentClient`, cùng cách
  `LlmVocabPracticeGenerator` đang gọi), qua một bước "top-up" nội bộ: khi
  một topic không đủ từ *learner chưa từng gặp* (theo `vocabulary_weak_points`
  join), hệ thống tự gọi LLM sinh thêm N từ mới cho topic đó (loại trừ các từ
  đã có để tránh trùng), lưu vào `vocabulary_library_words`. Việc "mở một
  topic lần đầu" tự kích hoạt top-up nếu topic rỗng/thiếu — không cần thao
  tác admin riêng cho các topic đã seed.
- **Audio TTS**: sinh **ngay khi từ được tạo** (đồng bộ, cùng bước sinh từ —
  giống `ListeningLearnServiceImpl.generate`/`DialogueAudioSynthesizer`,
  không dùng job queue bất đồng bộ vì codebase hiện không có), qua
  `TtsClient` hiện có, lưu qua `StorageClient` với key
  `vocab-library/{topicId}/{wordId}.wav`. Nhờ vậy khi bắt đầu section không
  cần gọi TTS runtime.

## 3. Cơ chế Section (luyện tập lặp lại trong phiên)

**Bắt đầu section**: chọn `sectionSize` từ (mặc định 10, min 5, max 20) từ
một topic, ưu tiên từ chưa "thạo" (mastery_level null hoặc thấp trong
`vocabulary_weak_points`), bù thêm từ ngẫu nhiên trong topic nếu chưa đủ.
Ghi một hàng `vocabulary_section_attempts` (status IN_PROGRESS), khởi tạo
queue = danh sách N từ theo thứ tự xáo trộn, mỗi từ có `streak = 0`.

**Loại thẻ (card)**:
- **INTRO** (chỉ lần đầu một từ xuất hiện trong section): flashcard không
  chấm điểm — hiện từ, nghĩa, ví dụ, nút phát audio, learner bấm "Đã hiểu"
  để tiếp tục. Giúp learner gặp từ mới trước khi bị hỏi (giống Duolingo).
- **QUIZ** (mọi lần xuất hiện sau): một trong 5 dạng bài, chọn ngẫu nhiên mỗi
  lần trình bày lại (đa dạng hoá, tránh học vẹt vị trí câu hỏi):
  - `MCQ` — tái dùng `VocabQuestionType.MCQ`; đáp án nhiễu lấy ngẫu nhiên từ
    các từ khác cùng topic (không cần LLM/storage riêng cho distractor).
  - `CLOZE` — tái dùng `VocabQuestionType.CLOZE`, dùng `example_en` đã lưu,
    che từ mục tiêu.
  - `MATCHING` (chọn nghĩa đúng) — tái dùng `VocabQuestionType.MATCHING`,
    nghĩa nhiễu lấy từ các từ khác cùng topic.
  - `LISTENING_DICTATION` (mới) — phát audio đã sinh sẵn của từ, learner gõ
    lại; chấm bằng `DictationScorer.score` (WER, đã có sẵn, hoạt động tốt cả
    với input 1 từ).
  - `TRANSLATE` (mới, hai chiều, chọn ngẫu nhiên mỗi lần):
    - EN→VI: cho từ tiếng Anh, learner gõ tự do nghĩa tiếng Việt — chấm bằng
      `OpenAnswerGrader` đã có (LLM chấm điểm liên tục, cùng cơ chế đang dùng
      cho câu hỏi OPEN của domain `listening`), vì nghĩa có thể diễn đạt
      nhiều cách.
    - VI→EN: cho nghĩa tiếng Việt, learner gõ từ tiếng Anh — so khớp chuẩn
      hoá chính xác (giống `VocabAttemptScorer.normalize`), không cần LLM.

**Ngưỡng đúng/sai**: `CARD_CORRECT_THRESHOLD = 0.7` cho các dạng chấm điểm
liên tục (`LISTENING_DICTATION`, `TRANSLATE` chiều EN→VI); các dạng còn lại
là nhị phân (khớp chính xác chuẩn hoá = đúng).

**Thuật toán queue (Leitner-lite trong phiên)**:
- `MASTERY_STREAK = 2` lần đúng liên tiếp → từ được coi là "thạo" trong
  phiên này, bị loại khỏi queue vĩnh viễn.
- Trả lời **đúng** nhưng chưa đạt streak: `streak++`, chèn lại từ xa hơn
  trong queue (`+6` thẻ hoặc cuối queue nếu queue ngắn hơn) — khoảng cách
  dãn ra theo mức độ nắm được, đúng tinh thần spaced-repetition/Leitner.
- Trả lời **sai**: `streak = 0`, chèn lại gần hơn (`+2` thẻ hoặc cuối queue
  nếu ngắn hơn) để gặp lại sớm, nhưng không phải ngay thẻ kế tiếp.
- Section kết thúc khi queue rỗng (mọi từ đạt streak yêu cầu) — luôn kết
  thúc hữu hạn vì streak/khoảng cách đều là hằng số xác định.
- Mỗi lần submit answer: chấm điểm, ghi một hàng `vocabulary_section_answers`,
  cập nhật `queue_state_json` trên `vocabulary_section_attempts`, trả về thẻ
  tiếp theo (hoặc `null` nếu đã xong) + tiến độ (`wordsRemaining/
  wordsMastered/totalWords`).

**Tích hợp weak-point pipeline**: khi section COMPLETED (queue rỗng) hoặc
learner chủ động "kết thúc sớm", build một `PracticeRedoRequest` từ **toàn
bộ** các hàng `vocabulary_section_answers` của attempt đó (mỗi lần trả lời
một từ là một `PracticeAttemptRequest`, `itemId = "vocab:" + word`,
`category = LearningCategories.VOCABULARY`) và gọi `PracticeService.redo`
**một lần** — một từ xuất hiện nhiều lần trong section sẽ tự nhiên kích hoạt
`isRecurredInBatch()` của `WeakPointScoringEngine`, đúng ý nghĩa khoa học của
recurrence boost. Đây là con đường tích hợp **duy nhất** vào weak-point/Kafka
— không xây pipeline thứ hai.

## 4. API (mới, dưới `vocabulary.learn`)

```
GET  /api/v1/learn/vocabulary/library/topics
       -> danh sách topic + số từ + % mastery của learner (query param userId)

POST /api/v1/learn/vocabulary/library/topics/{topicId}/sections
       body: { userId, sectionSize? }
       -> tạo vocabulary_section_attempts mới, trả về thẻ đầu tiên (INTRO hoặc QUIZ)

POST /api/v1/learn/vocabulary/library/sections/{sectionId}/answers
       body: { submittedAnswer } (rỗng/omit cho thẻ INTRO — chỉ "acknowledge")
       -> { correct, correctAnswer, explanation, progress, nextCard | null }

POST /api/v1/learn/vocabulary/library/sections/{sectionId}/finish
       -> kết thúc sớm, feed weak-point với phần đã làm, trả kết quả tổng kết

GET  /api/v1/learn/vocabulary/library/sections/history/{userId}
       -> lịch sử các section đã hoàn thành (giống getHistory hiện có)
```

Cập nhật `openapi.yaml` của `english-service` + `docs/API.md` trong cùng
commit với code, theo quy ước của repo.

## 5. Frontend (`RemeLearning_FE`, `src/features/learn/vocabulary/library/`)

Theo đúng pattern hiện có của `VocabularyLearnPage.tsx` (Tabs + hooks
react-query trong `hooks.ts`, `GenerateDialog`/`EmptyState` dùng chung):

- **`TopicLibraryPage.tsx`** — thêm tab "Thư viện" bên cạnh "Luyện tập"/"Lịch
  sử" trong `VocabularyLearnPage`, hoặc route con riêng — danh sách chủ đề
  dạng card (tên, số từ, % mastery), bấm vào để bắt đầu section.
- **`SectionRunner.tsx`** — hiển thị từng thẻ (INTRO hoặc 1 trong 5 dạng
  QUIZ), thanh tiến độ (`wordsMastered/totalWords`), phát audio cho
  INTRO/LISTENING_DICTATION qua `<audio>` element trỏ tới URL audio (tương tự
  cách domain `listening` đang stream audio qua endpoint riêng).
- **`SectionResultPanel.tsx`** — tổng kết khi section COMPLETED (accuracy,
  số từ đã thạo), nút "Luyện chủ đề khác"/"Luyện lại".

## 6. Testing

- Đơn vị thuần (không Spring context, theo quy ước repo): thuật toán queue
  Leitner-lite (mới kết thúc hữu hạn, đúng/sai reposition đúng công thức),
  scoring cho `LISTENING_DICTATION` (tái dùng `DictationScorerTest` style) và
  `TRANSLATE` chiều VI→EN (chuẩn hoá).
- Service-level test (`Mockito.mock`, không `@SpringBootTest`) cho luồng
  start-section / submit-answer / finish, đặc biệt: batch feed đúng vào
  `PracticeService.redo` khi section kết thúc, và `applyJavaComputedScore`
  cập nhật đúng `vocabulary_weak_points` qua `item_id` dùng chung.
- Mapper test theo pattern MyBatis hiện có nếu repo có (kiểm tra lại quy ước
  trước khi viết, vì một số mapper trong repo không có test riêng).
