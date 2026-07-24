# Nút "Làm lại" (AI/Thư viện) từ lịch sử — Ngữ pháp, Nghe hiểu, Nói/phát âm (sub-project A)

Date: 2026-07-24
Status: Approved for planning

## Bối cảnh

Dictation đã có flow "làm lại từ lịch sử" hoàn chỉnh: mỗi dòng lịch sử có 2 nút
— **"Làm lại"** (chỉ hiện khi dòng đó có `clipId`, điều hướng thẳng về
`/dictation/lesson/{clipId}`, không gọi BE) và **"Luyện tập với AI"** (luôn
hiện, gọi `DictationServiceImpl.generateAiPracticeFromAttempt(userId,
attemptId, translationLang)` — đọc chi tiết từ sai của **chính attempt đó**
từ bảng `dictation_misses`, sinh dialogue/practice item mới nhắm đúng lỗi
đó, rồi chuyển sang tab AI). Grammar, Listening, Speaking hiện có tab "Lịch
sử" chỉ đọc (mở dialog xem chi tiết, không có nút làm lại nào), và lịch sử
Thư viện của 3 domain này nằm tách biệt khỏi tab Lịch sử chính.

Tính năng này mang đúng 2 nút trên sang Grammar/Listening/Speaking, đồng
thời gộp lịch sử Thư viện vào chung 1 tab Lịch sử với lịch sử học thường.

**Dữ liệu lỗi chi tiết theo attempt (khảo sát thực tế trước khi thiết kế):**
- **Grammar học** (`GrammarAttemptDetailRow.itemsJson`/`answersJson`) và
  **Grammar thư viện** (`GrammarLibrarySessionAnswer.questionRef`/
  `submittedAnswer`/`correct`, join `GrammarLibrarySessionQuestion`): đã đủ
  chi tiết (câu hỏi, đáp án đúng/sai, loại câu hỏi) để sinh lại câu tương tự
  nhắm đúng loại lỗi.
- **Nghe hiểu học** (`ListeningAttemptDetailRow.resultsJson` — mảng
  `{prompt, yourAnswer, correctAnswer, correct, subScore, explanation}`):
  đã đủ chi tiết.
- **Nói/phát âm học** (`SpeakingAttemptDetailRow.wordScoresJson`,
  `weakPhonemesJson`): đã đủ chi tiết.
- **Nghe hiểu Thư viện** (`ListeningLibraryAttempt`: chỉ `score`,
  `correctCount`, `totalQuestions` — KHÔNG lưu từng câu đúng/sai) và **Nói
  Thư viện** (`SpeakingLibraryAttempt`: chỉ `phonemeScore`/`wordScore` tổng
  — KHÔNG lưu chi tiết phoneme/word nào sai): **chưa** có dữ liệu chi tiết,
  cần bổ sung bảng/cột lưu chi tiết lỗi ngay khi nộp bài, trước khi có thể
  sinh AI-retry cho 2 domain Thư viện này.

## 1. Bổ sung lưu chi tiết lỗi cho Thư viện Nghe hiểu & Nói

### Nghe hiểu Thư viện

Bảng mới `listening_library_attempt_answers` (migration mới, package
`listening.library`): `id, attempt_id (FK listening_library_attempts),
question_id (FK listening_library_questions), selected_option,
correct_option, is_correct, created_at`. `ListeningLibraryServiceImpl.
submitAnswers` ghi 1 hàng cho mỗi câu hỏi (đã tính đúng/sai trong vòng lặp
hiện có, chỉ cần persist thêm thay vì bỏ qua) — không đổi API response hiện
tại, chỉ thêm ghi nội bộ.

### Nói Thư viện

Thêm cột `weak_phonemes_json` (nullable TEXT) vào `speaking_library_attempts`
(migration `ALTER TABLE`), ghi danh sách phoneme/word có điểm dưới ngưỡng
của lần đọc đó — tái dùng đúng field `weakPhonemesJson`/logic phân tích đã
có ở `SpeakingAttemptDetailRow`/`SpeakingLearnServiceImpl` cho "học thường",
áp dụng lại cho `SpeakingLibraryServiceImpl.submitSentenceAttempt`.

## 2. Endpoint "generate-from-attempt" mới (theo khuôn dictation)

Mỗi domain có 1 hàm generator mới (theo pattern
`LlmDictationDialogueGenerator`/`DictationServiceImpl.
generateAiPracticeFromAttempt`): đọc dữ liệu lỗi của đúng 1 attempt/section,
gọi LLM sinh bài mới nhắm đúng lỗi đó, ghi vào practice-item bank hiện có
của domain đó (grammar: `grammar_practice_items`; listening:
listening practice items; speaking: speaking practice items — tái dùng
đúng bảng "học thường" hiện có, không tạo bảng mới), trả về danh sách practice
item đã refresh (giống `getAiPractice(userId)` của dictation).

```
POST /api/v1/learn/grammar/attempts/{attemptId}/ai-practice           (học)
POST /api/v1/learn/grammar/library/sessions/{sessionId}/ai-practice   (thư viện)
POST /api/v1/learn/listening/attempts/{attemptId}/ai-practice
POST /api/v1/learn/listening/library/sections/{sectionId}/ai-practice
POST /api/v1/learn/speaking/attempts/{attemptId}/ai-practice
POST /api/v1/learn/speaking/library/sections/{sectionId}/ai-practice
```

6 endpoint riêng (không gộp học+thư viện vào 1 endpoint) vì nguồn dữ liệu lỗi
khác bảng/khác shape giữa 2 flow của cùng 1 domain. Mỗi endpoint verify
ownership (user_id khớp) trước khi đọc, giống `DictationServiceImpl`.

`bff-service` proxy cả 6 endpoint, theo đúng pattern proxy hiện có.

## 3. Gộp lịch sử học + thư viện thành 1 danh sách (mỗi domain)

BE: mỗi domain thêm 1 endpoint tổng hợp lịch sử
(`GET /api/v1/learn/{domain}/history/{userId}` — hoặc tái dùng/mở rộng
endpoint history sẵn có nếu domain đã có 1 endpoint riêng cho học và 1 cho
thư viện) trả về danh sách hợp nhất, mỗi phần tử có `source: "LEARN" |
"LIBRARY"`, `attemptId` (hoặc `sessionId`/`sectionId` tương ứng), thời gian,
điểm, và với source `LIBRARY` kèm `topicId`/`sectionId` để FE điều hướng
"Làm lại". Sắp giảm dần theo thời gian ở tầng service (merge 2 query, sort
trong Java, không cần UNION SQL phức tạp vì mỗi domain chỉ có 2 nguồn).

FE: `HistorySection`-tương-đương của mỗi domain gọi 1 hook mới lấy danh sách
hợp nhất, render nhãn nguồn (badge "AI"/"Thư viện"), và 2 nút:
- **"Làm lại" (chỉ hiện khi `source === "LIBRARY"`)**: điều hướng
  `/learn/{domain}/library/topics/{topicId}` (grammar, có trang lý thuyết) hoặc
  thẳng vào section runner (listening/speaking, không có trang lý thuyết,
  theo đúng thiết kế Thư viện đã build ở sub-project B).
- **"Luyện tập với AI"** (luôn hiện): gọi đúng 1 trong 6 endpoint ở mục 2 tuỳ
  `source`, rồi chuyển sang tab "Luyện tập" hiện có của domain đó (không cần
  tab AI riêng như dictation vì grammar/listening/speaking's "học thường"
  tab đã sẵn là nơi hiện practice item AI-generated).

## 4. Testing

Theo đúng quy ước repo: unit test thuần (JUnit5+AssertJ+Mockito.mock) cho
generator mới (parse lỗi đúng, gọi LLM đúng tham số) và cho endpoint gộp
lịch sử (merge+sort đúng, gán đúng `source`). Không test tích hợp DB thật.

## 5. Docs

Theo CLAUDE.md: `openapi.yaml` (english-service + bff-service),
`docs/API.md`, `docs/sequence/English_service/*.md` (mỗi domain 1 diagram
mới cho generate-from-attempt), `docs/flow/english-service-data-flow.md`,
README từng service, và `Business.md` (RemeLearning_BA) — mô tả ý nghĩa
"luyện tập lại đúng lỗi sai" cho cả 3 domain.
