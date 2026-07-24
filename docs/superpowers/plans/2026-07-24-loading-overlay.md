# Shared LoadingOverlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace every ad-hoc "waiting for AI" loading indicator across the learn/practice features with one shared, blocking `<LoadingOverlay>` component.

**Architecture:** One new presentational component in `src/components/common/`, then a mechanical per-page swap: wrap the relevant card/form in a `relative` container and render `<LoadingOverlay show={mutation.isPending} label={...} />` inside it, removing the old ad-hoc spinner/disabled-state code for that specific wait.

**Tech Stack:** React + TypeScript + Tailwind (RemeLearning_FE), no new dependencies.

## Global Constraints

- No automated test file is required for this UI-only change (matches this repo's convention — sibling components like `GenerateDialog`/`AttemptResultShell` have no dedicated test file).
- Verify every task with `npx tsc --noEmit -p tsconfig.app.json` (zero errors) and `npx oxlint` on changed files (zero new issues).
- Do not touch skeleton/list loading states (initial fetch, tab-switch skeletons) — only AI-wait states (submit-for-scoring, generate-practice, start-section).
- Add 2 new i18n keys, `common.grading` and `common.generating`, to both `src/i18n/locales/en.json` and `src/i18n/locales/vi.json` — reuse these everywhere instead of each page inventing its own wait-text.

---

## Task 1: `LoadingOverlay` component + i18n keys

**Files:**
- Create: `src/features/../../components/common/LoadingOverlay.tsx` — actual path: `src/components/common/LoadingOverlay.tsx`
- Modify: `src/i18n/locales/en.json`
- Modify: `src/i18n/locales/vi.json`

**Interfaces:**
- Produces: `LoadingOverlay({ show, label }: { show: boolean; label?: string }): JSX.Element | null` — default export or named export, match this codebase's existing convention in `src/components/common/` if that folder already exists (check first), otherwise match `src/components/ui/`'s export style.

- [ ] **Step 1: Check whether `src/components/common/` exists and what export style sibling components use**

Run: `ls src/components/` and, if `common/` exists, read one file inside it; otherwise read `src/components/ui/radio-group.tsx` (a recently-added component) for the export/import convention to match.

- [ ] **Step 2: Write the component**

```tsx
import { Loader2 } from "lucide-react";

interface LoadingOverlayProps {
  show: boolean;
  label?: string;
}

// Blocking overlay for an in-progress AI action (grading, generating).
// Must be rendered inside a `relative`-positioned parent — it covers only
// that parent, not the whole page, and swallows clicks via pointer-events.
export function LoadingOverlay({ show, label }: LoadingOverlayProps) {
  if (!show) return null;

  return (
    <div
      className="absolute inset-0 z-50 flex flex-col items-center justify-center gap-2 rounded-md bg-background/70 backdrop-blur-sm pointer-events-auto"
      role="status"
      aria-live="polite"
      aria-busy="true"
    >
      <Loader2 className="h-8 w-8 animate-spin text-primary" />
      <p className="text-sm text-muted-foreground">{label ?? "Đang xử lý..."}</p>
    </div>
  );
}
```

Adjust the default label to use the i18n `common.grading` key's Vietnamese
value if this codebase's convention is to hardcode a Vietnamese default
rather than calling `useTranslation()` inside a shared component without
control over which key applies — check how `GenerateDialog.tsx` or another
shared component handles a similar generic default text, and match that
approach (likely: caller always passes `label`, so the fallback text here
rarely renders — keep it simple).

- [ ] **Step 3: Add i18n keys**

Add to both `en.json` and `vi.json` under a `common` top-level key (create it
if it doesn't exist — check first):

```json
"common": {
  "grading": "Đang chấm điểm...",
  "generating": "Đang tạo bài luyện tập..."
}
```

(English file gets the English translation, e.g. `"grading": "Grading your answer..."`,
`"generating": "Generating practice content..."` — match this repo's actual
bilingual convention by reading a few existing key pairs first.)

- [ ] **Step 4: Typecheck**

Run: `npx tsc --noEmit -p tsconfig.app.json`
Expected: zero errors.

- [ ] **Step 5: Commit**

```bash
git add src/components/common/LoadingOverlay.tsx src/i18n/locales/en.json src/i18n/locales/vi.json
git commit -m "feat(common): add shared LoadingOverlay component for AI-wait states"
```

---

## Task 2: Dictation — convert both full-card-replacement waits

**Files:**
- Modify: `src/features/dictation/DictationLessonPage.tsx`
- Modify: `src/features/dictation/DictationAiPracticePage.tsx`

**Interfaces:**
- Consumes: `LoadingOverlay` from Task 1.

- [ ] **Step 1: Read both files' `view === "submitting"` branches in full**

Note exactly what JSX is currently swapped in when submitting (likely the
entire runner card is replaced by a centered `Loader2` + text block) and
what JSX renders in the non-submitting state.

- [ ] **Step 2: Restructure so the runner card always renders, wrapped in `relative`, with `LoadingOverlay` layered on top**

Replace the `view === "submitting" ? <LoaderBlock /> : <RunnerCard />`
conditional with:

```tsx
<div className="relative">
  <RunnerCard /* same props as before, always rendered now */ />
  <LoadingOverlay show={view === "submitting"} label={t("common.grading")} />
</div>
```

Remove the old dedicated submitting-branch JSX entirely — the card itself no
longer unmounts during submission (this also means the card's local state,
e.g. the learner's typed answer, stays visible instead of disappearing,
which is a minor UX improvement, not a regression).

- [ ] **Step 3: Typecheck + lint**

Run: `npx tsc --noEmit -p tsconfig.app.json` (zero errors) and
`npx oxlint src/features/dictation/DictationLessonPage.tsx src/features/dictation/DictationAiPracticePage.tsx` (zero issues).

- [ ] **Step 4: Commit**

```bash
git add src/features/dictation/DictationLessonPage.tsx src/features/dictation/DictationAiPracticePage.tsx
git commit -m "refactor(dictation): use shared LoadingOverlay instead of full-card replacement"
```

---

## Task 3: Dictation — `GenerateAiPracticeDialog` + per-row "generate again"

**Files:**
- Modify: `src/features/dictation/GenerateAiPracticeDialog.tsx`
- Modify: `src/features/dictation/DictationPage.tsx`

**Interfaces:**
- Consumes: `LoadingOverlay` from Task 1.

- [ ] **Step 1: Read `GenerateAiPracticeDialog.tsx`'s `generate.isPending` usage in full**

Note the dialog's content structure (likely a form inside `DialogContent`) —
the overlay needs to wrap the form area, and the dialog's own `relative`
positioning may already exist via shadcn's `Dialog` primitives (check).

- [ ] **Step 2: Wrap the dialog's form content in `relative` and add the overlay**

```tsx
<div className="relative">
  {/* existing form fields, disabled via generate.isPending as before */}
  <LoadingOverlay show={generate.isPending} label={t("common.generating")} />
</div>
```

Keep the existing `disabled={generate.isPending}` on inputs/close button as
belt-and-suspenders (the overlay's `pointer-events-auto` already blocks
clicks, but don't remove the disabled attributes — redundant safety is fine
here, per this plan's Global Constraints not mandating their removal).

- [ ] **Step 3: Read `DictationPage.tsx`'s per-history-row "generate again" button in full**

Find the exact JSX structure of one history row (a `<div>` or `<Card>` per
entry) and the `generateFromAttempt.isPending && generateFromAttempt.variables?.attemptId === entry.attemptId` condition.

- [ ] **Step 4: Wrap that single row in `relative` and overlay it (not the whole list)**

```tsx
<div key={entry.attemptId} className="relative">
  {/* existing row content */}
  <LoadingOverlay
    show={generateFromAttempt.isPending && generateFromAttempt.variables?.attemptId === entry.attemptId}
    label={t("common.generating")}
  />
</div>
```

- [ ] **Step 5: Typecheck + lint**

Run: `npx tsc --noEmit -p tsconfig.app.json` and
`npx oxlint src/features/dictation/GenerateAiPracticeDialog.tsx src/features/dictation/DictationPage.tsx`.

- [ ] **Step 6: Commit**

```bash
git add src/features/dictation/GenerateAiPracticeDialog.tsx src/features/dictation/DictationPage.tsx
git commit -m "refactor(dictation): use shared LoadingOverlay for generate dialog and per-row retry"
```

---

## Task 4: Shared `GenerateDialog.tsx` (grammar/vocabulary/listening)

**Files:**
- Modify: `src/features/learn/shared/GenerateDialog.tsx`

**Interfaces:**
- Consumes: `LoadingOverlay` from Task 1.
- Produces: no interface change — same props as before, purely an internal
  rendering change.

- [ ] **Step 1: Read the full file, confirm it's genuinely shared by grammar/vocabulary/listening**

Grep: `grep -rn "GenerateDialog" src/features/learn/` to confirm which
pages import it, and check whether `src/features/learn/speaking/SpeakingLearnPage.tsx`
also imports this same shared component or has its own separate dialog
(per the spec's note to verify this at code time).

- [ ] **Step 2: Wrap the dialog's form content in `relative`, add `LoadingOverlay show={isGenerating}`**

Same pattern as Task 3 Step 2 — find the prop name (`isGenerating` per the
brief's earlier research) and wire it exactly the same way.

- [ ] **Step 3: If `speaking/SpeakingLearnPage.tsx` has its own separate generate dialog (not this shared one), repeat Steps 1-2's edit there too in this same task**

If it does turn out to reuse `shared/GenerateDialog.tsx`, skip this step —
Task 4's single edit already covers speaking too, and Task 7 (below) doesn't
need to touch the generate dialog at all.

- [ ] **Step 4: Typecheck + lint**

Run: `npx tsc --noEmit -p tsconfig.app.json` and
`npx oxlint src/features/learn/shared/GenerateDialog.tsx` (and speaking's
own dialog file if Step 3 applied).

- [ ] **Step 5: Commit**

```bash
git add src/features/learn/shared/GenerateDialog.tsx
git commit -m "refactor(learn): use shared LoadingOverlay in the shared GenerateDialog"
```

---

## Task 5: Grammar — `GrammarRunner`, `GrammarSessionRunner`, `GrammarTopicContentPage`

**Files:**
- Modify: `src/features/learn/grammar/GrammarRunner.tsx`
- Modify: `src/features/learn/grammar/library/GrammarSessionRunner.tsx`
- Modify: `src/features/learn/grammar/library/GrammarTopicContentPage.tsx`

**Interfaces:**
- Consumes: `LoadingOverlay` from Task 1.

- [ ] **Step 1: Read all three files' current `isSubmitting`/`isPending`-driven button-spinner code**

Each currently only spins the submit `Button` (via its `loading` prop) — the
rest of the card (question text, radio/text inputs) stays interactive
during the wait. This task changes that to also block interaction.

- [ ] **Step 2: `GrammarRunner.tsx` — wrap the question card in `relative`, add overlay**

```tsx
<div className="relative">
  {/* existing question/options JSX */}
  <LoadingOverlay show={isSubmitting} label={t("common.grading")} />
</div>
```

Keep the submit `Button`'s own `loading={isSubmitting}` prop as-is (visual
redundancy is harmless and matches Task 3's "keep existing disabled state"
precedent) — the important change is that the overlay now also blocks input
interaction, which the button spinner alone did not do.

- [ ] **Step 3: `GrammarSessionRunner.tsx` — same pattern for `submitAnswer.isPending || finishSession.isPending`**

```tsx
<div className="relative">
  {/* existing question/options JSX */}
  <LoadingOverlay
    show={submitAnswer.isPending || finishSession.isPending}
    label={t("common.grading")}
  />
</div>
```

- [ ] **Step 4: `GrammarTopicContentPage.tsx` — same pattern for `startSession.isPending`**

```tsx
<div className="relative">
  {/* existing topic content / start button area */}
  <LoadingOverlay show={startSession.isPending} label={t("common.generating")} />
</div>
```

- [ ] **Step 5: Typecheck + lint**

Run: `npx tsc --noEmit -p tsconfig.app.json` and `npx oxlint` on all three
changed files.

- [ ] **Step 6: Commit**

```bash
git add src/features/learn/grammar/GrammarRunner.tsx src/features/learn/grammar/library/GrammarSessionRunner.tsx src/features/learn/grammar/library/GrammarTopicContentPage.tsx
git commit -m "refactor(grammar): use shared LoadingOverlay for submit/generate waits"
```

---

## Task 6: Vocabulary — `VocabRunner`, `SectionRunner`, `TopicLibraryPanel`

**Files:**
- Modify: `src/features/learn/vocabulary/VocabRunner.tsx`
- Modify: `src/features/learn/vocabulary/library/SectionRunner.tsx`
- Modify: `src/features/learn/vocabulary/library/TopicLibraryPanel.tsx`

**Interfaces:**
- Consumes: `LoadingOverlay` from Task 1.

- [ ] **Step 1: Read all three files' current wait-state code**

`VocabRunner.tsx` (`submit.isPending`), `SectionRunner.tsx`
(`submitSectionAnswer.isPending`), `TopicLibraryPanel.tsx`
(`startSection.isPending` — this one overlays the specific topic card being
started, not the whole grid, matching Task 3 Step 4's per-row precedent).

- [ ] **Step 2: Apply the same `relative` wrapper + `LoadingOverlay` pattern to all three**

`VocabRunner.tsx`/`SectionRunner.tsx` use `label={t("common.grading")}`;
`TopicLibraryPanel.tsx` uses `label={t("common.generating")}` and wraps only
the individual topic card whose `startSection.variables?.topicId` matches
that card's id (mirroring Task 3 Step 4's per-row scoping, not the whole
grid).

- [ ] **Step 3: Typecheck + lint**

Run: `npx tsc --noEmit -p tsconfig.app.json` and `npx oxlint` on all three
changed files.

- [ ] **Step 4: Commit**

```bash
git add src/features/learn/vocabulary/VocabRunner.tsx src/features/learn/vocabulary/library/SectionRunner.tsx src/features/learn/vocabulary/library/TopicLibraryPanel.tsx
git commit -m "refactor(vocabulary): use shared LoadingOverlay for submit/generate waits"
```

---

## Task 7: Listening — `ListeningQuestions`, `SectionRunner`, `TopicLibraryPanel`

**Files:**
- Modify: `src/features/learn/listening/ListeningQuestions.tsx`
- Modify: `src/features/learn/listening/library/SectionRunner.tsx`
- Modify: `src/features/learn/listening/library/TopicLibraryPanel.tsx`

**Interfaces:**
- Consumes: `LoadingOverlay` from Task 1.

- [ ] **Step 1: Read all three files' current wait-state code**

Same shape as Task 6: `ListeningQuestions.tsx` (`submit.isPending`),
`SectionRunner.tsx` (`submitAnswers.isPending`), `TopicLibraryPanel.tsx`
(`startSection.isPending`, per-card scoped).

- [ ] **Step 2: Apply the same pattern as Task 6 Step 2 to all three files**

- [ ] **Step 3: Typecheck + lint**

Run: `npx tsc --noEmit -p tsconfig.app.json` and `npx oxlint` on all three
changed files.

- [ ] **Step 4: Commit**

```bash
git add src/features/learn/listening/ListeningQuestions.tsx src/features/learn/listening/library/SectionRunner.tsx src/features/learn/listening/library/TopicLibraryPanel.tsx
git commit -m "refactor(listening): use shared LoadingOverlay for submit/generate waits"
```

---

## Task 8: Speaking — `SpeakingLearnPage`, `SectionRunner`, `TopicLibraryPanel`

**Files:**
- Modify: `src/features/learn/speaking/SpeakingLearnPage.tsx`
- Modify: `src/features/learn/speaking/library/SectionRunner.tsx`
- Modify: `src/features/learn/speaking/library/TopicLibraryPanel.tsx`

**Interfaces:**
- Consumes: `LoadingOverlay` from Task 1.

- [ ] **Step 1: Read all three files' current wait-state code**

`SpeakingLearnPage.tsx` (`submit.isPending`, plus the recorder needs to stay
visually present but non-interactive — check how the recorder component
accepts a `disabled` prop, since blocking via overlay alone may not stop a
`MediaRecorder` already in progress; the overlay applies only to the
post-recording submit wait, not the recording itself). `SectionRunner.tsx`
(`submitAttempt.isPending`, `finishSection.isPending` — two separate waits,
same overlay reused sequentially, not simultaneously, since only one can be
pending at a time in this flow). `TopicLibraryPanel.tsx`
(`startSection.isPending`, per-card scoped).

- [ ] **Step 2: Apply the same pattern as Task 6 Step 2 to all three files**

For `SectionRunner.tsx`, use `show={submitAttempt.isPending || finishSection.isPending}`
on a single overlay covering the sentence-runner card (both waits share the
same visual area, so one overlay instance suffices — don't add two stacked
overlays).

- [ ] **Step 3: Typecheck + lint**

Run: `npx tsc --noEmit -p tsconfig.app.json` and `npx oxlint` on all three
changed files.

- [ ] **Step 4: Commit**

```bash
git add src/features/learn/speaking/SpeakingLearnPage.tsx src/features/learn/speaking/library/SectionRunner.tsx src/features/learn/speaking/library/TopicLibraryPanel.tsx
git commit -m "refactor(speaking): use shared LoadingOverlay for submit/generate waits"
```

---

## Self-Review Notes

- **Spec coverage:** All 21 wait-points listed in the spec's §3 are covered
  across Tasks 2-8 (Dictation: 4, Grammar: 3 + shared dialog, Vocabulary: 3 +
  shared dialog, Listening: 3 + shared dialog, Speaking: 4). The shared
  `GenerateDialog.tsx` (Task 4) covers grammar/vocabulary/listening's
  generate-dialog wait in one edit; speaking's is handled explicitly in Task
  4 Step 3 or Task 8 depending on whether it's the same shared component.
- **Type consistency:** `LoadingOverlayProps` fixed in Task 1, consumed
  identically (`show`, `label`) in every later task — no drift.
- **Not in scope:** `*AttemptDetailDialog.tsx` history-replay `isLoading`
  states across all features — explicitly excluded per the spec (not an AI
  action, just a fetch-for-viewing).
