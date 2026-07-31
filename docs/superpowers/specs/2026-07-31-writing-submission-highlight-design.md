# Highlight lỗi ngay trên bài làm gốc (Writing/Translate)

Date: 2026-07-31
Status: Approved for planning

## Bối cảnh

Màn kết quả chấm bài viết/dịch (`WritingResultPanel.tsx`) hiện có các section
theo thứ tự: Tiêu chí → Nhận xét chung (`feedback`) → **Bản sửa của AI**
(`correctedText`, bản viết lại hoàn chỉnh) → **Các lỗi (N)** (danh sách lỗi,
mỗi lỗi là 1 card riêng: badge category, `label`, `wrong` gạch bỏ → `corrected`,
`explanationVi`) + nút "Luyện lại những lỗi này" → Bài mẫu/bản dịch tham chiếu
→ Gợi ý ôn tập (`actionAdvice`).

Bài làm **gốc** mà learner tự nhập (`submittedText`) không hiện ở màn kết quả
này — nó chỉ hiện dạng text thuần (không highlight) trong dialog xem lại lịch
sử (`WritingAttemptDetailDialog.tsx`). Yêu cầu: cho learner thấy ngay lỗi nằm
ở đâu trên chính bài mình viết, tại màn kết quả ngay sau khi nộp — không phải
chỉ đọc danh sách lỗi tách rời hay bản đã-sửa-sẵn.

**Khảo sát quan trọng trước khi thiết kế:** `WritingLearnPage.tsx` giữ văn bản
learner nhập trong state `draft`, và state này **vẫn còn nguyên** tại thời
điểm `view === "result"` render `WritingResultPanel` (không bị `setDraft("")`
sau khi submit thành công) — đúng cho cả luồng "practice"
(`submit.mutate({ practiceItemId, submittedText: draft })`) và luồng "library"
(`submitLibraryAnswer.mutate({ promptId, request: { submittedText: draft } })`).
Vì vậy tính năng này **không cần đổi API/DTO backend nào** — chỉ cần truyền
`draft` xuống làm prop mới của `WritingResultPanel`.

## Phạm vi

- Chỉ áp dụng cho `WritingResultPanel` (màn kết quả ngay sau khi nộp bài, cả
  2 luồng practice + library). **Không** áp dụng cho
  `WritingAttemptDetailDialog` (xem lại lịch sử) — giữ nguyên hiện trạng.
- **Không** đổi "Các lỗi (N)" list, **không** đổi "Gợi ý ôn tập"
  (`actionAdvice`) — cả 2 vẫn hiển thị y như hiện tại, section mới là bổ
  sung, không thay thế.
- **Không** đổi bất kỳ REST endpoint, DTO, hay Kafka topic nào. Thay đổi
  backend duy nhất là 1 câu trong system prompt của `LlmWritingGrader`
  (xem mục 3) — không phải thay đổi contract.

## 1. Section mới "Bài làm của tôi"

Thứ tự section sau khi thêm:

```
Tiêu chí → Nhận xét chung → Bài làm của tôi (MỚI) → Bản sửa của AI
→ Các lỗi (N) → Bài mẫu/bản dịch tham chiếu → Gợi ý ôn tập
```

- Tiêu đề: key i18n mới `learn.writing.yourSubmissionTitle` = "Bài làm của tôi".
- Render khi `submittedText` non-empty (luôn đúng trong thực tế, nhưng vẫn
  guard `submittedText ? (...) : null` theo đúng pattern các section khác
  trong file).
- Style bọc ngoài: tái dùng đúng class hiện có cho khối text
  (`whitespace-pre-line rounded-lg bg-muted p-3 text-sm`), giống khối
  "Bản sửa của AI"/"Bài mẫu".
- Đoạn lỗi (`wrong`/`corrected`) bên trong dùng đúng class đang dùng ở "Các
  lỗi" list: `text-destructive line-through` cho `wrong`,
  `font-medium text-emerald-600` cho `corrected` — để 2 nơi trông đồng bộ.

## 2. Thuật toán khớp lỗi với vị trí trong bài (Hướng A — sequential match)

Mỗi lỗi (`WritingError`) chỉ có `wrong`/`corrected` là chuỗi ngắn, không có
offset. Vì `wrong` có thể lặp lại nhiều lần trong bài, việc khớp đúng lần
xuất hiện dựa trên **thứ tự lỗi trong `errors[]` đúng với thứ tự xuất hiện
trong bài** + quét tuần tự từ vị trí kết thúc lỗi trước.

Hàm thuần mới (file mới, ví dụ
`RemeLearning_FE/src/features/learn/writing/writingSubmissionHighlight.ts`):

```ts
type Segment =
  | { type: "text"; value: string }
  | { type: "diff"; wrong: string; corrected: string }

function buildSubmissionSegments(text: string, errors: WritingError[]): Segment[]
```

Logic:
1. `cursor = 0`, `segments = []`.
2. Với mỗi `error` theo đúng thứ tự trong `errors`:
   - Bỏ qua nếu `!error.wrong` (một số lỗi chỉ có `corrected`, ví dụ lỗi
     thiếu từ — không có gì để highlight inline, vẫn hiện đủ ở "Các lỗi" list).
   - `idx = text.indexOf(error.wrong, cursor)`.
   - Nếu `idx === -1`: bỏ qua lỗi này cho highlight inline (không lùi
     `cursor`, không thử tìm lại từ đầu bài — tránh khớp sai vị trí). Lỗi
     vẫn hiển thị đầy đủ ở "Các lỗi" list, không có cảnh báo nào hiện cho
     learner (đây là hành vi hiển thị, không phải lỗi nghiệp vụ).
   - Nếu tìm thấy: đẩy `{ type: "text", value: text.slice(cursor, idx) }`
     (nếu không rỗng), đẩy
     `{ type: "diff", wrong: error.wrong, corrected: error.corrected ?? error.wrong }`,
     `cursor = idx + error.wrong.length`.
3. Sau loop: đẩy `{ type: "text", value: text.slice(cursor) }` nếu không rỗng.

Component render (cùng file hoặc file component riêng nhỏ, ví dụ
`SubmissionWithHighlights.tsx`): map `segments`, đoạn `text` render thẳng,
đoạn `diff` render `<span className="text-destructive line-through">{wrong}</span>{" "}<span className="font-medium text-emerald-600">{corrected}</span>`.

## 3. Thay đổi backend duy nhất: thứ tự `errors[]`

Sequential match ở mục 2 cần `errors[]` đúng thứ tự xuất hiện trong bài.
Thêm 1 câu vào `SYSTEM_PROMPT` của
`RemeLearning/services/english-service/src/main/java/com/remelearning/english/writing/grading/LlmWritingGrader.java`
(đoạn hướng dẫn liệt kê lỗi, hiện ở dòng 44): yêu cầu LLM liệt kê lỗi theo
đúng thứ tự chúng xuất hiện trong bài làm của learner (từ đầu đến cuối).
Không đổi field/schema `LlmError`/`WritingErrorItem`, không đổi
response DTO nào.

## 4. Thay đổi FE cụ thể

- `WritingResultPanelProps` (`WritingResultPanel.tsx`): thêm
  `submittedText: string`.
- `WritingResultPanel.tsx`: thêm section mới (mục 1), gọi
  `buildSubmissionSegments(submittedText, errors)` rồi render.
- `WritingLearnPage.tsx`: truyền `submittedText={draft}` vào
  `<WritingResultPanel ... />` (chỗ khởi tạo props hiện ở dòng ~326-339) cho
  cả nhánh "practice" và nhánh "library" (`result` object hiện được set từ
  2 nơi — dòng ~172-176 và ~186-199 — không cần đổi 2 nơi này vì
  `submittedText` lấy từ `draft` ngay tại JSX, không qua `result`).
- i18n: thêm key `learn.writing.yourSubmissionTitle` vào các file locale hiện
  có của namespace `learn.writing` (theo đúng cấu trúc key khác trong cùng
  namespace, ví dụ `correctedTitle`, `errorsTitle`).

## 5. Testing

- FE: unit test cho `buildSubmissionSegments` (Vitest, theo quy ước
  `frontend-standards`): case không có lỗi, case 1 lỗi khớp đơn giản, case
  từ lỗi lặp lại nhiều lần trong bài (đảm bảo khớp đúng lần xuất hiện theo
  thứ tự, không khớp lùi), case lỗi không tìm thấy trong bài (bị bỏ qua,
  không throw), case `wrong` rỗng/null (bị bỏ qua).
- BE: không có logic mới cần unit test — chỉ đổi câu chữ prompt, không đổi
  code path nào `LlmWritingGraderTest` đang assert. Không cần sửa test hiện
  có.
- Manual: chạy FE dev server, nộp 1 bài có ít nhất 1 từ lặp lại nhiều lần
  (chỉ 1 lần sai) để xác nhận highlight rơi đúng vị trí, và nộp 1 bài không
  có lỗi để xác nhận section mới hiện đúng text thuần không highlight gì.

## 6. Docs

Không cần cập nhật `openapi.yaml`, `docs/API.md`, `docs/sequence/`,
`docs/flow/`, README service, hay `Business.md` — không có REST
endpoint/Kafka topic/DTO/business flow nào thay đổi, chỉ là cải thiện cách
hiển thị dữ liệu đã có sẵn ở FE cộng với 1 câu prompt LLM.
