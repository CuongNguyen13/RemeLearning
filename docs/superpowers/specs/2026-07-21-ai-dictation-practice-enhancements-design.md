# AI Dictation Practice — Enhancements & Bug Fixes

Date: 2026-07-21
Status: Approved for planning

## Context

"Nghe chép chính tả" (dictation) has an "AI practice" mode where
`LlmDictationDialogueGenerator` asks Gemini to write a short dialogue from a
learner's missed words, which is then TTS-synthesized and presented for
dictation. There are two independent entry points that both end up creating a
`dictation_practice_items` row:

- **Path A** — the "Luyện tập AI" button (`AiPracticeSection` in
  `DictationPage.tsx`) → `DictationServiceImpl.generateAiPractice` → always
  produces one multi-speaker dialogue (≥10 sentences) via
  `LlmDictationDialogueGenerator`.
- **Path B** — "Luyện tập với AI" from a history/attempt row →
  `DictationServiceImpl.generateAiPracticeFromAttempt` → currently uses the
  rule-based `dictationAnalyzer.generatePracticeSentences`, which produces
  many single-sentence practice items instead of one dialogue/short passage.

There is also a separate, older flow, the Library ("Thư viện") tab, backed by
`dictation_clips`/`dictation_clip_sentences` — real human-recorded audio with
a pre-existing `skill/level/topic/exam_type` taxonomy but no translation.

This change fixes a grading bug in Path A, unifies Path B onto the same
generator, adds level/exam-type selection (with per-field "random") to Path
A's creation flow, surfaces the resolved level/topic on history cards, and
adds a per-sentence translation to the user's UI language across all three
surfaces (Path A, Path B, and the Library).

## 1. Bug fix: audio text ≠ graded answer text

**Root cause** (`DictationServiceImpl.synthesizeDialoguePracticeItem`,
english-service, lines 537-560): for a multi-speaker dialogue, the TTS call
synthesizes only `line.text()` (e.g. *"Hello, you must be Ben..."*), but the
text persisted as `sentence_text` — later used both as the on-screen sentence
and as the reference answer for grading — is `"Anna: Hello, you must be
Ben..."`. Learners hear no name but are graded against a leading `"Anna: "`.

**Fix**: keep the graded/displayed text as `"Speaker: text"` (unchanged,
since it's useful context for a multi-speaker conversation), and make the TTS
audio match it — synthesize the full line, spoken name included, e.g.
`"Anna: Hello, you must be Ben..."`, so the two are always in sync by
construction. This applies to both Path A and Path B once unified (§3).

## 2. Topic name for generated dialogues

**Contract change**: `LlmDictationDialogueGenerator`'s prompt currently asks
for a raw JSON array of `{speaker, text}`. Change it to ask for a single JSON
object:

```json
{
  "topic": "Returning a faulty product",
  "lines": [
    {"speaker": "Anna", "text": "...", "translation": "..."},
    ...
  ]
}
```

- `topic`: a short human-readable label for the conversation's theme —
  always requested, regardless of level/exam-type selection.
- `translation`: present only when a translation target language is
  requested (§6); omitted from the prompt/schema otherwise.

`readDialogueArray` is replaced by a parser for this object shape;
`DictationDialogueLine` gains an optional `translation` field; the generator
method returns a small wrapper, e.g. `DialogueGenerationResult(String topic,
List<DictationDialogueLine> lines)`.

## 3. Unify Path B onto the same generator

`generateAiPracticeFromAttempt` drops `dictationAnalyzer
.generatePracticeSentences` and calls the same
`LlmDictationDialogueGenerator` + `synthesizeDialoguePracticeItem` path as
Path A, producing one dialogue/short-passage practice item instead of many
single-sentence ones. No level/exam-type selector exists for this entry
point (it's triggered from an existing history row, not a fresh creation
flow) — it calls the generator with `level=null`, `examType=null` (model
picks freely, same as today's implicit B2 default), but still gets topic
(§2), the audio fix (§1), and translation (§6).

## 4. Level / exam-type selection + random, for Path A

Currently `generateAiPractice` takes no parameters at all (FE calls
`POST /dictation/ai-practice/{userId}/generate` with no body). Add:

- **FE**: a small "Tạo bài luyện" dialog in front of the existing button,
  with two independent selects:
  - **Level**: A1 / A2 / B1 / B2 / C1 / **Ngẫu nhiên**.
  - **Loại đề thi**: options sourced from the existing
    `GET /dictation/facets` (`examTypes`, already used by the Library
    taxonomy) + **Ngẫu nhiên**.
  Each select's "Ngẫu nhiên" is independent — a user can fix the level and
  randomize the exam type, or randomize both.
- **Request**: `POST /dictation/ai-practice/{userId}/generate` body
  `{ "level": "B1" | "RANDOM" | null, "examType": "TOEIC" | "RANDOM" | null,
  "translationLang": "vi" | "en" }` (see §6 for the last field).
- **Resolution happens server-side**: when a field is `"RANDOM"`, english-
  service picks one concrete value (level from the fixed A1-C1 set; exam type
  from the same facet list the FE used, falling back to a small constant set
  if facets are empty) *before* calling the LLM, and returns the resolved
  values in the response so the FE never has to guess what was picked.
- The resolved `level`/`examType` (plus `topic`) feed the LLM prompt as
  explicit instructions (CEFR target, exam-style framing) instead of the
  hard-coded B2 today.

## 5. Persist & display level / exam type / topic

Add columns to `dictation_practice_items` (new Flyway migration):
`level VARCHAR(10)`, `exam_type VARCHAR(40)`, `topic VARCHAR(200)` — mirrors
the existing `dictation_clips` taxonomy shape. Add the same three fields to:

- `DictationPracticeItemDto` (list/history cards) — english-service and bff.
- `DictationPracticeItemDetailDto` (practice-taking page) — english-service
  and bff.

FE: history cards (`AiPracticeSection` in `DictationPage.tsx`) show a badge
with level/examType/topic instead of the current static "Luyện tập AI"
label; the practice-taking page (`DictationAiPracticePage.tsx`) shows the
same in its header.

## 6. Per-sentence translation to the user's UI language

- **Trigger**: FE sends its current i18next language
  (`i18n.resolvedLanguage`, `"en"` or `"vi"`) as `translationLang` on every
  relevant request (AI-practice generate for both paths; Library clip
  detail).
- **Skip rule**: since dictation content is always English, translation is
  only generated when `translationLang !== "en"` — today that means only
  `"vi"` ever produces a translation; `"en"` returns no `translation` field.
- **AI-practice (Path A & B)**: the LLM is asked for a `translation` per
  line in the same call that generates the dialogue (§2's schema). Stored as
  a new `translation_text` column on `dictation_practice_items`, newline-
  joined in the same order as `sentence_text`'s lines, so splitting is
  guaranteed to line up 1:1. `DictationSentenceDto` gains an optional
  `translation` field, populated by zipping the two split texts together in
  `getAiPracticeDetail`.
- **Library**: `dictation_clip_sentences` gains a nullable `translation`
  column. `DictationServiceImpl.getClipDetail` already does one lazy-fill
  step today (AI-aligning un-timestamped sentences) — add a second lazy-fill
  step alongside it: if any sentence for the clip is missing a translation
  and `translationLang == "vi"`, batch-translate the clip's sentences via
  `LlmClient` in one call and persist the results before returning, mirroring
  the existing lazy-alignment pattern rather than requiring a separate
  backfill/admin step.
- **FE rendering**: `SentenceDictationRunner` (shared by Library, Path A, and
  Path B pages) shows the translation as a toggleable hint under each
  sentence, not used for grading.

## Error handling

- If the LLM's JSON object response is missing `topic` or malformed,
  fall back to the existing `fallbackDialogue` template path (already
  present for phrase-based generation) with `topic = null` and no
  translation — never fail the whole generate call for a parse error, same
  as today's error-handling philosophy in this class.
- If translation generation fails (LLM error) mid-request, log and continue
  without translations for that item rather than failing the whole
  generate/detail call — translation is supplementary, not required for the
  practice to function.

## Out of scope

- No change to the Library's audio (still human-recorded, untouched).
- No change to grading logic itself (`DictationScorer`) — translations are
  display-only.
- No backend-persisted user language preference — `translationLang` is
  passed per-request from the FE's local i18n state, since no such profile
  field exists today.
