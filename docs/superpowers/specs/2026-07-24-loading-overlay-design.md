# `LoadingOverlay` dùng chung cho mọi điểm chờ AI (sub-project C)

Date: 2026-07-24
Status: Approved for planning

## Bối cảnh

Hiện tại không có component loading dùng chung nào cho các điểm chờ AI (submit
đáp án để chấm điểm, generate bài luyện tập mới, chấm điểm phát âm...) trong
`RemeLearning_FE`. Mỗi trang tự xử lý theo 2 kiểu khác nhau:

- **Kiểu 1 — thay cả card bằng `Loader2` + text** (`DictationLessonPage.tsx`,
  `DictationAiPracticePage.tsx`): khi `view === "submitting"`, cả runner card
  biến mất, chỉ còn spinner + "Đang chấm điểm...".
- **Kiểu 2 — chỉ spin nút submit** (grammar/vocabulary/listening/speaking,
  cả learn và library): `mutation.isPending` chỉ làm nút submit hiện spinner
  built-in (`loading` prop của `Button`), phần còn lại của card (câu hỏi,
  input) vẫn hiển thị bình thường và **không bị khoá tương tác** — người học
  có thể bấm lại/sửa input trong lúc chờ.

Tính năng này chuẩn hoá cả hai kiểu trên thành một component dùng chung, phủ
mờ đúng khu vực card/form đang chờ (không phủ toàn trang, sidebar/nav vẫn
dùng được) và **khoá tương tác trong khu vực đó** — bao gồm cả những chỗ
hiện đang chỉ spin nút (kiểu 2), để tránh double-submit nhất quán trên toàn
app.

## 1. Component

`src/components/common/LoadingOverlay.tsx` (mới):

```tsx
interface LoadingOverlayProps {
  show: boolean;
  label?: string; // mặc định: "Đang xử lý..."
}
```

- Render `null` khi `show === false`.
- Khi `show === true`: một lớp phủ `absolute inset-0` (yêu cầu container cha
  có `position: relative`), nền mờ (`bg-background/70 backdrop-blur-sm`),
  `pointer-events-auto` để chặn mọi click xuyên qua, giữa là `Loader2`
  (animate-spin) + `label` bằng `<p>`. `aria-live="polite"` +
  `aria-busy="true"` trên chính overlay để hỗ trợ screen reader (theo đúng
  quy ước `aria-busy`/`aria-live` đã dùng rải rác trong dictation hiện tại).
- Không có logic timeout/auto-hide — hiển thị hoàn toàn theo prop `show` do
  trang cha truyền vào (từ `mutation.isPending`).

## 2. Cách dùng — pattern chuẩn cho mọi trang

Mỗi khu vực card/form đang chờ AI được bọc trong 1 wrapper `relative`:

```tsx
<div className="relative">
  {/* card/form nội dung hiện có */}
  <LoadingOverlay show={submit.isPending} label={t("common.grading")} />
</div>
```

Label dùng key i18n chung `common.grading` ("Đang chấm điểm...") hoặc
`common.generating` ("Đang tạo bài luyện tập...") tuỳ ngữ cảnh — thêm 2 key
này vào `en.json`/`vi.json`, dùng lại cho mọi feature thay vì mỗi trang tự
định nghĩa text riêng.

## 3. Danh sách điểm cần chuyển đổi

Toàn bộ 21 điểm chờ AI hiện có (loại trừ các `isLoading` chỉ để fetch lại
lịch sử cũ, không phải một hành động AI đang chạy):

**Dictation:**
- `DictationLessonPage.tsx` — thay khối `view === "submitting"` (Loader2 thay
  cả card) bằng `LoadingOverlay` phủ lên card runner hiện có, không unmount
  card nữa.
- `DictationAiPracticePage.tsx` — tương tự.
- `GenerateAiPracticeDialog.tsx` — `generate.isPending` phủ lên nội dung
  dialog.
- `DictationPage.tsx` (nút "generate again" theo từng history row) —
  `generateFromAttempt.isPending && ...attemptId === entry.attemptId` phủ
  lên đúng row đó (không phải cả danh sách).

**Grammar (learn + library):** `GrammarRunner.tsx` (`submit.isPending`),
`GenerateDialog.tsx` dùng chung (`generate.isPending`),
`GrammarSessionRunner.tsx` (`submitAnswer.isPending || finishSession.isPending`),
`GrammarTopicContentPage.tsx` (`startSession.isPending`).

**Vocabulary (learn + library):** `VocabRunner.tsx` (`submit.isPending`),
`GenerateDialog.tsx` (dùng chung, đã liệt kê ở grammar),
`SectionRunner.tsx` (`submitSectionAnswer.isPending`),
`TopicLibraryPanel.tsx` (`startSection.isPending`).

**Listening (learn + library):** `ListeningQuestions.tsx`
(`submit.isPending`), `GenerateDialog.tsx` (dùng chung),
`SectionRunner.tsx` (`submitAnswers.isPending`), `TopicLibraryPanel.tsx`
(`startSection.isPending`).

**Speaking (learn + library):** `SpeakingLearnPage.tsx` (`submit.isPending`),
`GenerateDialog.tsx` (dùng chung), `SectionRunner.tsx`
(`submitAttempt.isPending`, `finishSection.isPending`),
`TopicLibraryPanel.tsx` (`startSection.isPending`).

`shared/GenerateDialog.tsx` là component dùng chung cho 3 feature
(grammar/vocabulary/listening) — sửa 1 lần ở đây áp dụng cho cả 3, chỉ cần
sửa riêng `speaking/SpeakingLearnPage.tsx`'s generate dialog nếu nó không tái
dùng `shared/GenerateDialog.tsx` (kiểm tra lại lúc code).

## 4. Việc KHÔNG đổi

- Không đổi bất kỳ loading kiểu skeleton/danh sách nào (tab switch, initial
  fetch, `*AttemptDetailDialog.tsx`'s `isLoading` khi mở xem lại lịch sử) —
  đây không phải một hành động AI đang chạy.
- Không thêm cơ chế tự động toàn cục qua `QueryClient`'s `isFetching`/
  `isMutating` — mỗi trang tiếp tục tự truyền `show` từ đúng mutation của nó.

## 5. Testing

Đây là thay đổi UI thuần, không có test tự động theo quy ước hiện tại của
`RemeLearning_FE` cho các component tương tự (`GenerateDialog`,
`AttemptResultShell` không có test riêng) — verify bằng `tsc --noEmit` +
`oxlint`, không cần test file mới.
