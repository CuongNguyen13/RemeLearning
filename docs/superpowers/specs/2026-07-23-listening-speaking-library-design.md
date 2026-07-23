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

Vì logic gating (LOCKED/IN_PROGRESS/PASSED) hiện bị lặp y hệt ở
`grammar.library` và `vocabulary.library`, tính năng này đồng thời trích xuất
logic đó vào `common` và refactor lại 2 domain hiện có để dùng chung, tránh
nhân bản lần thứ ba/tư.

## 1. `common` — trích xuất gating logic dùng chung

Thêm `com.remelearning.common.library.TopicProgressCalculator` (pure logic,
không phụ thuộc DB/mapper):

```java
public enum TopicStatus { LOCKED, IN_PROGRESS, PASSED }

public final class TopicProgressCalculator {
    // input: danh sách topic đã sort theo sequenceOrder + set các topicId
    // learner đã PASSED. output: Map<topicId, TopicStatus>.
    public static Map<Long, TopicStatus> compute(
        List<Long> topicIdsBySequenceOrder, Set<Long> passedTopicIds) { ... }
}
```

Quy tắc: topic đầu tiên (sequence_order nhỏ nhất) luôn ít nhất
`IN_PROGRESS`; mỗi topic sau chỉ `IN_PROGRESS`/`PASSED` nếu topic liền trước
đã `PASSED`, ngược lại `LOCKED`. Refactor `GrammarLibraryServiceImpl` và
`VocabularyLibraryServiceImpl` để gọi hàm này thay cho đoạn code gating hiện
tại của từng service — không đổi schema/API của 2 domain đó.

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
- **`listening_library_topic_progress`**: `user_id, topic_id, status
  (IN_PROGRESS/PASSED), best_score, updated_at` — trạng thái PASSED của
  learner cho từng topic (input cho `TopicProgressCalculator`).
- **`listening_library_attempts`**: `id, user_id, section_id, score,
  correct_count, total_questions, started_at, completed_at` — lịch sử làm
  section.

### `speaking.library` (package `com.remelearning.english.speaking.library`)

- **`speaking_library_topics`**: cùng cấu trúc, seed từ cùng bộ 60 topic.
- **`speaking_library_sections`**: `id, topic_id (FK), created_at` — một
  section gồm N câu mẫu.
- **`speaking_library_sentences`**: `id, section_id (FK), sentence_text,
  ipa, sample_audio_storage_key, created_at` — câu mẫu để đọc theo.
- **`speaking_library_topic_progress`**: tương tự listening.
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
       -> danh sách 60 topic + status (LOCKED/IN_PROGRESS/PASSED) + best_score
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
