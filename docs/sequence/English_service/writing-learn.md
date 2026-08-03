# Writing learn: AI-generated writing/translation tasks, criterion-based grading, next-sentence hints

Covers `com.remelearning.english.writing` (`WritingLearnController`/`WritingLearnServiceImpl`), the
fifth "Học & Luyện tập với AI" skill (see `vocabulary-learn.md` for the shared rationale). One domain
serves **three task types** — `COMPOSE` (write English from a Vietnamese brief), `TRANSLATE_VI_EN`,
`TRANSLATE_EN_VI` — because all three share the generate → write → grade flow entirely; the task type
only changes what `promptText` means and which fourth criterion is scored. FE calls go through
`bff-service`'s `LearnerController` (`/api/v1/learners/{userId}/learn/writing/...`), a pure
pass-through, omitted from the diagrams below (same convention as the other skill docs).

**The one structural difference from every other skill: this domain has no weak-point table of its
own.** Every mistake the grader reports already carries its own `category` (`"grammar"` or
`"vocabulary"`), so errors are routed into the existing `grammar_weak_points`/
`vocabulary_weak_points` rows via `PracticeService#redo` → `WeakPointDispatcher`. That is what makes
a "past perfect" slip while writing add to the same label already accumulated from
dictation/listening, instead of starting a parallel tally. The routing itself lives in
`WritingErrorPipeline`, shared with the library tab (section 5) so the two cannot drift apart.

No new Kafka topic, producer or consumer was added for this skill.

## 1. Generate (`POST /api/v1/learn/writing/{userId}/generate`)

```mermaid
sequenceDiagram
    participant Caller
    participant Ctrl as WritingLearnController
    participant Svc as WritingLearnServiceImpl
    participant GWP as GrammarWeakPointService
    participant VWP as VocabularyWeakPointService
    participant Gen as LlmWritingPracticeGenerator
    participant Profile as WritingExamProfile
    participant Ai as AiContentClient (common.ai.LlmClient)
    participant Gemini as Gemini API
    participant WMapper as WritingMapper
    participant DB as reme_english DB

    Caller->>Ctrl: POST /{userId}/generate {taskType, level?, examType?, focusItems?}
    Ctrl->>Svc: generate(userId, request)
    alt focusItems provided
        Svc->>Svc: targetLabels = focusItems
    else no focusItems
        Svc->>GWP: getTopWeakPoints(userId, 8)
        GWP->>DB: SELECT grammar_weak_points ORDER BY forgetting_score DESC
        Svc->>VWP: getTopWeakPoints(userId, 8)
        VWP->>DB: SELECT vocabulary_weak_points ORDER BY forgetting_score DESC
        Svc->>Svc: concat both, distinct, limit 8<br/>(writing is the one skill exercising grammar AND vocabulary at once;<br/>empty list is fine - the generator picks its own topic)
    end
    Svc->>Svc: ExamTypes.normalize(examType) - "toeic"/"TOEIC" collapse to one stored value
    Svc->>Gen: generate(taskType, targetLabels, level, examType)
    Gen->>Profile: WritingExamProfile.fromExamType(examType)<br/>(unknown/blank -> GENERAL, never fails)
    Profile-->>Gen: randomSentenceCount() in that exam's range,<br/>randomTopic(), registerHint(), randomComposeFormat() (COMPOSE only)
    Note over Gen,Profile: These are decided in JAVA and handed to the model as instructions.<br/>Passing only the label "TOEIC" produced passages that were all the same<br/>length and register regardless of the exam.
    Gen->>Ai: completeJson(systemPrompt, userPrompt, temp=0.6, maxTokens=1400)
    Ai->>Gemini: LlmClient.complete(...) -> generateContent REST call
    Gemini-->>Ai: raw text (code-fence stripped)
    Ai-->>Gen: parsed JSON {topic, promptText, referenceAnswer}
    alt LLM call fails, parse fails, or promptText blank
        Gen-->>Svc: AiContentException - no template task, nothing persisted
    end
    Gen-->>Svc: GeneratedWritingPractice{topic, promptText, referenceAnswer}
    Svc->>WMapper: insertItem(WritingPracticeItem{taskType, sourceLang, targetLang, referenceAnswer, targetLabelsJson})
    WMapper->>DB: INSERT writing_practice_items
    Svc-->>Ctrl: WritingPracticeItemDto (NO referenceAnswer field at all)
    Ctrl-->>Caller: ApiResponse<WritingPracticeItemDto>
```

`sourceLang`/`targetLang` are derived from the task type (`WritingTaskType.sourceLang()/targetLang()`),
not sent by the client. `promptText` always begins with a Vietnamese instruction line, per the
project rule that every practice item states its requirement in Vietnamese.

### What the exam style actually decides

`examType` is resolved to a `WritingExamProfile`, and the profile's concrete implications are chosen in
Java before the prompt is built:

| Exam | Source-passage sentences | Subject pool | Register | COMPOSE text format |
|---|---|---|---|---|
| TOEIC | 2–4 | workplace/commercial | practical, businesslike | email, internal notice, complaint reply, short report |
| IELTS | 4–6 | academic/social issues | formal, academic | opinion / discussion / problem-solution essay, Task 1 data description |
| TOEFL | 3–5 | campus/science | conversational-academic | independent essay, reading+lecture summary, email to a professor |
| VSTEP | 3–5 | Vietnam-context everyday | semi-formal | situational letter (Task 1), opinion essay (Task 2) |
| General (also: absent/unknown) | 3–5 | everyday | natural everyday | narrative, description, opinion paragraph |

The sentence count is **drawn fresh per generation** from that range, so ten TOEIC translations produce
ten different lengths rather than ten identically-shaped passages. The situation always comes from the
profile's subject pool — `targetLabels` only decide which structures/words must appear *inside* it, and
a label that doesn't fit is dropped rather than allowed to derail the passage; the sentence count and
register always apply.

**Một tình huống duy nhất từ đầu đến cuối.** The system prompt requires the source passage to be one
continuous text about a single scene (same narrator, place and time frame, sentences chained with
pronouns and connectives, a closing sentence). Without that constraint, N sentences + N target labels
came back as N unrelated example sentences — the passage changed subject on every line — so the
generator now tells the model to fix the scene first and drop a target structure that can't be used
naturally inside it.

An unrecognised exam label is passed through to the model verbatim (so a style the frontend adds first
still reaches it) while length/register fall back to the GENERAL profile — never a failed request.

## 2. Suggest next sentence (`POST /api/v1/learn/writing/suggest`)

One LLM call per press of the "Gợi ý câu tiếp theo" button. There is no debounce, no ghost-text and
no background polling, so a learner who never asks for a hint costs nothing.

```mermaid
sequenceDiagram
    participant Caller
    participant Ctrl as WritingLearnController
    participant Svc as WritingLearnServiceImpl
    participant WMapper as WritingMapper
    participant DB as reme_english DB
    participant Sug as LlmNextSentenceSuggester
    participant Ai as AiContentClient
    participant Gemini as Gemini API

    Caller->>Ctrl: POST /suggest {practiceItemId, draftText?}
    Ctrl->>Svc: suggest(request)
    Svc->>WMapper: findItemById(practiceItemId)
    WMapper->>DB: SELECT writing_practice_items
    Note over Svc,Sug: Only taskType/promptText/draftText/level are passed on.<br/>referenceAnswer is deliberately NOT a parameter of the suggester -<br/>for a translation task, handing it over would make the hint the answer.
    Svc->>Sug: suggest(taskType, promptText, draftText, level)
    alt taskType is TRANSLATE_VI_EN or TRANSLATE_EN_VI
        Sug->>Sug: pick TRANSLATE_SYSTEM_PROMPT - may only name the required<br/>structure and gloss <=2 hard words; never translate a sentence
    else taskType is COMPOSE
        Sug->>Sug: pick COMPOSE_SYSTEM_PROMPT - scaffolding, never a finished sentence
    end
    Sug->>Ai: completeJson(systemPrompt, userPrompt, temp=0.7, maxTokens=900)
    Ai->>Gemini: LlmClient.complete(...)
    Gemini-->>Ai: raw JSON array
    Ai-->>Sug: LlmSuggestion[]
    alt LLM/parse failure
        Sug-->>Svc: AiContentException (502) - an empty list would read as<br/>"the AI has no hints" rather than "the hint call failed"
    end
    Sug-->>Svc: List<WritingSuggestion>{ideaVi, structureHint, usefulPhrases[]}
    Svc-->>Ctrl: same list (suggestions are never recorded as a mistake)
    Ctrl-->>Caller: ApiResponse<List<WritingSuggestion>>
```

## 3. Submit + grade (`POST /api/v1/learn/writing/attempts`)

The core of the feature: this is where a writing mistake becomes a weak point.

```mermaid
sequenceDiagram
    participant Caller
    participant Ctrl as WritingLearnController
    participant Svc as WritingLearnServiceImpl
    participant WMapper as WritingMapper
    participant DB as reme_english DB
    participant Grader as LlmWritingGrader
    participant Ai as AiContentClient
    participant Gemini as Gemini API
    participant Pipe as WritingErrorPipeline
    participant Practice as PracticeService (practice package)

    Caller->>Ctrl: POST /attempts {userId, practiceItemId, submittedText}
    Ctrl->>Svc: submit(request)
    Svc->>WMapper: findItemById(practiceItemId)
    WMapper->>DB: SELECT writing_practice_items
    Svc->>Grader: grade(taskType, promptText, referenceAnswer, submittedText)
    Note over Grader: The grader DOES get the reference answer -<br/>it cannot mark a translation without it.
    Grader->>Ai: completeJson(systemPrompt, userPrompt, temp=0.2, maxTokens=2400)
    Ai->>Gemini: LlmClient.complete(...)
    Gemini-->>Ai: raw JSON {criteria, correctedText, feedbackVi, errors[]}
    Ai-->>Grader: LlmPayload
    Grader->>Grader: clamp each criterion to [0,1]; missing criterion -> 0 + log.warn
    Grader->>Grader: keep only the 4th criterion matching the task type<br/>(accuracy for TRANSLATE_*, taskResponse for COMPOSE)
    Grader->>Grader: drop errors with a blank label or a category<br/>outside grammar/vocabulary (nothing owns them)
    alt LLM/parse failure
        Grader-->>Svc: AiContentException - no neutral 0.5 grade is ever recorded
    end
    Grader-->>Svc: WritingGrade{criteria, correctedText, errors[], feedbackVi}
    Svc->>Pipe: averageCriteria(criteria)
    Note over Svc,Pipe: overallScore is the mean of the POPULATED criteria, computed in Java -<br/>the LLM's own overall figure is ignored, it routinely contradicts<br/>the criteria it just scored.
    Svc->>WMapper: insertAttempt(WritingAttempt{criteriaJson, errorsJson, feedback, overallScore})
    WMapper->>DB: INSERT writing_attempts
    Svc->>Pipe: feedWeakPoints(userId, errors)
    Pipe->>Pipe: per error: prefix = {grammar -> "grammar:", vocabulary -> "vocab:"}<br/>itemId = prefix + label.toLowerCase(); dedupe by (category, label)
    Note over Pipe: The prefixes are NOT the category names - vocabulary's existing rows<br/>are keyed "vocab:". Deriving a prefix from the category would key writing<br/>mistakes under "vocabulary:" and quietly fork the learner's history.
    alt at least one routable error
        Pipe->>Practice: redo(PracticeRedoRequest{userId, attempts[]})
        Note over Practice: see practice-redo.md - updates grammar/vocabulary weak points,<br/>refreshes mistake_history's Leitner schedule (review queue),<br/>and publishes learning.gap.analysis.requested
    else no routable error (e.g. flawless submission)
        Pipe->>Pipe: no call at all - no pointless event
    end
    Svc-->>Ctrl: WritingAttemptResultDto (referenceAnswer revealed only now)
    Ctrl-->>Caller: ApiResponse<WritingAttemptResultDto>
```

### External calls (section 3)

| Target | Call | Failure handling |
|---|---|---|
| Gemini (via `AiContentClient`) | 1 completion per submission | Neutral 0.5 grade, empty errors, Vietnamese notice; attempt is still recorded |
| `reme_english` DB | `SELECT writing_practice_items`, `INSERT writing_attempts` | Transactional (`@Transactional` on `submit`) |
| `practice` package (in-process) | `PracticeService#redo` | Same as the other skills — see [practice-redo.md](practice-redo.md) |

## 4. History, detail and retry

`GET /api/v1/learn/writing/history/{userId}` and `.../history/{userId}/{attemptId}` read straight off
`writing_attempts`; the detail endpoint deserializes the stored `criteria`/`errors` JSON rather than
re-grading, so opening history costs no LLM call and always shows exactly what the learner saw. A
mismatched `userId` is a 404, not another learner's row.

```mermaid
sequenceDiagram
    participant Caller
    participant Ctrl as WritingLearnController
    participant Svc as WritingLearnServiceImpl
    participant WMapper as WritingMapper
    participant DB as reme_english DB
    participant Analyzer as WritingMistakeAnalyzer (static, pure)
    participant Gen as LlmWritingPracticeGenerator

    Caller->>Ctrl: POST /history/{userId}/{attemptId}/ai-practice
    Ctrl->>Svc: generatePracticeFromAttempt(userId, attemptId)
    Svc->>WMapper: findAttemptDetailByIdAndUserId(attemptId, userId)
    WMapper->>DB: SELECT writing_attempts JOIN writing_practice_items
    alt not found / belongs to another learner
        Svc-->>Ctrl: BusinessException.notFound -> 404
    end
    Svc->>Analyzer: extractMistakeLabels(errorsJson)
    Analyzer-->>Svc: distinct labels in reported order<br/>(malformed/empty JSON -> empty list, never throws)
    Svc->>Svc: examType param present ? normalize(it) : attempt's own examType
    Svc->>Gen: generate(sameTaskType, mistakeLabels, sameLevel, resolvedExamType)
    Note over Svc,Gen: reuses the exact generate-and-persist pipeline from section 1,<br/>so the retry task lands in the same writing_practice_items bank.<br/>The examType override lets the learner re-target the same mistakes<br/>at a different exam; omitting it keeps the original.
    Svc-->>Ctrl: refreshed List<WritingPracticeItemDto>
```

## 5. Library tab

See [writing-library.md](writing-library.md) — the fixed catalogue side, notable for being the only
library with three independent taxonomy axes. It grades through the very same `WritingGrader` and
`WritingErrorPipeline` used above.
