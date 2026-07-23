# Tab "Thư viện" cho Nghe hiểu & Nói/phát âm (sub-project B)

Date: 2026-07-23
Status: Approved for planning

## Bối cảnh

`english-service` hiện có bốn domain học/luyện tập: `vocabulary`, `grammar`,
`listening`, `speaking` (chấm phát âm — `pronunciation` package chỉ là bộ tổng
hợp weak-point, không phải domain luyện tập). `vocabulary.library` và
`grammar.library` đã có mô hình "Thư viện": 60 topic cố định
(`sequence_order`), tiến trình `LOCKED`/`IN_PROGRESS`/`PASSED` theo thứ tự
(topic sau chỉ mở khi topic trước đã `PASSED`), nội dung sinh bởi LLM và lưu
ngân hàng để tái dùng, luyện tập theo "Section" (bấm topic → bắt đầu section →
trả lời → finish). `listening` và `speaking` hiện chỉ có flow phẳng
"generate on demand → làm bài → lịch sử", không có tab Thư viện, không có
khái niệm topic cố định.

Tính năng này thêm tab "Thư viện" cho `listening` và `speaking`, đầy đủ mức độ
như `grammar`/`vocabulary` (topic cố định + gating + ngân hàng nội dung
LLM-generated + Section), dùng chung bộ 60 topic (tên/thứ tự) với
grammar/vocabulary để trải nghiệm nhất quán xuyên 4 kỹ năng — nhưng theo đúng
quy ước hiện có của repo, mỗi domain vẫn tự sở hữu bảng/migration/package
riêng (không share bảng topic vật lý giữa các domain).

**Sửa đổi sau khi bắt tay triển khai (2026-07-23):** phương án ban đầu định
trích xuất logic gating LOCKED/IN_PROGRESS/PASSED thành một hàm tính-lại
thuần (pure, stateless) dùng chung trong `common`, rồi refactor lại
`grammar.library`/`vocabulary.library` để gọi hàm đó. Khi bắt tay code mới
phát hiện tiền đề này sai: `GrammarLibraryServiceImpl` **không** tính gating
bằng cách quét lại từ đầu — nó dùng một **state machine 4 trạng thái**
(`LOCKED`/`UNLOCKED`/`IN_PROGRESS`/`PASSED`) lưu persistent trong bảng
`grammar_topic_progress`, với các lệnh ghi tường minh
(`markInProgress`/`markPassed`/`unlockIfLocked`) tại đúng thời điểm chuyển
trạng thái — không phải một phép tính lại toàn bộ danh sách mỗi lần đọc.
Ngoài ra `VocabularyLibraryServiceImpl` **hoàn toàn không có** gating theo
topic (mọi topic từ vựng hiện mở tự do). Ép 2 class này dùng chung một hàm
3-trạng thái sẽ vừa làm mất trạng thái `UNLOCKED` (thay đổi hành vi quan sát
được, vỡ test hiện có của grammar) vừa không có gì thật sự trùng lặp để loại
bỏ ở phía vocabulary.

Quyết định: **giữ nguyên** `grammar.library`/`vocabulary.library` như hiện
tại (không refactor, không trích xuất `common`). `listening.library` và
`speaking.library` mới sẽ **clone chính xác** state machine 4 trạng thái của
`grammar.library` (bảng progress riêng theo domain, cùng shape cột, cùng 6
mapper method, cùng guard method ở service layer) — xem mục 1 bên dưới.

## 1. Mô hình gating (clone từ `grammar.library`)

Mỗi domain mới (`listening`, `speaking`) tự có enum + bảng + mapper progress
riêng, cấu trúc giống hệt `grammar.library`'s:

```java
public enum ListeningTopicStatus { LOCKED, UNLOCKED, IN_PROGRESS, PASSED }
// (SpeakingTopicStatus tương tự cho domain speaking)
```

Bảng `listening_topic_progress`/`speaking_topic_progress`: `id, user_id,
topic_id (FK), status (varchar), unlocked_at, passed_at, updated_at`, ràng
buộc `UNIQUE (user_id, topic_id)` — y hệt `grammar_topic_progress`.

Mapper (6 method, tên và ngữ nghĩa giống hệt `GrammarTopicProgressMapper`):
`findByUserIdAndTopicId`, `findByUserId`, `bootstrapFirstTopic` (insert
UNLOCKED cho topic đầu tiên nếu chưa có, `ON CONFLICT DO NOTHING`),
`unlockIfLocked` (insert UNLOCKED nếu chưa có hàng, hoặc upsert
`status = 'UNLOCKED'` **chỉ khi** hàng hiện tại đang `LOCKED` — dùng
`ON CONFLICT ... DO UPDATE ... WHERE status = 'LOCKED'`), `markInProgress`,
`markPassed` (unconditional UPDATE theo `(user_id, topic_id)`).

Service layer: `listTopics` bootstrap topic đầu tiên rồi map mọi topic sang
status (mặc định `LOCKED` nếu chưa có hàng progress) — giống
`GrammarLibraryServiceImpl.listTopics`. `startOrResumeSection` gọi guard
`requireUnlockedOrInProgress` (chỉ chặn `LOCKED`, coi hàng thiếu là `LOCKED`)
trước khi tạo/lấy section, rồi gọi `markInProgress`. Khi learner đạt ngưỡng
đạt (0.7) ở bước chấm điểm cuối (listening: `submitAnswers`; speaking:
`finishSection`), gọi `markPassed` cho topic hiện tại rồi
`unlockIfLocked` cho topic kế tiếp theo `sequence_order + 1` (nếu tồn tại) —
y hệt `GrammarLibraryServiceImpl.buildPassedResponse`. Không mượn phần sinh
lại câu hỏi retry theo từng câu sai của grammar (đó là một tính năng khác,
phức tạp hơn) — chỉ mượn cơ chế gating/progress.

## 2. Data model mới

### `listening.library` (package `com.remelearning.english.listening.library`)

- **`listening_library_topics`**: `id, code, name, description, level,
  sequence_order, created_at` — seed 60 dòng trong migration, copy tên/mô tả/
  thứ tự từ `grammar_library_topics` (cùng nội dung chủ đề, id độc lập).
- **`listening_library_sections`**: `id, topic_id (FK), passage_text,
  audio_storage_key, created_at` — một đoạn văn (passage) TTS theo topic.
- **`listening_library_questions`**: `id, section_id (FK), question_text,
  options_json, correct_option, explanation, created_at` — câu hỏi MCQ gắn
  với 1 section.
- **`listening_topic_progress`**: `id, user_id, topic_id (FK), status
  (LOCKED/UNLOCKED/IN_PROGRESS/PASSED), unlocked_at, passed_at, updated_at`,
  `UNIQUE (user_id, topic_id)` — bảng state machine clone từ
  `grammar_topic_progress` (mục 1), không phải bảng tính-lại.
- **`listening_library_attempts`**: `id, user_id, section_id, score,
  correct_count, total_questions, started_at, completed_at` — lịch sử làm
  section.

### `speaking.library` (package `com.remelearning.english.speaking.library`)

- **`speaking_library_topics`**: cùng cấu trúc, seed từ cùng bộ 60 topic.
- **`speaking_library_sections`**: `id, topic_id (FK), created_at` — một
  section gồm N câu mẫu.
- **`speaking_library_sentences`**: `id, section_id (FK), sentence_text,
  ipa, sample_audio_storage_key, created_at` — câu mẫu để đọc theo.
- **`speaking_topic_progress`**: tương tự `listening_topic_progress` (mục 1).
- **`speaking_library_attempts`**: `id, user_id, section_id, sentence_id,
  phoneme_score, word_score, recorded_audio_storage_key, created_at` — mỗi
  câu đọc là một attempt (khác listening: chấm theo câu, không theo cả
  section cùng lúc), section hoàn thành khi mọi câu trong section có ít nhất
  một attempt đạt ngưỡng điểm.

## 3. Sinh nội dung (top-up on demand)

- **Listening**: `LlmListeningLibraryGenerator` — khi topic chưa có section
  nào (hoặc learner đã làm hết section hiện có), gọi LLM sinh 1 đoạn văn
  (độ dài theo `level` của topic) + 4-5 câu hỏi MCQ, lưu vào
  `listening_library_sections`/`listening_library_questions`. Audio sinh
  ngay khi tạo section (đồng bộ), tái dùng `TtsClient`/`DialogueAudioSynthesizer`
  đã có ở `listening` package hiện tại — không dùng job queue bất đồng bộ
  (đúng quy ước, xem spec vocabulary-library-sections).
- **Speaking**: `LlmSpeakingLibraryGenerator` — sinh N câu mẫu theo topic
  (độ khó theo `level`), audio mẫu sinh ngay bằng TTS như trên. Chấm điểm khi
  learner đọc lại **tái dùng nguyên** service/scoring hiện có ở `speaking`
  package (phoneme/word scoring) — không viết lại pipeline chấm điểm, chỉ đổi
  nguồn câu mẫu (từ ngân hàng library thay vì sinh ngẫu nhiên mỗi lần).

## 4. API mới

### Listening (`/api/v1/learn/listening/library`)

```
GET  /{userId}/topics
       -> danh sách 60 topic + status (LOCKED/UNLOCKED/IN_PROGRESS/PASSED)
POST /{userId}/topics/{topicId}/sections
       -> tạo/lấy section hiện có (top-up nếu cần), trả về passage + audio url + câu hỏi
POST /sections/{sectionId}/answers
       body: { userId, answers: [{questionId, selectedOption}] }
       -> chấm điểm, ghi listening_library_attempts, cập nhật topic_progress nếu PASSED
GET  /{userId}/sections/history
       -> lịch sử attempt đã hoàn thành
GET  /sections/{sectionId}/audio
       -> stream audio đoạn văn
```

### Speaking (`/api/v1/learn/speaking/library`)

```
GET  /{userId}/topics
POST /{userId}/topics/{topicId}/sections
       -> tạo/lấy section, trả về danh sách câu mẫu + audio mẫu
POST /sections/{sectionId}/sentences/{sentenceId}/attempts   (multipart audio)
       -> chấm phoneme/word (tái dùng speaking scoring), ghi speaking_library_attempts
POST /sections/{sectionId}/finish
       -> đóng section khi đủ câu đạt ngưỡng, cập nhật topic_progress
GET  /{userId}/sections/history
GET  /sentences/{sentenceId}/sample-audio
```

Cập nhật `openapi.yaml` của `english-service` trong cùng commit.

## 5. `bff-service` — proxy

Thêm method vào `EnglishServiceClient` + route trong `LearnerController` +
DTO cho toàn bộ endpoint ở mục 4 (mirror cách vocabulary library được proxy ở
commit `f67297a`). Cập nhật `openapi.yaml` của `bff-service`.

## 6. Frontend (`RemeLearning_FE`)

- Thêm tab "Thư viện" vào `ListeningLearnPage.tsx` và `SpeakingLearnPage.tsx`
  (hiện chỉ có `practice`/`history`), theo đúng vị trí/thứ tự tab đã dùng ở
  grammar/vocabulary.
- `src/features/learn/listening/library/`: `TopicLibraryPanel.tsx` (clone
  layout từ grammar/vocabulary — card lưới, status badge, progress),
  `SectionRunner.tsx` (nghe đoạn văn, trả lời MCQ), `hooks.ts` (gọi 5 endpoint
  ở mục 4). Bấm topic mở section trực tiếp — không có trang lý thuyết riêng
  (giống vocabulary, khác grammar).
- `src/features/learn/speaking/library/`: tương tự, `SectionRunner.tsx` ghi
  âm từng câu mẫu (tái dùng recorder component đã có ở `speaking` practice
  hiện tại), `hooks.ts` gọi 6 endpoint ở mục 4.

## 7. Docs (theo CLAUDE.md)

Cùng commit với code: `docs/API.md` (mục lục + bảng + chi tiết 11 endpoint
mới), `docs/sequence/english-service/` + `docs/sequence/bff-service/` (thêm
sequence diagram cho luồng start-section/submit-answer của mỗi domain),
`docs/flow/english-service-data-flow.md` (thêm bảng transform cho 2 domain
mới), README của `english-service` và `bff-service`, và
`Business.md` (`RemeLearning_BA`, ngoài repo này) mô tả ý nghĩa nghiệp vụ của
tab Thư viện cho nghe/nói.

## 8. Testing

- Pure unit test (không Spring context): `TopicProgressCalculatorTest` (mọi
  trường hợp gating), so sánh lại kết quả grammar/vocabulary sau refactor
  không đổi hành vi cũ.
- Service-level test (`Mockito.mock`) cho listening/speaking library service:
  top-up khi ngân hàng cạn, chấm điểm, cập nhật topic_progress khi PASSED.
- Không thêm test tích hợp DB thật (không có `@SpringBootTest`/integration
  test nào trong repo, giữ đúng quy ước).
