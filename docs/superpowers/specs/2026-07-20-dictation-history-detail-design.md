# Dictation History Attempt Detail — Design

Date: 2026-07-20
Status: Approved

## Problem

On the "Nghe chép chính tả" (dictation) page, the "Lịch sử" (History) tab lists past
attempts (`DictationHistoryEntry`: title, skill, level, examType, accuracy, wer, attemptedAt) as
static, non-interactive cards. Clicking an entry does nothing. Learners have no way to review what
they got wrong on a past attempt.

Separately, the AI feedback shown right after submitting an attempt (`aiSuggestions`, computed by
`DictationAnalyzer.analyzeAttempt`) is only ever returned in the HTTP response — it is never
persisted, so it cannot be replayed later from History.

## Goals

- Clicking a History entry opens a detail view showing: the reference script text, the learner's
  submitted transcript, the specific words they got wrong, the AI suggestions generated for that
  attempt, and the accuracy/WER already shown on the card.
- Persist `aiSuggestions` at attempt-submission time so they can be replayed later.

## Non-goals

- Reproducing the exact inline, word-by-word highlighted diff used in the live post-attempt result
  screen (`AttemptResultPanel` / `DiffView`). See "Diff representation" below for why.
- Any change to the "Luyện nghe với AI" tab, forgetting-score prioritization, or LLM
  provider/fallback wiring — tracked as a separate design.

## Diff representation: flat mistake list, not inline diff

`dictation_misses` already records every wrong word for an attempt (`expected_word`, `actual_word`,
`tag`), populated from two sources during `submitAttempt`:
- the final-transcript diff (`DictationScorer.score(referenceText, userTranscript)`), and
- any sentence-mode retry mistakes (`extractSentenceMistakes`), for attempts made via the
  sentence-by-sentence runner.

It does not store word position/order, so the exact ordered array `DiffView` needs (interleaved
CORRECT/WRONG tokens in original sentence order) can't be reconstructed from it.

Re-deriving that array at read time by re-running `DictationScorer.score(referenceText,
userTranscript)` was considered and rejected: for sentence-mode attempts, the *final* transcript is
forced fully correct by the runner (the learner keeps retrying a sentence until it matches), so a
fresh diff would show zero mistakes — hiding exactly the mistakes the learner made along the way,
which are the ones that matter pedagogically and are the ones actually stored in `dictation_misses`.

Decision: History detail shows the reference text and transcript as plain read-only panels, plus a
flat list of mistake chips (`expected → actual`, tagged missing/substituted) sourced directly from
`dictation_misses`. This is correct for both attempt modes at the cost of not being an inline
highlighted render.

## Backend changes

### Schema (english-service, new Flyway migration `V9__dictation_attempt_suggestions.sql`)

```sql
ALTER TABLE dictation_attempts ADD COLUMN ai_suggestions TEXT;
```

Stored as a JSON-encoded array of strings (serialized/deserialized in `DictationServiceImpl` via the
service's existing `ObjectMapper` bean — no new MyBatis type handler). Null/absent for attempts
submitted before this change.

### `DictationServiceImpl.submitAttempt`

Persist `analysis.getSuggestions()` onto the `DictationAttempt` entity before `insertAttempt`, so the
suggestions shown in the moment are the same ones replayed later from History.

### New read path

- `DictationMapper`: new query joining `dictation_attempts` (by id + user_id, for ownership scoping)
  with the resolved reference text (`dictation_clips.script_text` or
  `dictation_practice_items.sentence_text`, whichever the attempt references) and title/skill/level/
  examType metadata (same join `findHistoryByUserId` already does), plus a second query for
  `dictation_misses` filtered by `attempt_id`.
- New DTO `DictationAttemptDetailDto`: `attemptId`, `title`, `skill`, `level`, `examType`,
  `referenceText`, `userTranscript`, `accuracy`, `wer`, `attemptedAt`, `mistakes:
  List<DictationMistakeDto>` (`expectedWord`, `actualWord`, `tag`), `aiSuggestions: List<String>`.
- New endpoint on `DictationController`: `GET /api/v1/dictation/history/{userId}/{attemptId}` —
  404 (`BusinessException.notFound`) if the attempt doesn't exist or doesn't belong to `userId`,
  matching the ownership-scoping convention already used by `/history/{userId}`.

### bff-service

- New client method on the dictation downstream client, new controller route
  `GET /api/v1/learners/{userId}/dictation/history/{attemptId}`, new
  `DictationAttemptDetailDto`/`DictationMistakeDto` in `bff-service`'s own dto package (proxy DTOs,
  per the repo's convention of not sharing domain classes across service boundaries).

### Docs (per repo convention, same change)

- `english-service/openapi.yaml` and `bff-service/openapi.yaml`: new endpoint.
- `docs/API.md`: new endpoint entry (mục lục + bảng tổng hợp + chi tiết).
- `docs/sequence/english-service/` and `docs/sequence/bff-service/`: new/updated sequence diagram
  for the history-detail read path.
- `docs/flow/english-service-data-flow.md`: note the new `ai_suggestions` column and its
  read/write shape.
- `Business.md` (in `RemeLearning_BA`): note that learners can now review what they got wrong on a
  past dictation attempt.
- `english-service/README.md` and `bff-service/README.md`: new endpoint.

## Frontend changes (RemeLearning_FE)

- New type `DictationAttemptDetail` and `DictationMistake` in `src/types/api.ts`.
- New API client method `getDictationAttemptDetail(userId, attemptId)` in `src/api/learners.ts`
  (`GET /learners/{userId}/dictation/history/{attemptId}`).
- New hook `useDictationAttemptDetail(userId, attemptId, enabled)` in
  `src/features/dictation/hooks.ts` — `enabled` gated on the dialog being open, so the detail is
  fetched lazily on click, not prefetched for every history row.
- `HistorySection` (`DictationPage.tsx`): each history card becomes clickable, opening a shadcn
  `Dialog` with the clicked `attemptId`.
- New component `AttemptDetailDialog.tsx`: header (title/skill/level/examType + accuracy/WER
  badges), reference text panel, transcript panel, mistake-chips list, and the existing
  `AiSuggestions` component reused as-is (same `suggestions: string[]` shape) for the persisted
  suggestions.
- New i18n keys (`vi.json`/`en.json`) for the dialog's labels.

## Testing

- Backend: unit tests for `DictationServiceImpl.submitAttempt` (suggestions now persisted) and the
  new detail-fetch path (found / not-found / wrong-user-id cases), following the existing
  plain-JUnit-5 + AssertJ + `Mockito.mock(...)` pattern (no `@SpringBootTest`).
- Frontend: no existing test suite convention was found for this feature area during research: this
  spec will follow whatever the FE's existing dictation-feature test coverage does today (checked at
  implementation time), rather than introducing a new pattern.
