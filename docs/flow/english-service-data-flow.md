# english-service — Data Flow

Focuses on **what happens to the data** (transformations, formats, storage) as it moves through
`english-service`'s three analysis domains — `vocabulary`, `grammar`, `pronunciation` — plus the
cross-cutting `practice` (redo-exercise) and `dictation` (listen-and-type practice) packages, as
opposed to the sequence diagrams in [../sequence/English_service/](../sequence/English_service/)
which focus on call order between components. Only `vocabulary` ingests `transcript.ready`; `grammar`
and `pronunciation` each run their own weak-point ingestion off the same `learning.gap.analyzed`
event, filtered to their own `category`, on their own Kafka `groupId`. `practice` also consumes
`learning.gap.analyzed` (no category filter, to seed `mistake_history`) and is the first component in
`english-service` to *produce* a Kafka event, `learning.gap.analysis.requested`, once a learner redoes
an exercise. `dictation` is pull-based, not event-driven: it reads `vocabulary`/`grammar`'s weak-point
tables in-process to pick sentences, calls out to an LLM and Google Cloud TTS, and stores generated
audio in S3/MinIO.

**"Học & Luyện tập với AI" — four new "learn" skill packages.** `vocabulary/learn`, `grammar/learn`
(each nested under their existing domain package), plus two brand-new top-level domains,
`listening` and `speaking`, add an AI-generate-then-grade loop per skill: generate an AI practice
item (Gemini text, and for `listening`/`speaking` also Supertonic TTS audio via shared helpers in
`learn/common/`), let the learner submit an attempt, grade it with a pure in-process scorer (or, for
`speaking`, ai-service's wav2vec2 GOP model), and — instead of building a fourth weak-point pipeline —
feed every graded item straight into the **existing** `PracticeService.redo(...)` call used by
manual redo exercises (`mistake_history`/`WeakPointScoringEngine`/`learning.gap.analysis.requested`,
see `PracticeFlow` below). `vocabulary`/`grammar` route into their own pre-existing weak-point tables
this way; `speaking` reuses `pronunciation_weak_points` (category `"pronunciation"`); `listening`
introduces a brand-new category, `"listening"` (`LearningCategories.LISTENING`), that has **no**
matching weak-point table — see the `ListeningLearnFlow`/`SpeakingLearnFlow`/`VocabLearnFlow`/
`GrammarLearnFlow` subgraphs and the note below the diagram.

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
flowchart TD
    subgraph Input["Input (from ai-service via Kafka)"]
        TREvent["transcript.ready event<br/>{recording_id, user_id, full_text, segments[]}"]
        LGAEvent["learning.gap.analyzed event<br/>{recording_id, user_id, weak_points[]}"]
    end

    subgraph TranscriptFlow["Transcript ingestion"]
        Decode1["EventCodec decode<br/>snake_case JSON -> TranscriptReadyEvent"]
        Idem1{"findByRecordingId<br/>already exists?"}
        Skip1["skip (at-least-once redelivery)"]
        Insert1["insertTranscript + insertSegment per item<br/>(segmentOrder = incrementing index)"]
    end

    subgraph WeakPointFlow["Weak point ingestion (one independent consumer per domain, same topic)"]
        Decode2V["EventCodec decode (vocabulary)<br/>snake_case JSON -> LearningGapAnalyzedEvent"]
        FilterV{"category == vocabulary?"}
        DiscardV["skip<br/>(owned by grammar's/pronunciation's own consumer)"]
        ClassifyV["VocabularyClassifier.classify(label)<br/>rule-based heuristics, or LLM (Gemini) with<br/>fallback to OTHER on failure"]
        UpsertV["upsert keyed on (user_id, item_id)"]

        Decode2G["EventCodec decode (grammar)<br/>snake_case JSON -> LearningGapAnalyzedEvent"]
        FilterG{"category == grammar?"}
        DiscardG["skip<br/>(owned by vocabulary's/pronunciation's own consumer)"]
        ClassifyG["GrammarClassifier.classify(label)<br/>rule-based heuristics, or LLM (Gemini) with<br/>fallback to OTHER on failure"]
        UpsertG["upsert keyed on (user_id, item_id)"]

        Decode2P["EventCodec decode (pronunciation)<br/>snake_case JSON -> LearningGapAnalyzedEvent"]
        FilterP{"category == pronunciation?"}
        DiscardP["skip<br/>(owned by vocabulary's/grammar's own consumer)"]
        ClassifyP["PronunciationClassifier.classify(label)<br/>rule-based heuristics, or LLM (Gemini) with<br/>fallback to OTHER on failure"]
        UpsertP["upsert keyed on (user_id, item_id)"]
    end

    subgraph PracticeFlow["Practice / redo-exercise (package practice)"]
        Decode2Pr["EventCodec decode (practice seed)<br/>snake_case JSON -> LearningGapAnalyzedEvent"]
        SeedPr["seedIfAbsent per weak point<br/>(no category filter; ON CONFLICT (user_id, item_id) DO NOTHING)"]

        RedoReq["POST /api/v1/practice/redo<br/>{userId, attempts: [{itemId, category, label, correct}]}"]
        LogAttempt["insert practice_attempts (audit log)<br/>per attempt"]
        LockPrior["findOneForUpdate (SELECT ... FOR UPDATE)<br/>read scoring state BEFORE this attempt"]
        RecordAttempt["recordAttempt per attempt<br/>occurrence_count += (correct ? 0 : 1), last_seen_at = now()"]
        ScoreEngine["common.scoring.WeakPointScoringEngine<br/>forgetting (adaptive half-life) x (1-mastery, BKT)<br/>x difficultyWeight (Rasch) x recurrenceBoost"]
        UpdateState["updateScoringState + item_difficulty_stats upsertIncrement"]
        DispatchDomain["dispatch to owning domain's<br/>applyJavaComputedScore (scoreSource=JAVA_ENGINE)"]
        BuildHistory["findByUserId -> build AnalysisRequestedEvent<br/>{recordingId: practice-&lt;uuid&gt;, userId, segments: [], history[]}"]
        PublishAR["AnalysisRequestedProducer.publish<br/>-> learning.gap.analysis.requested"]
        RevQueue["GET /api/v1/practice/review-queue/{userId}<br/>findDueForReview(userId, now)"]
    end

    subgraph DictationFlow["Dictation (package dictation, pull-based; grading feeds the Kafka pipeline)"]
        ImportLib["DictationLibraryImporter (startup)<br/>StorageClient.list/read -> upsertClip per real audio clip<br/>taxonomy: skill/level(CEFR)/topic/examType from folder+filename<br/>+ folder (direct parent dir, rev 2) + split scriptText into sentences (rev 2)"]
        SessionReq["POST /api/v1/dictation/sessions/{userId}<br/>{skill?, level?, topic?, examType?, count}"]
        PickClips["findRandomClipsByFacets -> List[DictationClipDto] (no script)"]
        StreamAudio["GET /clips/{id}/audio -> StorageClient.read stream (mp3/wav)"]

        ListFolders["GET /dictation/folders (rev 2)<br/>findDistinctFolders -> List[DictationFolderDto]{folderId, name, lessonCount}"]
        ListFolderLessons["GET /dictation/folders/{folderId}/lessons/{userId} (rev 3)<br/>findLessonSummariesByFolder (joins per-user attempt agg) -> List[DictationLessonSummaryDto] (no script)"]
        GetClipDetail["GET /dictation/clips/{clipId} (rev 2)<br/>findClipById + findSentencesByClipId -> DictationClipDetailDto{scriptText, sentences[]}"]
        AlignSentences["ensureSentencesAligned (lazy, only if a sentence is missing startMs/endMs)<br/>StorageClient.read(audio) -> SentenceAlignmentClient.align -> ai-service POST /api/v1/dictation/align-sentences<br/>-> updateSentenceTimestamps per matched sentence; failures/no-match just leave nulls"]

        AttemptReq["POST /api/v1/dictation/attempts<br/>{userId, clipId? | practiceItemId?, userTranscript, sentenceMistakes?}"]
        ScoreDictation["DictationScorer.score(referenceText, userTranscript)<br/>word-level Levenshtein -> WER<br/>(rev 2: FE grades per-sentence client-side first, then reassembles the full transcript here)"]
        ScoreSentenceMistakes["rev 3: DictationScorer.score(expectedText, attemptedText) per sentenceMistakes[] entry<br/>-> extra missing/substituted words merged into the same miss list"]
        InsertAttempt["insertAttempt + insertMisses (missing/substituted words from userTranscript's diff, plus any from ScoreSentenceMistakes)"]
        Analyze["DictationAnalyzer.analyzeAttempt(referenceText, userTranscript, diff)<br/>rule-based heuristic, or Gemini -> root-cause-classified errorTable (LEXICON/GRAMMAR/PHONOLOGY) + rootCauses + actionAdvice + practice sentences"]
        PublishGap["DictationGapEventPublisher -> learning.gap.analyzed<br/>toLearningCategory: LEXICON-&gt;vocabulary, GRAMMAR-&gt;grammar, PHONOLOGY-&gt;pronunciation<br/>(unclassified word, e.g. a sentence-mode retry miss -> vocabulary)"]

        AiGen["POST /ai-practice/{userId}/generate<br/>{level?, examType?, translationLang?} -> resolveLevel/resolveExamType (concrete value, RANDOM from a fixed CEFR pool<br/>A1/A2/B1/B2/C1 or the library's own distinct exam types w/ TOEIC/IELTS/TOEFL/General fallback, or unset)<br/>-> pending items' text (or top missed words if none pending) + resolved level/examType/translationLang -> LlmDictationDialogueGenerator (Gemini)<br/>-> one monologue/multi-speaker dialogue with a topic label + parallel per-line translation (only if translationLang != en)<br/>-> random voice per speaker -> Supertonic (ai-service) per line, synthesized from the SAME text persisted as the graded sentence<br/>-> WavAudioMerger -> one merged file -> StorageClient.write -> replaces prior pending items"]
        AiGenFromAttempt["POST /dictation/history/{userId}/{attemptId}/ai-practice<br/>{translationLang?} -> one attempt's own missed words -> same LlmDictationDialogueGenerator as AiGen (level/examType left unset)<br/>-> Supertonic (ai-service) -> StorageClient.write"]
        GetAiPracticeDetail["GET /dictation/ai-practice/items/{practiceItemId}/detail<br/>findPracticeItemById + splitIntoSentences(sentenceText, translationText) (zips translation per sentence)<br/>-> DictationPracticeItemDetailDto{scriptText, level, examType, topic, sentences[]} (startMs/endMs always null, one merged audio file)"]
    end

    subgraph VocabLearnFlow["Vocabulary learn (package vocabulary.learn)"]
        VLGenReq["POST /api/v1/learn/vocabulary/{userId}/generate<br/>{level?, examType?, focusItems?}"]
        VLResolveWords["resolveTargetWords: focusItems, else learner's own<br/>top vocabularyWeakPointService weak points (limit 8)"]
        VLGenerate["VocabPracticeGenerator.generate(words, level, examType)<br/>LLM (Gemini) -> GeneratedVocabPractice{topic, items[]}<br/>each item: CLOZE / MCQ / MATCHING"]
        VLInsertItem["insertItem"]
        VLSubmitReq["POST /api/v1/learn/vocabulary/attempts<br/>{userId, practiceItemId, answers[]}"]
        VLScore["VocabAttemptScorer.score(questions, answers)<br/>pure per-question exact-match -> {accuracy, perQuestionCorrect[]}"]
        VLInsertAttempt["insertAttempt"]
        VLFeed["feedWeakPoints: one PracticeAttemptRequest per distinct target word<br/>itemId=vocab:&lt;word&gt;, category=vocabulary"]
    end

    subgraph GrammarLearnFlow["Grammar learn (package grammar.learn) - structural clone of VocabLearnFlow"]
        GLGenReq["POST /api/v1/learn/grammar/{userId}/generate<br/>{level?, examType?, focusItems?}"]
        GLResolveRules["resolveTargetRules: focusItems, else learner's own<br/>top grammarWeakPointService weak points (limit 8)"]
        GLGenerate["GrammarPracticeGenerator.generate(rules, level, examType)<br/>LLM (Gemini) -> GeneratedGrammarPractice{topic, items[]}<br/>each item: ERROR_CORRECTION / FILL_TENSE / TRANSFORM / MCQ"]
        GLInsertItem["insertItem"]
        GLSubmitReq["POST /api/v1/learn/grammar/attempts<br/>{userId, practiceItemId, answers[]}"]
        GLScore["GrammarAttemptScorer.score(questions, answers)<br/>pure per-question exact-match -> {accuracy, perQuestionCorrect[]}"]
        GLInsertAttempt["insertAttempt"]
        GLFeed["feedWeakPoints: one PracticeAttemptRequest per distinct target rule<br/>itemId=grammar:&lt;rule&gt;, category=grammar"]

        GLHistoryAttemptReq["POST /api/v1/learn/grammar/history/{userId}/{attemptId}/ai-practice"]
        GLReadAttempt["findAttemptDetailByIdAndUserId(attemptId, userId)<br/>404 if not found / not owned by userId"]
        GLAnalyzeMissed["GrammarMistakeAnalyzer.extractMissedRules(itemsJson, answersJson)<br/>re-scores via GrammarAttemptScorer -> distinct targetRule[] of every wrong question"]
        GLListRefreshed["findItemsByUserId(userId) -> refreshed practice-set list"]
    end

    subgraph ListeningLearnFlow["Listening learn (package listening, brand-new domain)"]
        LLGenReq["POST /api/v1/learn/listening/{userId}/generate<br/>{level?, examType?, translationLang?, focusItems?}"]
        LLResolveKw["resolveTargetKeywords: focusItems, else the learner's own past<br/>wrong KEYWORD answers (own attempt history - no weak-point table)"]
        LLGenerate["ListeningPracticeGenerator.generate(keywords, level, examType, translationLang)<br/>LLM (Gemini) -> transcript + MCQ/KEYWORD/OPEN questions + optional translation"]
        LLSynthesize["DialogueAudioSynthesizer.synthesize(lines, ttsLang)<br/>Supertonic TTS per line, merged -> {transcriptText, translationText, audioBytes}"]
        LLInsertItem["insertItem + StorageClient.write(audio) -> storageKey"]
        LLSubmitReq["POST /api/v1/learn/listening/attempts<br/>{userId, practiceItemId, answers[]}"]
        LLScoreClosed["ListeningQuestionScoring.scoreClosed<br/>MCQ exact-match, or KEYWORD WER (like DictationScorer)"]
        LLScoreOpen["OpenAnswerGrader.grade(transcript, prompt, modelAnswer, submitted)<br/>LLM (Gemini) -> {score 0..1, feedback}"]
        LLInsertAttempt["insertAttempt (answersJson + resultsJson + score)"]
        LLFeed["feedWeakPoints: one PracticeAttemptRequest per distinct label<br/>(KEYWORD answer, or MCQ/OPEN's skill) - itemId=listening:&lt;label&gt;, category=listening"]

        LLHistoryAttemptReq["POST /api/v1/learn/listening/history/{userId}/{attemptId}/ai-practice"]
        LLReadAttempt["findAttemptDetailByIdAndUserId(attemptId, userId)<br/>404 if not found / not owned by userId"]
        LLAnalyzeMissed["ListeningMistakeAnalyzer.extractMissedTopics(resultsJson, attempt.topic)<br/>-> distinct retry-target text[] of every wrong question<br/>(correctAnswer for KEYWORD/MCQ; attempt's topic name for OPEN,<br/>since an OPEN correctAnswer is a full model-answer sentence, too<br/>diffuse a target keyword - product decision)"]
        LLListRefreshed["findItemsByUserId(userId) -> refreshed practice-set list"]
    end

    subgraph SpeakingLearnFlow["Speaking learn (package speaking, brand-new domain)"]
        SLGenReq["POST /api/v1/learn/speaking/{userId}/generate<br/>{level?, examType?, focusItems?}"]
        SLResolveWords["resolveTargetWords: focusItems, else learner's own<br/>top pronunciationWeakPointService weak points (limit 8)"]
        SLGenerate["SpeakingPracticeGenerator.generate(words, level, examType)<br/>LLM (Gemini) -> {topic, targetText, translation}"]
        SLSynthesizeSample["TtsClient.synthesize(targetText, 1 voice)<br/>Supertonic -> sample audio -> StorageClient.write -> storageKey"]
        SLSubmitReq["POST /api/v1/learn/speaking/{userId}/attempts (multipart)<br/>{practiceItemId, audio}"]
        SLStoreAudio["StorageClient.write(learner audio)<br/>speaking/attempts/&lt;userId&gt;/&lt;uuid&gt;.wav"]
        SLScore["PronunciationScoringClient.score(audio, targetText, lang)<br/>-&gt; ai-service GOP endpoint (see note below)<br/>-&gt; {overall, words[{word, score, phonemes[{ipa, score}]}], transcript, weakPhonemes[]}"]
        SLInsertAttempt["insertAttempt (overallScore + wordScoresJson + transcript + weakPhonemesJson)"]
        SLFeed["feedWeakPoints: one PracticeAttemptRequest per distinct word<br/>(score &gt;= 0.6 = correct) - itemId=pronunciation:&lt;word&gt;, category=pronunciation"]

        SLHistoryAttemptReq["POST /api/v1/learn/speaking/history/{userId}/{attemptId}/ai-practice"]
        SLReadAttempt["findAttemptDetailByIdAndUserId(attemptId, userId)<br/>404 if not found / not owned by userId"]
        SLAnalyzeMissed["SpeakingMistakeAnalyzer.extractWeakPhonemes(weakPhonemesJson)<br/>-> distinct IPA symbols[] (already a flat, crisp retry target -<br/>no OPEN-vs-KEYWORD diffing/fallback needed like listening)"]
        SLListRefreshed["findItemsByUserId(userId) -> refreshed practice-set list"]
    end

    subgraph VocabularyLibraryFlow["Vocabulary library (package vocabulary.library) - extends VocabLearnFlow"]
        LibStartReq["POST /.../library/{userId}/topics/{topicId}/sections<br/>{sectionSize?}"]
        LibCountCheck{"library word count for topic &lt; sectionSize?"}
        LibGenerate["LlmLibraryWordGenerator.generate(topic, existingWords, 15)<br/>LLM (Gemini) -> GeneratedLibraryWord[]{word, wordType, meaningVi, exampleEn}<br/>(empty list, not a template, on call/parse failure)"]
        LibInsertWord["insert per generated word"]
        LibTts["TtsClient.synthesize(word, lang=en) -> wav bytes<br/>(once per new word, never at Section-runtime)"]
        LibStorageWrite["StorageClient.write(vocab-library/{topicId}/{wordId}.wav)<br/>-> updateAudioStorageKey(storageKey)"]
        LibPickWords["findNotYetMasteredByTopicId (+ findRandomByTopicIdExcluding fallback)"]
        LibQueueInit["SectionQueue.initial(wordIds)<br/>-> SectionQueueEntry[]{wordId, streak=0, introShown=false} (shuffled)"]
        LibInsertSection["insertAttempt -> {status=IN_PROGRESS, libraryWordIdsJson, queueStateJson}"]

        LibAnswerReq["POST /.../library/sections/{sectionId}/answers<br/>{submittedAnswer?}"]
        LibScore["SectionAnswerScoring.scoreClosed (WER/exact-match),<br/>or OpenAnswerGrader.grade (LLM, TRANSLATE_EN_TO_VI only)"]
        LibInsertAnswer["insertAnswer"]
        LibApplyQueue["SectionQueue.applyResult(queue, correct)<br/>-> updated SectionQueueEntry[] (streak++/reset, requeue +6/+2, drop if streak==2)"]
        LibUpdateQueueState["updateAttemptQueueState -> updated queue_state JSON"]
        LibComplete["completeAttempt('COMPLETED' | 'ABANDONED' if finished early) when queue empty"]
        LibFeed["feedWeakPoints: one PracticeAttemptRequest per<br/>vocabulary_section_answers row (NOT deduped by word)"]
    end

    subgraph GrammarLibraryFlow["Grammar library (package grammar.library) - 60-topic catalog + AI theory page/pool, generated once"]
        GLibContentReq["GET /.../library/topics/{topicId}"]
        GLibContentCheck{"grammar_library_contents row exists for topic?"}
        GLibGenerate["LlmGrammarLibraryContentGenerator.generateTopicContent(topic.name, topic.level)<br/>LLM (Gemini) -> {explanationEn, explanationVi, illustrationText, examples[], questions[8-10]}<br/>(static-template fallback, not empty, on call/parse failure)"]
        GLibInsertContent["insert grammar_library_contents row"]
        GLibInsertQuestions["insert grammar_library_questions row per generated question"]

        GLibSessionReq["POST /.../library/{userId}/topics/{topicId}/sessions"]
        GLibLockCheck{"grammar_topic_progress.status == LOCKED (or no row)?"}
        GLibSnapshot["build List&lt;GrammarLibrarySessionQuestion&gt;<br/>full content snapshot per pool question, questionRef = q-&lt;id&gt;"]
        GLibInsertSession["insert grammar_library_sessions row<br/>{sessionType=INITIAL, questionsJson, status=IN_PROGRESS}"]
        GLibMarkInProgress["grammar_topic_progress -> IN_PROGRESS"]

        GLibAnswerReq["POST /.../sessions/{sessionId}/answers<br/>{questionRef, submittedAnswer?}"]
        GLibScore["ExactMatchScorer.score([answer], [submitted], stripTrailingPunctuation=true)<br/>(shared with grammar.learn)"]
        GLibInsertAnswer["insert grammar_library_session_answers row"]

        GLibFinishReq["POST /.../sessions/{sessionId}/finish"]
        GLibRecompute["recompute correctness per question<br/>(latest answer per questionRef; unanswered = wrong)"]
        GLibCompleteSession["update grammar_library_sessions<br/>{status=COMPLETED, correct_count, total_count}"]
        GLibFeed["one PracticeAttemptRequest per question in the session<br/>itemId=grammar:&lt;topicCode&gt;, category=grammar (NOT deduped)"]
        GLibAllCorrect{"every question correct?"}
        GLibMarkPassed["grammar_topic_progress -> PASSED, passed_at=now()"]
        GLibUnlockNext["grammar_topic_progress (next sequence_order) -> UNLOCKED<br/>(insert-or-flip-if-LOCKED, never regresses UNLOCKED/IN_PROGRESS/PASSED)"]
        GLibRetryGen["per wrong question:<br/>LlmGrammarLibraryContentGenerator.generateRetryQuestion(topic.name, topic.level, type, oldPrompt)<br/>LLM (Gemini) -> one fresh question, same type, not repeating oldPrompt"]
        GLibRetrySession["insert grammar_library_sessions row<br/>{sessionType=RETRY, questionsJson=retryQuestions (inline, never in the pool table), status=IN_PROGRESS}"]

        GLibHistorySessionReq["POST /.../library/{userId}/sessions/{sessionId}/ai-practice"]
        GLibOwnerCheck{"session found &amp; session.userId == userId?"}
        GLibAnalyzeMissed["GrammarMistakeAnalyzer.hasAnyMissedQuestion(questionsJson, answers)<br/>-> boolean (library questions carry no explicit rule tag - a session is<br/>already scoped to one topic, so there is nothing per-question to diff out)"]
        GLibHasMistakes{"any question missed?"}
        GLibNoRegen["return empty list (nothing to regenerate)"]
        GLibTopicLookup["GrammarLibraryTopicMapper.findById(topicId) -> topic.name, topic.level"]
        GLibDelegate["GrammarLearnService.generatePracticeForRules(userId, [topic.name], topic.level, examType=null)<br/>delegates to grammar.learn's own generate-and-persist pipeline (GLGenerate/GLInsertItem/GLListRefreshed)<br/>so both flows feed the same grammar_practice_items bank"]
    end

    subgraph ListeningLibraryFlow["Listening library (package listening.library) - fixed topic catalog + AI Section (passage+audio), generated once"]
        LLibSectionReq["POST /.../library/{userId}/topics/{topicId}/sections"]
        LLibLockCheck{"listening_topic_progress.status == LOCKED (or no row)?"}
        LLibSectionCheck{"listening_library_sections row exists for topic?"}
        LLibGenerate["LlmListeningLibraryGenerator.generateSection(topic)<br/>LLM (Gemini) -> {passage, questions[4]}<br/>(no static-template fallback - AiContentException propagates, no Section persisted, on call/parse failure)"]
        LLibSynthesize["DialogueAudioSynthesizer.synthesize([Narrator: passage], ttsLang)<br/>Supertonic -> audio bytes"]
        LLibStorageWrite["StorageClient.write(listening-library/{topicId}/{uuid}.wav, audioBytes)"]
        LLibInsertSection["insert listening_library_sections row {topicId, passageText, audioStorageKey}"]
        LLibInsertQuestions["insert listening_library_questions row per generated question"]
        LLibMarkInProgress["listening_topic_progress -> IN_PROGRESS"]

        LLibAnswerReq["POST /.../{userId}/sections/{sectionId}/answers {answers[]}"]
        LLibScore["score each submitted answer against correctOption per questionId<br/>-> {score = correctCount/total, correctCount, totalQuestions}"]
        LLibInsertAttempt["insert listening_library_attempts row"]
        LLibPassCheck{"score >= 0.7 (PASS_THRESHOLD)?"}
        LLibMarkPassed["listening_topic_progress -> PASSED, passed_at=now()"]
        LLibUnlockNext["listening_topic_progress (next sequence_order) -> UNLOCKED<br/>(insert-or-flip-if-LOCKED, never regresses UNLOCKED/IN_PROGRESS/PASSED)"]

        LLibHistorySectionReq["POST /.../library/{userId}/sections/{sectionId}/ai-practice"]
        LLibFindLatestAttempt["findByUserId(userId), filter to sectionId, keep latest by completedAt<br/>(no dedicated 'attempt for this section' query)"]
        LLibHasAttempt{"learner has a completed attempt on this section?"}
        LLibNoAttempt["return empty list (nothing to regenerate)"]
        LLibAnalyzeMissed["ListeningMistakeAnalyzer.hasAnyMissedQuestion(answers)<br/>-> boolean (a Section is scoped to one topic already and its questions<br/>carry no per-question topic tag of their own)"]
        LLibHasMistakes{"any question missed?"}
        LLibNoRegen["return empty list (nothing to regenerate)"]
        LLibTopicLookup["ListeningLibraryTopicMapper.findById(topicId) -> topic.name, topic.level"]
        LLibDelegate["ListeningLearnService.generatePracticeForKeywords(userId, [topic.name], topic.level, examType=null)<br/>delegates to listening.learn's own generate-and-persist pipeline (LLGenerate/LLSynthesize/LLInsertItem/LLListRefreshed)<br/>so both flows feed the same listening_practice_items bank"]
    end

    subgraph SpeakingLibraryFlow["Speaking library (package speaking.library) - fixed topic catalog + AI Section (sample sentences + per-sentence audio), generated once"]
        SLibSectionReq["POST /.../library/{userId}/topics/{topicId}/sections"]
        SLibLockCheck{"speaking_topic_progress.status == LOCKED (or no row)?"}
        SLibSectionCheck{"speaking_library_sections row exists for topic?"}
        SLibGenerate["LlmSpeakingLibraryGenerator.generateSection(topic)<br/>LLM (Gemini) -> {sentences: [{text, ipa}] (5 items)}<br/>(no static-template fallback - AiContentException propagates, no Section persisted, on call/parse failure)"]
        SLibInsertSection["insert speaking_library_sections row {topicId} (no content columns)"]
        SLibSynthesizeLoop["per generated sentence:<br/>DialogueAudioSynthesizer.synthesize([Narrator: sentenceText], ttsLang) -> audio bytes<br/>StorageClient.write(speaking-library/{topicId}/{uuid}.wav, audioBytes)<br/>insert speaking_library_sentences row {sectionId, sentenceText, ipa, sampleAudioStorageKey}"]
        SLibMarkInProgress["speaking_topic_progress -> IN_PROGRESS"]

        SLibAttemptReq["POST /.../{userId}/sections/{sectionId}/sentences/{sentenceId}/attempts (multipart audio)"]
        SLibStorageWrite["StorageClient.write(speaking-library/attempts/{userId}/{uuid}.wav, audioBytes)"]
        SLibScore["PronunciationScoringClient.score(audio, sentenceText, ttsLang)<br/>(same GOP call speaking.learn already makes)<br/>-> wordScore = avg(words[].score), phonemeScore = avg(words[].phonemes[].score)"]
        SLibInsertAttempt["insert speaking_library_attempts row {sectionId, sentenceId, phonemeScore, wordScore, recordedAudioStorageKey, weakPhonemesJson=score.weakPhonemes()}<br/>(does NOT touch speaking_topic_progress)"]

        SLibFinishReq["POST /.../{userId}/sections/{sectionId}/finish"]
        SLibPassCheck{"every sentence in the section has >=1 attempt BY THIS USER<br/>with phonemeScore >= 0.7 AND wordScore >= 0.7 (PASS_THRESHOLD) -<br/>findBySectionIdAndUserId(sectionId, userId), not findBySectionId<br/>(bugfix: an unscoped query let another learner's passing attempt<br/>on a shared section count toward this learner's pass/unlock)"}
        SLibMarkPassed["speaking_topic_progress -> PASSED, passed_at=now()"]
        SLibUnlockNext["speaking_topic_progress (next sequence_order) -> UNLOCKED<br/>(insert-or-flip-if-LOCKED, never regresses UNLOCKED/IN_PROGRESS/PASSED)"]

        SLibHistorySectionReq["POST /.../library/{userId}/sections/{sectionId}/ai-practice"]
        SLibFindOwnAttempts["findBySectionIdAndUserId(sectionId, userId)<br/>(a section is a shared catalog object attemptable by any learner -<br/>unlike listening's single-attempt-per-section, speaking scores per-sentence,<br/>so any sentence/any retry by this learner all count; scoped at the<br/>mapper level so another learner's rows never cross the wire)"]
        SLibAnalyzeMissed["union: every filtered attempt's<br/>SpeakingMistakeAnalyzer.extractWeakPhonemes(weakPhonemesJson)<br/>-> distinct IPA symbols[] (not just the latest attempt, unlike listening;<br/>no topic-name fallback needed - phonemes are already a crisp target)"]
        SLibHasMistakes{"any mispronounced phoneme across any attempt?"}
        SLibNoRegen["return empty list (nothing to regenerate)"]
        SLibTopicLookup["SpeakingLibraryTopicMapper.findById(topicId) -> topic.level"]
        SLibDelegate["SpeakingLearnService.generatePracticeForKeywords(userId, weakPhonemes, topic.level, examType=null)<br/>delegates to speaking.learn's own generate-and-persist pipeline (SLGenerate/SLSynthesizeSample/SLListRefreshed)<br/>so both flows feed the same speaking_practice_items bank"]
    end

    subgraph Storage["reme_english DB"]
        T1[("transcripts")]
        T2[("transcript_segments")]
        T3[("vocabulary_weak_points")]
        T4[("grammar_weak_points")]
        T5[("pronunciation_weak_points")]
        T6[("mistake_history")]
        T7[("practice_attempts")]
        T8[("item_difficulty_stats")]
        T9[("dictation_clips")]
        T10[("dictation_misses")]
        T11[("dictation_attempts")]
        T12[("dictation_practice_items")]
        T13[("dictation_clip_sentences")]
        T14[("vocab_practice_items")]
        T15[("vocab_practice_attempts")]
        T16[("grammar_practice_items")]
        T17[("grammar_practice_attempts")]
        T18[("listening_practice_items")]
        T19[("listening_attempts")]
        T20[("speaking_practice_items")]
        T21[("speaking_attempts")]
        T22[("vocabulary_topics")]
        T23[("vocabulary_library_words")]
        T24[("vocabulary_section_attempts")]
        T25[("vocabulary_section_answers")]
        T26[("grammar_library_topics")]
        T27[("grammar_library_contents")]
        T28[("grammar_library_questions")]
        T29[("grammar_topic_progress")]
        T30[("grammar_library_sessions")]
        T31[("grammar_library_session_answers")]
        T32[("listening_library_topics")]
        T33[("listening_library_sections")]
        T34[("listening_library_questions")]
        T35[("listening_topic_progress")]
        T36[("listening_library_attempts")]
        T37[("speaking_library_topics")]
        T38[("speaking_library_sections")]
        T39[("speaking_library_sentences")]
        T40[("speaking_topic_progress")]
        T41[("speaking_library_attempts")]
        T42[("listening_library_attempt_answers")]
    end

    subgraph ReadOut["Read-out (REST)"]
        GetTranscript["GET /api/v1/transcripts/{recordingId}<br/>-> TranscriptResponse{fullText, segments[]}"]
        GetWeakV["GET /api/v1/vocabulary/weak-points/{userId}[/grouped]<br/>-> List or Map[VocabularyType, List]"]
        GetWeakG["GET /api/v1/grammar/weak-points/{userId}[/grouped]<br/>-> List or Map[GrammarType, List]"]
        GetWeakP["GET /api/v1/pronunciation/weak-points/{userId}[/grouped]<br/>-> List or Map[PronunciationType, List]"]
        GetDictationHistory["GET /api/v1/dictation/history/{userId}<br/>-> List[DictationHistoryEntryDto]{attemptId, clipId, title, skill,<br/>level, examType, accuracy, wer, attemptedAt, attemptCount, practiceType}"]
        GetAttemptDetail["GET /api/v1/dictation/history/{userId}/{attemptId}<br/>findAttemptDetailByIdAndUserId + findMissesByAttemptId<br/>-> DictationAttemptDetailDto{referenceText, userTranscript, mistakes[], errorTable[], rootCauses[], actionAdvice[]}"]
    end

    subgraph Output["Output (to ai-service via Kafka)"]
        AREvent["learning.gap.analysis.requested event<br/>{recording_id, user_id, segments: [], history[]}"]
    end

    TREvent --> Decode1 --> Idem1
    Idem1 -->|yes| Skip1
    Idem1 -->|no| Insert1
    Insert1 --> T1
    Insert1 --> T2

    LGAEvent --> Decode2V --> FilterV
    FilterV -->|no| DiscardV
    FilterV -->|yes| ClassifyV
    ClassifyV --> UpsertV
    UpsertV --> T3

    LGAEvent --> Decode2G --> FilterG
    FilterG -->|no| DiscardG
    FilterG -->|yes| ClassifyG
    ClassifyG --> UpsertG
    UpsertG --> T4

    LGAEvent --> Decode2P --> FilterP
    FilterP -->|no| DiscardP
    FilterP -->|yes| ClassifyP
    ClassifyP --> UpsertP
    UpsertP --> T5

    LGAEvent --> Decode2Pr --> SeedPr --> T6

    RedoReq --> LogAttempt --> T7
    RedoReq --> LockPrior --> T6
    LockPrior --> RecordAttempt --> T6
    RecordAttempt --> ScoreEngine
    T8 --> ScoreEngine
    ScoreEngine --> UpdateState --> T6
    UpdateState --> T8
    ScoreEngine --> DispatchDomain
    DispatchDomain -->|category=vocabulary| T3
    DispatchDomain -->|category=grammar| T4
    DispatchDomain -->|category=pronunciation| T5
    DispatchDomain -->|category=listening| NoDispatch["WeakPointDispatcherImpl: no case for 'listening'<br/>-> log.warn, computed score dropped<br/>(mistake_history/item_difficulty_stats above are still updated)"]
    RecordAttempt --> BuildHistory
    T6 --> BuildHistory
    BuildHistory --> PublishAR --> AREvent
    T6 --> RevQueue

    T1 --> GetTranscript
    T2 --> GetTranscript
    T3 --> GetWeakV
    T4 --> GetWeakG
    T5 --> GetWeakP

    ImportLib --> T9
    ImportLib --> T13
    SessionReq --> PickClips
    T9 --> PickClips
    T9 --> StreamAudio

    T9 --> ListFolders
    T9 --> ListFolderLessons
    T9 --> GetClipDetail
    T13 --> GetClipDetail
    GetClipDetail --> AlignSentences
    AlignSentences --> T13

    AttemptReq --> ScoreDictation
    T9 --> ScoreDictation
    T12 --> ScoreDictation
    AttemptReq --> ScoreSentenceMistakes
    ScoreDictation --> InsertAttempt --> T11
    ScoreSentenceMistakes --> InsertAttempt
    InsertAttempt --> T10
    InsertAttempt --> Analyze --> T12
    Analyze --> PublishGap
    T11 --> GetDictationHistory
    T9 --> GetDictationHistory
    T11 --> GetAttemptDetail
    T9 --> GetAttemptDetail
    T12 --> GetAttemptDetail
    T10 --> GetAttemptDetail

    T10 --> AiGen
    AiGen --> T12

    T11 --> AiGenFromAttempt
    T10 --> AiGenFromAttempt
    AiGenFromAttempt --> T12

    T12 --> GetAiPracticeDetail

    VLGenReq --> VLResolveWords --> VLGenerate --> VLInsertItem --> T14
    VLSubmitReq --> VLScore --> VLInsertAttempt --> T15
    VLScore --> VLFeed
    VLFeed -.PracticeService.redo(...) in-process, same pipeline as RedoReq.-> LogAttempt
    VLFeed -.same.-> LockPrior

    GLGenReq --> GLResolveRules --> GLGenerate --> GLInsertItem --> T16
    GLSubmitReq --> GLScore --> GLInsertAttempt --> T17
    GLScore --> GLFeed

    GLHistoryAttemptReq --> GLReadAttempt
    T17 --> GLReadAttempt
    GLReadAttempt --> GLAnalyzeMissed --> GLGenerate
    GLInsertItem --> GLListRefreshed
    T16 --> GLListRefreshed
    GLFeed -.PracticeService.redo(...) in-process, same pipeline as RedoReq.-> LogAttempt
    GLFeed -.same.-> LockPrior

    LLGenReq --> LLResolveKw --> LLGenerate --> LLSynthesize --> LLInsertItem --> T18
    LLSubmitReq --> LLScoreClosed
    LLSubmitReq --> LLScoreOpen
    LLScoreClosed --> LLInsertAttempt --> T19
    LLScoreOpen --> LLInsertAttempt
    LLInsertAttempt --> LLFeed
    LLFeed -.PracticeService.redo(...) in-process, same pipeline as RedoReq.-> LogAttempt
    LLFeed -.same.-> LockPrior

    LLHistoryAttemptReq --> LLReadAttempt
    T19 --> LLReadAttempt
    LLReadAttempt --> LLAnalyzeMissed --> LLGenerate
    LLInsertItem --> LLListRefreshed
    T18 --> LLListRefreshed

    SLGenReq --> SLResolveWords --> SLGenerate --> SLSynthesizeSample --> T20
    SLSubmitReq --> SLStoreAudio --> SLScore --> SLInsertAttempt --> T21
    SLScore --> SLFeed
    SLFeed -.PracticeService.redo(...) in-process, same pipeline as RedoReq.-> LogAttempt
    SLFeed -.same.-> LockPrior

    SLHistoryAttemptReq --> SLReadAttempt
    T21 --> SLReadAttempt
    SLReadAttempt --> SLAnalyzeMissed --> SLGenerate
    SLSynthesizeSample --> SLListRefreshed
    T20 --> SLListRefreshed

    T22 --> LibStartReq
    LibStartReq --> LibCountCheck
    LibCountCheck -->|yes, under-stocked| LibGenerate --> LibInsertWord --> T23
    LibInsertWord --> LibTts --> LibStorageWrite --> T23
    LibCountCheck -->|no| LibPickWords
    LibStorageWrite --> LibPickWords
    T23 --> LibPickWords
    LibPickWords --> LibQueueInit --> LibInsertSection --> T24

    LibAnswerReq --> LibScore
    T24 --> LibScore
    LibScore --> LibInsertAnswer --> T25
    LibInsertAnswer --> LibApplyQueue --> LibUpdateQueueState --> T24
    LibApplyQueue -->|queue empty| LibComplete --> T24
    LibComplete --> LibFeed
    T25 --> LibFeed
    LibFeed -.PracticeService.redo(...) in-process, same pipeline as RedoReq.-> LogAttempt
    LibFeed -.same.-> LockPrior
    LibFeed -.shares vocab:&lt;word&gt; itemId, same table.-> T3

    T26 --> GLibContentReq
    GLibContentReq --> GLibContentCheck
    GLibContentCheck -->|no, first read| GLibGenerate --> GLibInsertContent --> T27
    GLibInsertContent --> GLibInsertQuestions --> T28
    GLibContentCheck -->|yes| GLibSessionReq
    T28 --> GLibSessionReq

    GLibSessionReq --> GLibLockCheck
    T29 --> GLibLockCheck
    GLibLockCheck -->|no, UNLOCKED/IN_PROGRESS| GLibSnapshot --> GLibInsertSession --> T30
    GLibInsertSession --> GLibMarkInProgress --> T29

    GLibAnswerReq --> GLibScore
    T30 --> GLibScore
    GLibScore --> GLibInsertAnswer --> T31

    GLibFinishReq --> GLibRecompute
    T30 --> GLibRecompute
    T31 --> GLibRecompute
    GLibRecompute --> GLibCompleteSession --> T30
    GLibRecompute --> GLibFeed
    GLibFeed -.PracticeService.redo(...) in-process, same pipeline as RedoReq.-> LogAttempt
    GLibFeed -.same.-> LockPrior
    GLibRecompute --> GLibAllCorrect
    GLibAllCorrect -->|yes| GLibMarkPassed --> T29
    GLibMarkPassed --> GLibUnlockNext --> T29
    GLibAllCorrect -->|no| GLibRetryGen --> GLibRetrySession --> T30

    GLibHistorySessionReq --> GLibOwnerCheck
    T30 --> GLibOwnerCheck
    GLibOwnerCheck -->|yes| GLibAnalyzeMissed
    T31 --> GLibAnalyzeMissed
    GLibAnalyzeMissed --> GLibHasMistakes
    GLibHasMistakes -->|no| GLibNoRegen
    GLibHasMistakes -->|yes| GLibTopicLookup
    T26 --> GLibTopicLookup
    GLibTopicLookup --> GLibDelegate --> GLGenerate

    T32 --> LLibSectionReq
    LLibSectionReq --> LLibLockCheck
    T35 --> LLibLockCheck
    LLibLockCheck -->|no, UNLOCKED/IN_PROGRESS/PASSED| LLibSectionCheck
    T33 --> LLibSectionCheck
    LLibSectionCheck -->|no, first read| LLibGenerate --> LLibSynthesize --> LLibStorageWrite --> LLibInsertSection --> T33
    LLibInsertSection --> LLibInsertQuestions --> T34
    LLibSectionCheck -->|yes, reuse most recent| LLibMarkInProgress
    LLibInsertQuestions --> LLibMarkInProgress
    LLibMarkInProgress --> T35

    LLibAnswerReq --> LLibScore
    T34 --> LLibScore
    LLibScore --> LLibInsertAttempt --> T36
    LLibInsertAttempt --> T42
    LLibScore --> LLibPassCheck
    LLibPassCheck -->|yes| LLibMarkPassed --> T35
    LLibMarkPassed --> LLibUnlockNext --> T35

    LLibHistorySectionReq --> LLibFindLatestAttempt
    T36 --> LLibFindLatestAttempt
    LLibFindLatestAttempt --> LLibHasAttempt
    LLibHasAttempt -->|no| LLibNoAttempt
    LLibHasAttempt -->|yes| LLibAnalyzeMissed
    T42 --> LLibAnalyzeMissed
    LLibAnalyzeMissed --> LLibHasMistakes
    LLibHasMistakes -->|no| LLibNoRegen
    LLibHasMistakes -->|yes| LLibTopicLookup
    T32 --> LLibTopicLookup
    LLibTopicLookup --> LLibDelegate --> LLGenerate

    T37 --> SLibSectionReq
    SLibSectionReq --> SLibLockCheck
    T40 --> SLibLockCheck
    SLibLockCheck -->|no, UNLOCKED/IN_PROGRESS/PASSED| SLibSectionCheck
    T38 --> SLibSectionCheck
    SLibSectionCheck -->|no, first read| SLibGenerate --> SLibInsertSection --> T38
    SLibInsertSection --> SLibSynthesizeLoop --> T39
    SLibSectionCheck -->|yes, reuse most recent| SLibMarkInProgress
    SLibSynthesizeLoop --> SLibMarkInProgress
    SLibMarkInProgress --> T40

    SLibAttemptReq --> SLibStorageWrite --> SLibScore
    T39 --> SLibScore
    SLibScore --> SLibInsertAttempt --> T41

    SLibFinishReq --> SLibPassCheck
    T39 --> SLibPassCheck
    T41 --> SLibPassCheck
    SLibPassCheck -->|yes, every sentence passed| SLibMarkPassed --> T40
    SLibMarkPassed --> SLibUnlockNext --> T40

    SLibHistorySectionReq --> SLibFindOwnAttempts
    T41 --> SLibFindOwnAttempts
    SLibFindOwnAttempts --> SLibAnalyzeMissed --> SLibHasMistakes
    SLibHasMistakes -->|no| SLibNoRegen
    SLibHasMistakes -->|yes| SLibTopicLookup
    T37 --> SLibTopicLookup
    SLibTopicLookup --> SLibDelegate --> SLGenerate
```

## Data shape at each stage

| Stage | Format | Notes |
|---|---|---|
| `TranscriptReadyEvent` | `{recordingId, userId, fullText, segments: [{speaker, text, startSeconds, endSeconds, language}]}` | decoded from ai-service's snake_case JSON via `EventCodec` |
| `transcripts` row | `{id, recording_id, user_id, full_text}` | one row per recording, idempotent on `recording_id` |
| `transcript_segments` rows | `{id, transcript_id, speaker, content, start_seconds, end_seconds, segment_order, language}` | one row per segment, ordered; `language` (`V4__transcript_segment_language.sql`) is per-segment since ai-service auto-detects each diarized speaker turn's language independently |
| `LearningGapAnalyzedEvent` | `{recordingId, userId, weakPoints: [{itemId, category, label, forgettingScore, recommendation}]}` | covers all categories; each domain's own consumer keeps only its matching category and discards the rest — its own copy of the DTO lives in that domain's `event` package |
| `vocabulary_weak_points` row | `{id, recording_id, user_id, item_id, label, vocabulary_type, forgetting_score, recommendation, mastery_level, next_review_at, score_source, updated_at}` | upserted on `(user_id, item_id)` — re-analysis updates score in place instead of duplicating; `score_source` (`PYTHON_LEGACY`/`JAVA_ENGINE`) guards the upsert so a Kafka-sourced write can't clobber a fresher Java-direct one (see below) |
| `VocabularyType` | enum `NOUN, VERB, ADJECTIVE, ADVERB, PHRASAL_VERB, COLLOCATION, IDIOM, OTHER` | assigned by `VocabularyClassifier` |
| `grammar_weak_points` row | `{id, recording_id, user_id, item_id, label, grammar_type, forgetting_score, recommendation, mastery_level, next_review_at, score_source, updated_at}` | upserted on `(user_id, item_id)`, same shape/guard as vocabulary's table |
| `GrammarType` | enum `TENSE, SUBJECT_VERB_AGREEMENT, ARTICLE, PREPOSITION, WORD_ORDER, PLURAL, PUNCTUATION, OTHER` | assigned by `GrammarClassifier` |
| `pronunciation_weak_points` row | `{id, recording_id, user_id, item_id, label, pronunciation_type, forgetting_score, recommendation, mastery_level, next_review_at, score_source, updated_at}` | upserted on `(user_id, item_id)`, same shape/guard as vocabulary's table |
| `PronunciationType` | enum `VOWEL, CONSONANT, STRESS, INTONATION, LINKING, RHYTHM, OTHER` | assigned by `PronunciationClassifier` |
| `PracticeRedoRequest` | `{userId, attempts: [{itemId, category, label, correct}]}` | REST request body, not an event |
| `practice_attempts` row | `{id, user_id, item_id, category, label, is_correct, attempted_at}` | audit-log insert only, never read back by the scoring pipeline |
| `mistake_history` row | `{id, user_id, item_id, category, label, occurrence_count, last_seen_at, updated_at, ease_factor, half_life_days, mastery, leitner_box, next_review_at, last_weak_score, label_key}` | upserted on `(user_id, item_id)`; `occurrence_count`/`last_seen_at` seeded/updated as before, the scoring-state columns are read (locked via `FOR UPDATE`) then updated by `WeakPointScoringOrchestrator` around each redo attempt |
| `item_difficulty_stats` row | `{category, label_key, correct_count, incorrect_count, updated_at}` | population-level (cross-user) aggregate, keyed `(category, label_key)` — feeds `RaschDifficultyEstimator`'s item-difficulty weight; `label_key` is `LabelKeys.normalize(label)` (trim/collapse-whitespace/lowercase), used because `item_id` isn't a verified cross-user-shared identifier in this system |
| `ScoringResult` (in-memory) | `{weakScore, updatedState: {easeFactor, halfLifeDays, mastery, leitnerBox}, nextReviewAt}` | output of `common.scoring.WeakPointScoringEngine.scoreAfterAttempt`, computed from the item's PRE-attempt state so the same-batch recurrence signal stays meaningful |
| `WeakPointScoreUpdate` (in-memory) | `{recordingId, userId, itemId, category, label, weakScore, masteryLevel, nextReviewAt}` | handed from the orchestrator to whichever domain's `applyJavaComputedScore` owns `category` |
| `AnalysisRequestedEvent` | `{recordingId: "practice-<uuid>", userId, segments: [], history: [{itemId, category, label, occurrenceCount, lastSeenDaysAgo}]}` | built from the learner's full current `mistake_history`, not just the items just redone; `lastSeenDaysAgo` computed as `Duration.between(lastSeenAt, now)` in days; still published so `recommendation-service`/`dashboard-service` stay in sync even though they never see the new Java-direct path |
| `ReviewQueueItem` | `{itemId, category, label, lastWeakScore, nextReviewAt}` | read straight off `mistake_history` where `next_review_at <= now()`, ordered soonest-first — the Leitner schedule surfaced |
| `StartDictationSessionRequest` | `{skill?, level?, topic?, examType?, count}` | REST request body; any null facet is unfiltered on that dimension |
| `dictation_clips` row | `{id, code, title, skill, level, topic, exam_type, script_text, storage_key, source, folder, created_at}` | fixed library clip; `script_text` is never returned by browse/session, only as `referenceText` after grading or `scriptText` on the rev-2 clip-detail endpoint; upsert-by-`code`; `folder` (rev 2) = direct parent directory of the audio file, independent of `topic`/`skill`/`level`/`exam_type` |
| `DictationClipDto` | `{clipId, code, title, skill, level, topic, examType, audioUrl}` | REST response for browse/session; omits the script |
| `dictation_clip_sentences` row (rev 2, translation rev 7) | `{id, clip_id, seq, text, start_ms?, end_ms?, translation?, created_at}` | one row per script line, upserted on `(clip_id, seq)`; `start_ms`/`end_ms` stay null until `GetClipDetail`'s lazy AI-alignment step matches that sentence; `translation` (rev 7) stays null until `ensureSentencesTranslated` fills it for a requested non-"en" `translationLang` |
| `DictationFolderDto` (rev 2) | `{folderId, name, lessonCount}` | REST response for `GET /dictation/folders` |
| `DictationLessonSummaryDto` (rev 3) | `{clipId, code, title, audioUrl, level, sentenceCount, attemptCount?, latestAccuracy?}` | REST response for `GET /dictation/folders/{folderId}/lessons/{userId}`; no script. `sentenceCount` stands in for a duration estimate; `attemptCount`/`latestAccuracy` are the requesting learner's own progress, null when never attempted |
| `DictationClipDetailDto` (rev 2, translation rev 7) | `{clipId, code, title, audioUrl, scriptText, sentences: [{index, text, startMs?, endMs?, translation?}]}` | REST response for `GET /dictation/clips/{clipId}?translationLang=`; the only rev-2 endpoint that exposes the script, and only for the one clip opened; `translation` lazily filled the same way `startMs`/`endMs` are, gated on `translationLang` being present and not "en" |
| `DictationAttemptRequest` | `{userId, clipId? | practiceItemId?, userTranscript, sentenceMistakes?: [{sentenceIndex, expectedText, attemptedText}]}` | REST request body; exactly one of clipId/practiceItemId; `sentenceMistakes` (rev 3) only sent for sentence-mode retries, scored separately and merged into the same miss list as `userTranscript`'s diff |
| `DictationScoreResult` (in-memory) | `{accuracy, wer, diff: [{tag: CORRECT|SUBSTITUTED|MISSING|EXTRA, actualWord, expectedWord}]}` | output of `DictationScorer.score`, a pure word-level Levenshtein alignment |
| `dictation_misses` row | `{id, attempt_id, user_id, clip_id, expected_word, actual_word, tag, created_at}` | one row per wrong word; drives AI analysis + the published forgetting score |
| `dictation_attempts` row | `{id, clip_id?, practice_item_id?, user_id, user_transcript, accuracy, wer, ai_suggestions?, created_at}` | one row per graded submission, full history kept; `ai_suggestions` is a JSON-encoded `DictationAnalysis` (`errorTable`/`rootCauses`/`actionAdvice`/`practiceSentences`) generated at submit time - column name predates this shape; attempts from before it shipped stored a plain string array, read back as `actionAdvice` with empty `errorTable`/`rootCauses`; null for attempts made before the column existed |
| `dictation_practice_items` row | `{id, user_id, sentence_text, source, storage_key?, level?, exam_type?, topic?, translation_text?, created_at}` | `AiGenFromAttempt`: one row per generated passage (level/exam_type left null - no facet selector on this entry point); `AiGen` (rev 5, taxonomy rev 7): one row for the whole generated dialogue passage (`sentence_text` = full passage, `"Speaker: line"` per turn if multi-speaker); `level`/`exam_type`/`topic` (rev 7) = the resolved facets (concrete or RANDOM-resolved) plus the LLM's own topic label; `translation_text` (rev 7) is the parallel per-line translation, populated only when `translationLang` was requested and isn't "en"; `storage_key` set once Supertonic audio synthesized/merged |
| `GenerateAiPracticeRequest` (rev 7) | `{level?, examType?, translationLang?}` | REST request body for `AiGen`; `level`/`examType` each accept a concrete value, the literal `"RANDOM"` (server resolves it - level from `A1,A2,B1,B2,C1`, examType from the library's own distinct exam types, falling back to `TOEIC,IELTS,TOEFL,General`), or unset (no preference, LLM's own default) |
| `DictationPracticeItemDetailDto` (rev 6, taxonomy+translation rev 7) | `{practiceItemId, audioUrl, scriptText, level?, examType?, topic?, sentences: [{index, text, startMs: null, endMs: null, translation?}]}` | REST response for `GetAiPracticeDetail`; `sentences` split in-memory from `sentence_text`/`translation_text` in parallel (one per dialogue line, or by sentence-ending punctuation for a monologue) - mirrors `DictationClipDetailDto` but timings are always null since the passage is one merged audio file |
| `DictationAttemptResultDto` | `{referenceText, accuracy, wer, diff[], errorTable: [{original, transcribed, category: LEXICON\|GRAMMAR\|PHONOLOGY, note?}], rootCauses: [{category, summary, examples[]}], actionAdvice[], practiceSentences[]}` | REST grading response; `rootCauses` only lists categories that actually occurred; only point `script_text` is exposed |
| published `learning.gap.analyzed` | `{recording_id: "dictation-clip-<id>", user_id, weak_points: [{item_id: "dictation:<word>", category: vocabulary\|grammar\|pronunciation, label, forgetting_score, recommendation: actionAdvice[0]}]}` | **category is per-word, not always `"vocabulary"`**: `toLearningCategory` maps each missed word's `errorTable` root-cause (`LEXICON`/`GRAMMAR`/`PHONOLOGY`) to `vocabulary`/`grammar`/`pronunciation`; a word with no `errorTable` entry (e.g. a sentence-mode-retry miss, which never goes through `DictationAnalyzer`) defaults to `vocabulary`; dictation misses fed into the existing recommendation pipeline this way |
| `DictationHistoryEntryDto.practiceType` | `LIBRARY \| AI_PRACTICE` | derived in Java from `clipId` being present/null - not a DB column - so the FE can badge each history row |
| `GenerateVocabPracticeRequest` / `GenerateGrammarPracticeRequest` | `{level?, examType?, focusItems?}` | REST request body; `focusItems` (explicit words/rules from a "Luyện ngay" deep-link) wins over the learner's own weak points |
| `vocab_practice_items` / `grammar_practice_items` row | `{id, user_id, level?, exam_type?, topic, target_words\|target_rules (JSON string[]), items (JSON `VocabQuestionItem[]`\|`GrammarQuestionItem[]`), created_at}` | one row per generated set; `items` holds the full graded content (prompt/type/options/answer/translation), now surfaced on `getItem`/`listItems` too (see `VocabQuestionDto`/`GrammarQuestionDto` below) |
| `VocabQuestionItem` | `{targetWord, type: CLOZE\|MCQ\|MATCHING, prompt, options?, answer, translation}` | `options` null for `CLOZE`; JSON element of `vocab_practice_items.items` |
| `GrammarQuestionItem` | `{targetRule, type: ERROR_CORRECTION\|FILL_TENSE\|TRANSFORM\|MCQ, prompt, options?, answer, translation, translationVi}` | `options` only for `MCQ`; JSON element of `grammar_practice_items.items`. `translationVi` is a plain Vietnamese meaning-translation of `answer`, distinct from `translation` (a grammar-rule explanation) - grammar-only, `VocabQuestionItem` has no equivalent |
| `VocabQuestionDto` / `GrammarQuestionDto` (REST) | `{index, prompt, type, options?, answer, translation}` (+ `translationVi` on `GrammarQuestionDto` only) | generate/`getItem`/`listItems` response - now **includes** `answer` + `translation` so the client can grade each question locally for instant feedback (mirrors `ExactMatchScorer`); the authoritative score still comes only from the submit-attempt endpoint |
| `SubmitVocabAttemptRequest` / `SubmitGrammarAttemptRequest` | `{userId, practiceItemId, answers: string[]}` | REST request body, one answer per question index |
| `VocabScoreResult` / `GrammarScoreResult` (in-memory) | `{accuracy, perQuestionCorrect: boolean[]}` | pure exact-match scorer, no partial credit |
| `vocab_practice_attempts` / `grammar_practice_attempts` row | `{id, practice_item_id, user_id, answers (JSON string[]), score, created_at}` | one row per submission, full history kept |
| `VocabAttemptResultDto` / `GrammarAttemptResultDto` | `{accuracy, results: [{index, prompt, yourAnswer, correctAnswer, correct, translation}], actionAdvice[]}` (+ `translationVi` per result on `GrammarAttemptResultDto` only) | REST grading response |
| `PracticeAttemptRequest` fed from vocabulary/grammar learn | `{itemId: "vocab:<word>"\|"grammar:<rule>", category: "vocabulary"\|"grammar", label, correct}` | one per distinct target word/rule in the submitted attempt (dedup by lower-cased label), passed straight into `PracticeService.redo(...)` - **not** a new event, reuses `PracticeFlow`'s existing scoring/dispatch |
| `GrammarMistakeAnalyzer.extractMissedRules` output (in-memory) | `List<String>` distinct `targetRule` values | pure function over an attempt's stored `itemsJson`/`answersJson`, re-scored with the same `GrammarAttemptScorer` the original attempt used; feeds straight into `GrammarPracticeGenerator.generate(missedRules, level, examType)` for `POST /learn/grammar/history/{userId}/{attemptId}/ai-practice` |
| `GrammarMistakeAnalyzer.hasAnyMissedQuestion` output (in-memory) | `boolean` | pure function over a Grammar Library session's `questionsJson` + its answers; library questions carry no explicit rule tag of their own and a session is already scoped to one topic, so there is no per-question rule to diff out - this only answers whether the session had any mistake. When `true`, the call site (`GrammarLibraryServiceImpl.generatePracticeFromSession`) builds the target-rules list itself as `List.of(topic.getName())` (the session's single topic name) and feeds that into `GrammarPracticeGenerator.generate(...)` via `GrammarLearnService.generatePracticeForRules`; when `false`, generation is skipped entirely and an empty list is returned |
| `grammar_practice_items` row from a "generate from attempt/session" call | same shape as the `generate`-produced row above | both `POST /learn/grammar/history/{userId}/{attemptId}/ai-practice` and `POST /learn/grammar/library/{userId}/sessions/{sessionId}/ai-practice` insert into this same table via `GrammarLearnServiceImpl.generatePracticeForRules` - there is only one AI-practice bank per domain regardless of which flow (learn attempt vs. library session) the mistake came from; the library flow's `target_rules` is always the single-element `[topic.name]`, not per-question text |
| `GenerateListeningPracticeRequest` | `{level?, examType?, translationLang?, focusItems?}` | REST request body |
| `listening_practice_items` row | `{id, user_id, level?, exam_type?, topic, transcript, translation?, questions (JSON `ListeningQuestionItem[]`), storage_key?, created_at}` | one row per generated passage; audio synthesized synchronously in the same call, `storage_key` set before the row is returned |
| `ListeningQuestionItem` | `{type: MCQ\|KEYWORD\|OPEN, skill, prompt, options?, answer, explanation}` | `skill` (e.g. "main-idea"/"detail"/"attitude"/"keyword") doubles as the weak-point label for non-`KEYWORD` questions; `options` only for `MCQ`; `answer` is the correct option (`MCQ`), expected phrase (`KEYWORD`, scored by WER), or model answer (`OPEN`, used as the LLM grading reference) |
| `ListeningQuestionDto` (REST) | `{index, prompt, type, options?, answer, explanation}` | generate/`getItem`/`listItems` response - now **includes** `answer` + `explanation` for client-side grading of `MCQ`/`KEYWORD`; `answer` is **null** for `OPEN` (those are LLM-graded server-side by `OpenAnswerGrader` and must not leak). transcript/translation still withheld until the attempt is submitted; authoritative score still from the submit endpoint |
| `SubmitListeningAttemptRequest` | `{userId, practiceItemId, answers: string[]}` | REST request body |
| `OpenAnswerGrade` (in-memory) | `{score: 0..1, feedback}` | `OpenAnswerGrader` (LLM) output for `OPEN` questions only; `MCQ`/`KEYWORD` are scored by the pure `ListeningQuestionScoring.scoreClosed` instead |
| `listening_attempts` row | `{id, practice_item_id, user_id, answers (JSON string[]), results (JSON `ListeningAttemptQuestionResultDto[]`), score, created_at}` | `score` = mean of all `subScore`s (each question's own 0..1); each result now also carries the question's `type` (`MCQ`\|`KEYWORD`\|`OPEN`), added so `ListeningMistakeAnalyzer.extractMissedTopics` can tell OPEN questions apart from KEYWORD/MCQ (`null` for attempts persisted before this field existed) |
| `ListeningAttemptResultDto` | `{accuracy, results: [{index, prompt, yourAnswer, correctAnswer, correct, subScore, explanation}], transcript, translation?, actionAdvice[]}` | REST grading response; `transcript`/`translation` returned **only** on this response, not on `getItem`/`listItems` (those would leak the passage; note the per-question `answer`/`explanation` themselves are now on `getItem`/`listItems` for client-side grading, `answer` null for `OPEN`) |
| `PracticeAttemptRequest` fed from listening learn | `{itemId: "listening:<label>", category: "listening", label, correct}` | one per distinct label (KEYWORD's `answer`, or MCQ/OPEN's `skill`); **`category = "listening"` has no matching consumer in `WeakPointDispatcherImpl`** - `mistake_history`/`item_difficulty_stats`/the review queue and `learning.gap.analysis.requested`'s `history[]` still pick it up (all category-agnostic), but no `listening_weak_points` table exists and the Java-computed score for it is simply logged and dropped - see the note below |
| `ListeningMistakeAnalyzer.extractMissedTopics` output (in-memory) | `List<String>` distinct `correctAnswer` values | pure function over an attempt's persisted `resultsJson` (`ListeningAttemptQuestionResultDto[]`, already graded - not re-scored); `resultsJson` carries no per-question topic/skill tag of its own (only `prompt`/`correctAnswer`/`explanation`), so each wrong question's own `correctAnswer` is used as the retry target text (the missed keyword itself for `KEYWORD`, the correct option/model answer for `MCQ`/`OPEN`); feeds straight into `ListeningPracticeGenerator.generate(missedTopics, level, examType, translationLang=null)` for `POST /learn/listening/history/{userId}/{attemptId}/ai-practice` |
| `listening_library_attempt_answers` row | `{id, attempt_id, question_id, selected_option, correct_option, is_correct, created_at}` | one row per submitted answer within a `listening_library_attempts` row (Task 1), so a later feature can regenerate AI practice targeting only the questions actually missed - mirrors dictation's mistake-history pattern |
| `ListeningMistakeAnalyzer.hasAnyMissedQuestion` output (in-memory) | `boolean` | pure function over a Listening Library section's most recent attempt's `listening_library_attempt_answers` rows; library questions carry no explicit topic tag of their own and a Section is already scoped to one topic, so there is no per-question topic to diff out - this only answers whether the attempt had any mistake. When `true`, the call site (`ListeningLibraryServiceImpl.generatePracticeFromSection`) builds the target-keywords list itself as `List.of(topic.getName())` (the section's owning topic name) and feeds that into `ListeningPracticeGenerator.generate(...)` via `ListeningLearnService.generatePracticeForKeywords`; when `false` (or no completed attempt exists), generation is skipped entirely and an empty list is returned |
| `listening_practice_items` row from a "generate from attempt/section" call | same shape as the `generate`-produced row above (including synthesized audio) | both `POST /learn/listening/history/{userId}/{attemptId}/ai-practice` and `POST /learn/listening/library/{userId}/sections/{sectionId}/ai-practice` insert into this same table via `ListeningLearnServiceImpl.generatePracticeForKeywords` - there is only one AI-practice bank per domain regardless of which flow (learn attempt vs. library section) the mistake came from; the library flow's target keywords is always the single-element `[topic.name]`, not per-question text |
| `GenerateSpeakingPracticeRequest` | `{level?, examType?, focusItems?}` | REST request body |
| `speaking_practice_items` row | `{id, user_id, level?, exam_type?, topic, target_text, translation?, storage_key?, created_at}` | one row per generated sentence/passage; `storage_key` is the Supertonic **sample** (model) recording, synthesized with one fixed voice (unlike listening's multi-speaker dialogue) |
| `SpeakingAttemptRequest` (multipart) | `{userId (path), practiceItemId, audio (multipart file)}` | REST request; audio persisted to `StorageClient` before scoring, so a scoring failure still leaves the recording retrievable |
| `PronunciationScore` (in-memory, from ai-service) | `{overall, words: [{word, score, phonemes: [{ipa, score}]}], transcript, weakPhonemes: string[]}` | decoded from ai-service's `POST /api/v1/pronunciation/score` JSON response via `PronunciationScoringClient` (`common.ai.pronunciation`) - see the ai-service GOP note below |
| `speaking_attempts` row | `{id, practice_item_id, user_id, audio_storage_key, overall_score, word_scores (JSON `WordScoreDto[]`), transcript?, weak_phonemes (JSON string[]), created_at}` | one row per recorded submission |
| `SpeakingAttemptResultDto` | `{overall, words: [{word, score, phonemes: [{ipa, score}]}], transcript, weakPhonemes[], actionAdvice[]}` | REST grading response |
| `PracticeAttemptRequest` fed from speaking learn | `{itemId: "pronunciation:<word>", category: "pronunciation", label, correct: score >= 0.6}` | one per distinct word in `words[]`; reuses `pronunciation_weak_points` (the same table ai-service's original forgetting-pattern pipeline and `english-service`'s own `pronunciation.kafka` consumer write to) - no new table, unlike `listening` |
| `SpeakingMistakeAnalyzer.extractWeakPhonemes` output (in-memory) | `List<String>` distinct IPA symbols | pure function over one attempt's persisted `weakPhonemesJson` (both `speaking_attempts.weak_phonemes_json` for learn and `speaking_library_attempts.weak_phonemes_json` for library - Task 2 deliberately reused the same shape); unlike `ListeningMistakeAnalyzer.extractMissedTopics`, no per-question OPEN-vs-KEYWORD diffing/fallback is needed - ai-service's GOP scorer already reduced each mistake to a short, crisp IPA symbol, always a good-enough generator target on its own; feeds straight into `SpeakingPracticeGenerator.generate(weakPhonemes, level, examType)` for `POST /learn/speaking/history/{userId}/{attemptId}/ai-practice` |
| `speaking_practice_items` row from a "generate from attempt/section" call | same shape as the `generate`-produced row above (including the synthesized sample audio) | both `POST /learn/speaking/history/{userId}/{attemptId}/ai-practice` and `POST /learn/speaking/library/{userId}/sections/{sectionId}/ai-practice` insert into this same table via `SpeakingLearnServiceImpl.generatePracticeForKeywords` - there is only one AI-practice bank per domain regardless of which flow (learn attempt vs. library section) the mistake came from |
| `vocabulary_topics` row | `{id, name, description?, created_at}` | fixed topic list (Du lịch, Công việc, Đời sống hàng ngày, Ẩm thực, Công nghệ, Sức khỏe, Giáo dục, Môi trường), seeded once, not learner-specific |
| `GeneratedLibraryWord` | `{word, wordType, meaningVi, exampleEn}` | LLM JSON, one per new word `LlmLibraryWordGenerator` asks Gemini for when a topic is under-stocked; empty list (not a template) on any call/parse failure |
| `vocabulary_library_words` row | `{id, topic_id, word, word_type, meaning_vi, example_en, audio_storage_key?, created_at}` | one row per generated word; `audio_storage_key` set right after synthesis, so a TTS failure still leaves the word itself usable (just no audio) |
| `SectionStartRequest` | `{sectionSize?}` | REST request body; defaults to 10 words |
| `SectionQueueEntry` (JSON in `queue_state`) | `{wordId, streak, introShown, pendingExerciseType?}` | one entry per word still in play; `SectionQueue.initial` builds the starting list (shuffled, `streak=0`, `introShown=false`) |
| `vocabulary_section_attempts` row | `{id, user_id, topic_id, status: IN_PROGRESS\|COMPLETED\|ABANDONED, section_size, library_word_ids_json, queue_state_json, created_at, completed_at?}` | `queue_state_json` is the live `SectionQueueEntry[]`, rewritten on every answer until the queue empties or the learner quits early (`ABANDONED`) |
| `SectionCardDto` | `{cardKind: INTRO\|QUIZ, word?, meaningVi?, exampleEn?, audioUrl?, exerciseType?, progress}` | REST response for both start-section and the next-card half of submit-answer; `SectionCardBuilder` omits any field that would leak the answer for the card's `exerciseType` (e.g. no `audioUrl` on CLOZE/MCQ) |
| `SubmitSectionAnswerRequest` | `{submittedAnswer?}` | REST request body; absent for exercise types with no free-text answer |
| `SectionAnswerScore` (in-memory) | `{score, correct}` | `SectionAnswerScoring.scoreClosed` (WER or exact-match) for every exercise type except `TRANSLATE_EN_TO_VI` |
| `OpenAnswerGrade` (in-memory) | `{score, feedback}` | `OpenAnswerGrader.grade` (LLM) output, `TRANSLATE_EN_TO_VI` only - same grader `listening`'s `OPEN` questions use |
| `vocabulary_section_answers` row | `{id, section_attempt_id, library_word_id, exercise_type, submitted_answer?, score, correct, created_at}` | one row per answer submitted, full in-session repetition history kept (a word can appear more than once per section) |
| `SectionQueueEntry` (post-`applyResult`) | `{wordId, streak, introShown, pendingExerciseType?}` | `SectionQueue.applyResult` drops the word (mastered) at `streak==2`, else requeues it `+6` cards on a correct-not-yet-mastered answer or `+2` cards (streak reset to `0`) on a wrong one |
| `SectionAnswerResultDto` | `{correct, correctAnswer?, completed, nextCard?, progress}` | REST grading response; `nextCard` is null once `completed=true` |
| `PracticeAttemptRequest` fed from vocabulary library | `{itemId: "vocab:<word>", category: "vocabulary", label, correct}` | one per `vocabulary_section_answers` row for the completed/abandoned attempt, **NOT** deduped by word (unlike `VocabLearnFlow`'s `VLFeed`) - a word answered 3 times in one Section produces 3 requests, so a same-batch repeat sets `recurredInBatch=true` in `WeakPointScoringEngine`, the entire point of in-session repetition; shares `vocabulary_weak_points` with `VocabLearnFlow` (same `item_id` scheme), no second mastery table |
| `grammar_library_topics` row | `{id, code, name, description?, level, sequence_order, created_at}` | fixed, hand-seeded catalog of 60 topics (`V17__grammar_library.sql`), never generated/topped up at runtime, unlike `vocabulary_topics`/`vocabulary_library_words` |
| `GeneratedGrammarTopicContent` (LLM JSON) | `{explanationEn, explanationVi, illustrationText, examples: [{en, vi}], questions: [{type, prompt, options?, answer, explanationVi, translationVi}] (8-10 items)}` | `LlmGrammarLibraryContentGenerator.generateTopicContent` output; falls back to a minimal static template (not empty) on call/parse failure. `translationVi` is a plain Vietnamese meaning-translation of `answer`, distinct from `explanationVi` (a grammar-rule explanation) |
| `grammar_library_contents` row | `{id, topic_id (unique), explanation_en, explanation_vi, illustration_text, examples_json, generated_at}` | one row per topic, generated once on first `GET .../topics/{topicId}` and reused forever |
| `grammar_library_questions` row | `{id, topic_id, question_type, prompt, options_json?, answer, explanation_vi?, translation_vi?, created_at}` | the reusable 8-10 question pool, generated alongside the content row in the same transaction (`translation_vi` added in `V18__grammar_library_translation.sql`, nullable for rows generated before it existed) |
| `GrammarLibraryTopicDto` (REST) | `{topicId, code, name, description?, level, sequenceOrder, status: LOCKED\|UNLOCKED\|IN_PROGRESS\|PASSED}` | `GET .../{userId}/topics` response; `status` comes from `grammar_topic_progress`, defaulting to `LOCKED` when no row exists |
| `GrammarLibraryContentDto` (REST) | `{topicId, explanationEn, explanationVi, illustrationText, examples: [{en, vi}], questions: GrammarLibraryQuestionDto[]}` | `GET .../topics/{topicId}` response; `GrammarLibraryQuestionDto` **includes** `answer`/`explanationVi`/`translationVi` (theory view, not a quiz) |
| `grammar_topic_progress` row | `{id, user_id, topic_id, status, unlocked_at?, passed_at?, updated_at}` | upserted on `(user_id, topic_id)`; the first topic (`sequence_order=1`) is bootstrapped to `UNLOCKED` the first time a learner calls `listTopics` |
| `GrammarLibrarySessionQuestion` (JSON element, in `questions_json`) | `{questionRef, type, prompt, options?, answer, explanationVi, translationVi}` | a full content snapshot, not a foreign-key reference - lets a `RETRY` session's freshly-generated questions live inline without ever touching `grammar_library_questions` |
| `grammar_library_sessions` row | `{id, user_id, topic_id, session_type: INITIAL\|RETRY, questions_json, status: IN_PROGRESS\|COMPLETED, correct_count, total_count, started_at, completed_at?}` | `INITIAL` snapshots the full pool (`questionRef = "q-" + questionId`); `RETRY` snapshots only AI-regenerated replacements for the previously-wrong questions (`questionRef = "r-" + index`) |
| `StartGrammarSessionResponse` (REST) | `{sessionId, sessionType, questions: GrammarSessionQuestionDto[], totalCount}` | `GrammarSessionQuestionDto` omits `answer`/`explanationVi`/`translationVi` (in-progress quiz, unlike the theory-page DTO) |
| `SubmitGrammarLibraryAnswerRequest` | `{questionRef, submittedAnswer?}` | REST request body |
| `grammar_library_session_answers` row | `{id, session_id, question_ref, submitted_answer?, correct, answered_at}` | one row per submission; a re-submitted `questionRef` is resolved to its most-recent row at `finishSession` time |
| `GrammarLibraryAnswerResultDto` (REST) | `{questionRef, correct, correctAnswer?, explanationVi?, translationVi?}` | `POST .../sessions/{sessionId}/answers` response |
| `FinishGrammarLibrarySessionResponse` (REST) | `{sessionId, correctCount, totalCount, passed, retrySession?: StartGrammarSessionResponse, nextTopicUnlocked, nextTopicId?}` | `retrySession` non-null only when `passed=false` |
| `PracticeAttemptRequest` fed from grammar library | `{itemId: "grammar:<topicCode>", category: "grammar", label: topicName, correct}` | one per question in the finished session, **NOT** deduped (same convention as `LibFeed` above) - lets a session's repeated exposure to the same topic register as in-batch recurrence |
| `GrammarLibraryHistoryEntryDto` (REST, updated) | `{sessionId, topicId, sessionType, correctCount, totalCount, accuracy, completedAt?}` | `topicId` is new (merged-history task) - previously omitted since the existing per-topic endpoint always knew `topicId` from the path; now needed so a cross-topic listing still carries it |
| `GrammarHistoryEntryDto` (REST, new) | `{source: "LEARN"\|"LIBRARY", attemptOrSessionId, completedAt?, score?, topicId?}` | `GET /api/v1/learn/grammar/merged-history/{userId}` response - normalizes `GrammarAttemptHistoryEntryDto` (learn) and `GrammarLibraryHistoryEntryDto` (library) into one shape, built by a new standalone `GrammarHistoryServiceImpl` (depends on both `GrammarLearnService`/`GrammarLibraryService` interfaces to avoid a circular bean dependency, since `GrammarLibraryServiceImpl` already depends on `GrammarLearnService`), sorted descending by `completedAt`. `topicId` only populated for `LIBRARY` rows |
| `listening_library_topics` row | `{id, code, name, description?, level, sequence_order, created_at}` | fixed, hand-seeded catalog (`V19__listening_library.sql`, same topic set/order as `grammar_library_topics`), never generated/topped up at runtime |
| `GeneratedListeningLibrarySection` (LLM JSON) | `{passage, questions: [{question, options[4], correctOption: A\|B\|C\|D, explanation}] (4 items)}` | `LlmListeningLibraryGenerator.generateSection` output; unlike `GeneratedGrammarTopicContent`, **no** static-template fallback - any call/parse failure or blank passage throws `AiContentException`, so a failed generation simply produces no Section |
| `listening_library_sections` row | `{id, topic_id, passage_text, audio_storage_key?, created_at}` | one row per generated Section; `audio_storage_key` is set before insert (unlike the "learn" skills' insert-then-update-key flow) since this mapper has no update-key method, addressed by `topic_id` + a random suffix instead of the not-yet-known section id |
| `listening_library_questions` row | `{id, section_id, question_text, options_json, correct_option, explanation, created_at}` | the reusable 4-question pool, generated alongside the section row (not in the same DB transaction as `grammar.library`'s content+questions insert, but sequentially in the same generator call) |
| `ListeningLibraryTopicDto` (REST) | `{id, name, level, status: LOCKED\|UNLOCKED\|IN_PROGRESS\|PASSED}` | `GET .../{userId}/topics` response; `status` comes from `listening_topic_progress`, defaulting to `LOCKED` when no row exists |
| `listening_topic_progress` row | `{id, user_id, topic_id, status, unlocked_at?, passed_at?, updated_at}` | upserted on `(user_id, topic_id)`, structurally identical to `grammar_topic_progress`; the first topic (`sequence_order=1`) is bootstrapped to `UNLOCKED` the first time a learner calls `getTopics` |
| `ListeningLibrarySectionDto` (REST) | `{sectionId, passageText, audioUrl?, questions: {questionId, questionText, options[]}[]}` | `POST .../topics/{topicId}/sections` response; questions omit `correctOption`/`explanation` (in-progress quiz, answers not leaked) |
| `SubmitListeningAnswersRequest` | `{answers: [{questionId, selectedOption}]}` | REST request body |
| `listening_library_attempts` row | `{id, user_id, section_id, score, correct_count, total_questions, started_at, completed_at}` | one row per graded submission, full history kept; no upsert/idempotency key - every submission is a new row |
| `SubmitListeningAnswersResponse` (REST) | `{score, correctCount, totalQuestions, topicPassed, nextTopicId?, nextTopicUnlocked}` | `POST .../sections/{sectionId}/answers` response; `topicPassed = score >= 0.7`; `nextTopicId`/`nextTopicUnlocked` only populated when passed and a next topic exists |
| `ListeningLibraryAttempt` (REST, history) | `{id, userId, sectionId, score, correctCount, totalQuestions, startedAt, completedAt}` | `GET .../{userId}/sections/history` response; the domain row is returned directly, unlike `GrammarLibraryHistoryEntryDto` which is a dedicated DTO - no separate history-view type exists for listening library today |
| `ListeningHistoryEntryDto` (REST, new) | `{source: "LEARN"\|"LIBRARY", attemptOrSessionId, completedAt?, score?, sectionId?, topicId?}` | `GET /api/v1/learn/listening/merged-history/{userId}` response - normalizes `ListeningAttemptHistoryEntryDto` (learn) and the raw `ListeningLibraryAttempt` (library) into one shape, built by a new standalone `ListeningHistoryServiceImpl` for the same circular-dependency-avoidance reason as `GrammarHistoryServiceImpl`. `sectionId`/`topicId` only populated for `LIBRARY` rows; `topicId` is resolved from `sectionId` via `ListeningLibraryService#resolveTopicId(sectionId)` (a section row already carries its owning `topicId` - see `ListeningLibrarySection`) so the FE's "Làm lại" button can deep-link straight to the owning topic instead of just switching to the library tab |
| listening library has no `PracticeAttemptRequest`/`PracticeService.redo(...)` feed | — | unlike every other "library"/"learn" skill, scoring here writes only to `listening_library_attempts`/`listening_topic_progress` - it does not reach the weak-point/spaced-repetition pipeline at all (consistent with the pre-existing gap that category `listening` has no dedicated weak-point table anywhere in the service, see the `PracticeAttemptRequest fed from listening learn` row above) |
| `speaking_library_topics` row | `{id, code, name, description?, level, sequence_order, created_at}` | fixed, hand-seeded catalog (`V20__speaking_library.sql`, same topic set/order as `grammar_library_topics`/`listening_library_topics`), never generated/topped up at runtime |
| `GeneratedSpeakingLibrarySection` (LLM JSON) | `{sentences: [{text, ipa}] (5 items)}` | `LlmSpeakingLibraryGenerator.generateSection` output; same "no static-template fallback" behavior as `GeneratedListeningLibrarySection` - any call/parse failure or empty `sentences` throws `AiContentException`, so a failed generation simply produces no Section |
| `speaking_library_sections` row | `{id, topic_id, created_at}` | one row per generated Section, inserted **before** its sentences (it carries no content columns of its own, unlike `listening_library_sections`) |
| `speaking_library_sentences` row | `{id, section_id, sentence_text, ipa?, sample_audio_storage_key?, created_at}` | the reusable 5-sentence pool, each with its own Supertonic sample clip synthesized+stored before insert, addressed by `topic_id` + a random suffix per sentence (mirrors listening's "set before insert" ordering, just one clip per sentence instead of one per section) |
| `SpeakingLibraryTopicDto` (REST) | `{id, name, level, status: LOCKED\|UNLOCKED\|IN_PROGRESS\|PASSED}` | `GET .../{userId}/topics` response; `status` comes from `speaking_topic_progress`, defaulting to `LOCKED` when no row exists |
| `speaking_topic_progress` row | `{id, user_id, topic_id, status, unlocked_at?, passed_at?, updated_at}` | upserted on `(user_id, topic_id)`, structurally identical to `listening_topic_progress`/`grammar_topic_progress`; the first topic (`sequence_order=1`) is bootstrapped to `UNLOCKED` the first time a learner calls `getTopics` |
| `SpeakingLibrarySectionDto` (REST) | `{sectionId, sentences: {sentenceId, sentenceText, ipa?, sampleAudioUrl?}[]}` | `POST .../topics/{topicId}/sections` response |
| `PronunciationScore` (from `PronunciationScoringClient.score`) | `{overall, words: [{word, score, phonemes: [{ipa, score}]}], transcript, weakPhonemes[]}` | same ai-service wav2vec2 GOP response `speaking.learn` already decodes; collapsed to `wordScore`/`phonemeScore` (plain averages over `words[].score` and `words[].phonemes[].score`) rather than persisting the full per-word/per-phoneme breakdown |
| `speaking_library_attempts` row | `{id, user_id, section_id, sentence_id, phoneme_score, word_score, recorded_audio_storage_key?, weak_phonemes_json? (JSON string[] of IPA symbols, verbatim `PronunciationScore.weakPhonemes()` - same shape/threshold `speaking_attempts.weak_phonemes_json` uses for `speaking.learn`, not re-derived), created_at}` | one row per scored **sentence** attempt (unlike listening's one row per whole section submission), full history kept; no upsert/idempotency key |
| `SentenceAttemptResultDto` (REST) | `{sentenceId, phonemeScore, wordScore, passed, transcript}` | `POST .../sections/{sectionId}/sentences/{sentenceId}/attempts` response; `passed = phonemeScore >= 0.7 AND wordScore >= 0.7`, informational only - does not itself unlock anything; weak-phoneme detail is persisted but not (yet) echoed in this response, only via the history endpoint |
| `FinishSectionResponse` (REST) | `{totalSentences, passedSentences, passed, nextTopicId?, nextTopicUnlocked}` | `POST .../sections/{sectionId}/finish` response; `passed` requires every sentence to have at least one qualifying attempt (not necessarily its most recent one) |
| `SpeakingLibraryAttempt` (REST, history) | `{id, userId, sectionId, sentenceId, phonemeScore, wordScore, recordedAudioStorageKey?, weakPhonemesJson?, createdAt}` | `GET .../{userId}/sections/history` response; the domain row is returned directly, same simplification as `ListeningLibraryAttempt` |
| `SpeakingHistoryEntryDto` (REST, new) | `{source: "LEARN"\|"LIBRARY", attemptOrSessionId, completedAt?, score?, sectionId?, topicId?}` | `GET /api/v1/learn/speaking/merged-history/{userId}` response - normalizes `SpeakingAttemptHistoryEntryDto` (learn, `score = overallScore`) and the raw `SpeakingLibraryAttempt` (library, `score = (phonemeScore + wordScore) / 2.0`, averaging both accuracy signals) into one shape, built by a new standalone `SpeakingHistoryServiceImpl` for the same circular-dependency-avoidance reason as `GrammarHistoryServiceImpl`/`ListeningHistoryServiceImpl`. Library rows keep `SpeakingLibraryAttempt`'s existing per-sentence-attempt granularity, not rolled up per section; `topicId` is resolved from `sectionId` via `SpeakingLibraryService#resolveTopicId(sectionId)` (mirrors the listening addition) so the FE's "Làm lại" button can deep-link straight to the owning topic |
| speaking library has no `PracticeAttemptRequest`/`PracticeService.redo(...)` feed | — | same deliberate scope cut as `listening.library`: scoring here writes only to `speaking_library_attempts`/`speaking_topic_progress`, not to `pronunciation_weak_points` (unlike `speaking.learn`, which does feed that table via the same `PronunciationScoringClient` call) |
| `SpeakingMistakeAnalyzer.extractWeakPhonemes` output, unioned across attempts (in-memory) | `List<String>` distinct IPA symbols | unlike `ListeningMistakeAnalyzer.hasAnyMissedQuestion` (only the section's single latest attempt matters, since one attempt scores a whole section), speaking-library scores **per sentence** - a section has several sentences, each retried any number of times - so `SpeakingLibraryServiceImpl.generatePracticeFromSection` first filters `attemptMapper.findBySectionId(sectionId)` down to this learner's own rows (the mapper itself is not user-scoped, since a Section is a shared catalog object any learner may have attempted), then unions every one of those attempts' `extractWeakPhonemes` output (distinct, first-seen order) rather than reading just the latest one. Empty if no attempts, or none had a mispronounced phoneme - generation is skipped and an empty list returned, same as `listening.library`. When non-empty, feeds directly into `SpeakingPracticeGenerator.generate(...)` via `SpeakingLearnService.generatePracticeForKeywords(userId, weakPhonemes, topic.level, examType=null)` - unlike Grammar/Listening Library's topic-name fallback, no such fallback is needed here since the phonemes themselves are already crisp generator targets |

## Where data comes from / where it can go next

- Both input events are published by `ai-service` — see
  [../flow/ai-service-data-flow.md](ai-service-data-flow.md) for how that data was produced (S3 ->
  Whisper -> pyannote -> `RuleBasedAnalyzer`).
- `english-service` now does produce one Kafka event: `learning.gap.analysis.requested`, published by
  `practice.kafka.AnalysisRequestedProducer` after a redo-exercise submission. `vocabulary.analyzed`/
  `grammar.analyzed`/`pronunciation.analyzed` topic constants still exist with no producer.
- `grammar` and `pronunciation` don't re-ingest `transcript.ready`: the `transcripts`/
  `transcript_segments` tables are written once by `vocabulary`'s consumer and read back by all
  three domains via `GET /api/v1/transcripts/{recordingId}`.
- All four `learning.gap.analyzed` consumers (three domains + `practice`'s seed consumer) share the
  same topic but run on distinct Kafka `groupId`s (`english-service`, `english-service-grammar`,
  `english-service-pronunciation`, `english-service-practice`) so each receives every message rather
  than Kafka splitting partitions across them.
- `practice`'s consumer only *seeds* `mistake_history` (a no-op if the item already has history) —
  the `occurrence_count`/`last_seen_at` values that actually drive re-scoring only change when a
  learner submits `POST /api/v1/practice/redo`, so replaying old `learning.gap.analyzed` messages
  can never inflate a learner's mistake count.
- `learning.gap.analysis.requested`'s consumer (`ai-service`) and the resulting
  `learning.gap.analyzed` republish are documented in
  [../flow/ai-service-data-flow.md](ai-service-data-flow.md) — this file stops at the point the event
  is published, since ai-service's processing of it is unchanged by `practice`'s existence.
- **New in this update:** the redo flow no longer only round-trips through Kafka for a fresh score.
  `WeakPointScoringOrchestratorImpl` computes one directly, in Java, per attempt, via
  `common.scoring.WeakPointScoringEngine` — combining an adaptive-half-life generalization of the
  Ebbinghaus decay, a Bayesian Knowledge Tracing mastery estimate, and a Rasch-style
  population-level difficulty weight, plus a same-batch recurrence boost — and writes the result
  straight into the owning domain's weak-point table with `score_source = JAVA_ENGINE`. The
  ai-service round-trip is kept (unchanged formula, still `occurrence_count x forgetting`) purely so
  `recommendation-service`/`dashboard-service` still learn about the update; a `score_source` guard
  on each domain's `upsert` (`WHERE NOT (existing = JAVA_ENGINE AND incoming = PYTHON_LEGACY)`) stops
  that slower, older-formula write from clobbering the fresher one. Full rationale and formula in
  `Business.md` §10 (both copies) and the class docs under
  `RemeLearning/common/src/main/java/com/remelearning/common/scoring/`.
- **`dictation` (redesigned).** Two sections over one grading flow: a **fixed library** of real
  recorded clips (imported from disk/cloud via `common.storage.StorageClient` into `dictation_clips`,
  tagged skill/level/topic/examType) and **"Luyện nghe với AI"** (Gemini sentences voiced by
  **Supertonic** in ai-service). The request flow is pull-based (FE → bff → REST), but grading
  (`POST /attempts`) now **publishes `learning.gap.analyzed`** so misses reach the existing
  recommendation pipeline — the one point this flow re-enters Kafka. Outbound calls: `StorageClient`
  (local FS/S3) for clip + generated audio, HTTP to ai-service for TTS, and optional Gemini for the
  analysis. Grading itself (`DictationScorer`) is still a pure in-memory function.
- **Rev 2 (sentence-mode dictation).** Adds a folder → file browse path (`GET /dictation/folders` →
  `.../folders/{folderId}/lessons` → `.../clips/{clipId}`) alongside the existing facet/session path,
  backed by a new `folder` column and `dictation_clip_sentences` table populated at import time. The
  grading endpoint (`POST /attempts`) and its published `learning.gap.analyzed` are **unchanged** —
  the FE grades sentence-by-sentence client-side against `sentences[]`, then reassembles the full
  transcript and calls the same attempt endpoint. `sentences[].startMs`/`endMs` are filled in by
  `GetClipDetail`'s lazy AI-alignment step (`AlignSentences` above): `SentenceAlignmentClient`
  (`common.ai.align`) sends the clip's audio + sentence texts to ai-service's Whisper-based
  `POST /api/v1/dictation/align-sentences`, and whatever timings come back are persisted via
  `updateSentenceTimestamps` before the response is built. A sentence Whisper can't locate, or any
  failure reaching ai-service, just leaves that sentence's fields null - retried on the next read of
  the same clip, never fails the request.
- **Rev 3 (sentence-mode gating + mistake aggregation).** The FE's sentence runner now requires an
  explicit correct check before advancing (no auto-advance, no skipping a wrong answer), so
  `userTranscript` reaching `POST /attempts` is always fully correct sentence-by-sentence and its diff
  alone would show zero misses even for a learner who struggled. The FE now also submits
  `sentenceMistakes[]` - every wrong check it recorded along the way - in the same request.
  `DictationServiceImpl` scores each `{expectedText, attemptedText}` pair independently with the same
  `DictationScorer` (`ScoreSentenceMistakes` above) and merges the resulting word-level misses into the
  exact same `insertMisses`/`learning.gap.analyzed` flow a normal wrong `userTranscript` would produce
  - no new table, no new Kafka topic, just an additional input folded into the existing pipeline.
  `DictationAttemptResultDto`'s `accuracy`/`wer`/`diff` are computed only from `userTranscript` and are
  unaffected by `sentenceMistakes`.
- **Rev 4 (per-attempt AI practice + history badge).** A second AI-practice trigger,
  `POST /dictation/history/{userId}/{attemptId}/ai-practice`, scopes the same Gemini-sentences ->
  Supertonic-audio pipeline as `AiGen` to **one specific past attempt's** misses instead of the
  aggregate `missWindow` top-missed-words pool - the "Luyện tập với AI" action on a single history
  row. It shares `AiGen`'s private helpers (`generateFreshPracticeItems`/`synthesizeAudio` were split
  into `createPracticeItems` + `synthesizeAudio` so both flows reuse the persistence/TTS loop).
  Sentences it creates flow back through the unchanged grading endpoint and reappear in history like
  any other AI-practice attempt. Separately, `DictationHistoryEntryDto` now carries `practiceType`
  (`LIBRARY`/`AI_PRACTICE`), computed in Java from `clipId` - no new column - so the FE can badge each
  history row by its origin.
- **Rev 5 (`AiGen` -> one AI-generated dialogue passage instead of N templated sentences).**
  `generateAiPractice` (`AiGen` above) no longer calls `DictationAnalyzer.generatePracticeSentences`
  (that helper - previously named `generateFreshPracticeItems` - is removed; `AiGenFromAttempt` is
  unaffected and still uses it via `createPracticeItems`). It now always asks Gemini
  (`LlmDictationDialogueGenerator`, a new always-active `DictationDialogueGenerator` - not gated by
  `dictation.analyzer.mode`) to write **one** listening-practice passage - a monologue or a
  multi-speaker dialogue - covering all target phrases (the pending items' text, or the learner's
  top-missed words if none are pending) at once. `DictationServiceImpl.assignVoicesToSpeakers` then
  picks one random Supertonic voice per distinct speaker from the fixed 10-voice pool (`F1-F5`/`M1-M5`),
  each line is synthesized individually, and `WavAudioMerger` (new, `dictation.audio` package)
  concatenates the resulting WAV clips into one continuous file. The result replaces whatever
  practice items were previously pending (`deletePracticeItemsWithoutAudio`) with this single new
  one; any failure along the way is logged and swallowed, leaving prior pending items untouched so
  the next call can retry. No schema change: the passage is still stored in the existing
  `dictation_practice_items.sentence_text`/`storage_key` columns.
- **Rev 6 (`GetAiPracticeDetail` -> sentence-mode practice for AI passages, matching the library).**
  New read-only endpoint, no schema change: `splitIntoSentences` splits the already-persisted
  `sentence_text` in memory the moment a learner opens an AI-practice item - one sentence per dialogue
  line (`\n`-separated, matching how `AiGen`/`AiGenFromAttempt` write multi-speaker turns) for a
  dialogue, or by sentence-ending punctuation for a single-speaker monologue. Every sentence's
  `startMs`/`endMs` stays `null` (the passage's audio was already merged into one file by `AiGen`
  before this endpoint ever runs), so the FE falls back to its own word-count-share time estimate -
  the same fallback it already used for a library clip whose `AlignSentences` step hasn't matched a
  sentence yet. This lets the FE drive AI-practice through the identical sentence-by-sentence runner
  it uses for `GetClipDetail`, instead of a single free-text transcript box.
- **Rev 7 (audio/answer-key fix + level/examType/topic facets + unified from-attempt generation +
  translation).** `V11__ai_practice_taxonomy_and_translation.sql` adds `level`/`exam_type`/`topic`/
  `translation_text` to `dictation_practice_items` and `translation` to `dictation_clip_sentences`.
  Five changes: (1) **bug fix** - `synthesizeDialoguePracticeItem` used to synthesize each TTS line
  from the bare dialogue text while persisting the graded/displayed text with a `"Speaker: "` prefix
  for multi-speaker passages, so the audio never actually said the speaker name the learner was
  graded against; both now come from one shared `lineText`. (2) **Topic label** - the LLM assigns a
  short topic string to each generated passage, stored in `topic` and returned on every
  `DictationPracticeItemDto`/`DictationPracticeItemDetailDto`. (3) **Level/exam-type facets with
  "RANDOM"** - `AiGen`'s new `GenerateAiPracticeRequest` body lets the caller pick a concrete `level`/
  `examType`, the literal `"RANDOM"` (`resolveLevel`/`resolveExamType` pick one - level from the fixed
  CEFR pool `A1,A2,B1,B2,C1`, examType from the library's own distinct exam types via
  `findDistinctExamTypes`, falling back to `TOEIC,IELTS,TOEFL,General` if the library has none), or
  leave it unset (LLM's own default, unchanged prior behavior); the resolved value is always echoed
  back on the created item. (4) **Unified from-attempt generation** - `AiGenFromAttempt` now calls the
  same `LlmDictationDialogueGenerator` as `AiGen` (previously it called a separate
  `DictationAnalyzer.generatePracticeSentences`, producing many single-sentence items instead of one
  cohesive passage); the only remaining difference is that it never sets `level`/`examType`. (5)
  **Translation** - both `AiGen`/`AiGenFromAttempt`'s `translationLang` and `GetClipDetail`'s
  `translationLang` query param produce a per-line/per-sentence translation, but only when the value
  is present and not `"en"` (the content's own language); for the library this is lazy-filled via
  `ensureSentencesTranslated` (mirroring `ensureSentencesAligned`'s shape), for AI-practice it's
  generated inline as part of the same Gemini call that writes the passage.
- **New: four "Học & Luyện tập với AI" learn skills (`VocabLearnFlow`/`GrammarLearnFlow`/
  `ListeningLearnFlow`/`SpeakingLearnFlow` above), reusing `PracticeService.redo(...)` instead of a
  new pipeline.** `vocabulary/learn` and `grammar/learn` are structural clones of each other
  (generate an AI practice set targeting the learner's own weak points -> grade a submitted attempt
  with a pure in-memory scorer -> feed each graded word/rule into `PracticeService.redo(...)`).
  `listening` and `speaking` are brand-new top-level domains that follow the same generate/grade/feed
  shape but with heavier generation (Gemini dialogue + Supertonic TTS for `listening`; Gemini sentence
  + one-voice Supertonic sample for `speaking`) and, for `speaking`, LLM-based grading replaced by
  ai-service's wav2vec2 GOP model instead of a pure-Java scorer. All four call
  `practiceService.redo(...)` as a **direct in-process method call**, not a second HTTP round-trip to
  `POST /api/v1/practice/redo` - so they get `mistake_history`, `WeakPointScoringEngine`, and the
  bundled `learning.gap.analysis.requested` re-publish for free, exactly like a manual redo exercise.
  **Contract change - client-side grading:** the generate/`getItem`/`listItems` question payloads now
  ship the correct answer so the FE grades each question locally for instant feedback -
  `VocabQuestionDto`/`GrammarQuestionDto` add `answer` + `translation`, `ListeningQuestionDto` adds
  `answer` + `explanation` (with `answer` null for `OPEN`, which is LLM-graded server-side and must not
  leak). The authoritative score is still produced only by the submit-attempt endpoint - the in-memory
  scorers, `PracticeService.redo`, and the Kafka flow above are unchanged; client-side grading is
  display-only.
- **`category = "listening"` is new and has no dedicated weak-point table.** Unlike `vocabulary`/
  `grammar`/`pronunciation` (each backed by its own `*_weak_points` table and Kafka consumer),
  `LearningCategories.LISTENING` (`RemeLearning/common/.../constants/LearningCategories.java`) has no
  matching branch in `WeakPointDispatcherImpl.dispatch` - a `listening` attempt still updates
  `mistake_history`/`item_difficulty_stats` (category-agnostic) and still surfaces in the learner's
  `mistake_history`-driven review queue and in `learning.gap.analysis.requested`'s `history[]`, but the
  Java-computed weak score itself is discarded (`log.warn("Unknown category ...")`), and `dictation`'s
  `learning.gap.analyzed` republish / ai-service's original forgetting pipeline never emit `"listening"`
  either, so **no Kafka consumer anywhere persists a `listening` weak point today**. Per
  `V14__listening_practice.sql`'s own migration comment, target-word/keyword selection for regenerating
  listening practice is instead read straight from this service's own `listening_attempts`/
  `listening_practice_items` history (`ListeningLearnServiceImpl.resolveTargetKeywords`), the same way
  `dictation` mines its own miss table rather than a shared weak-point table. If a `listening_weak_points`
  table is ever added, `WeakPointDispatcherImpl` would need a new `"listening"` case wired to it.
- **New: `vocabulary.library` (topic word bank + Leitner-lite Section practice), extending
  `VocabLearnFlow` above.** Three transformations distinct from call order (see
  [../sequence/English_service/vocabulary-library.md](../sequence/English_service/vocabulary-library.md)
  for the call-order view):

  ```mermaid
  flowchart LR
      LLMJson["GeneratedLibraryWord<br/>(LLM JSON)"] --> LibRow["vocabulary_library_words row"]
      LibRow --> TtsBytes["synthesized .wav bytes<br/>(TtsClient)"]
      TtsBytes --> StorageKey["storage key<br/>(vocab-library/{topicId}/{wordId}.wav)"]
  ```

  ```mermaid
  flowchart LR
      QState1["queue_state JSON<br/>(SectionQueueEntry[])"] --> Apply["SectionQueue.applyResult"]
      Apply --> QState2["updated queue_state JSON"]
  ```

  ```mermaid
  flowchart LR
      Answers["vocabulary_section_answers rows"] --> Attempts["PracticeAttemptRequest[]<br/>(one per answer, unfiltered)"]
      Attempts --> Engine["WeakPointScoringEngine"]
  ```

  A library word is generated once per topic-under-stock event (Gemini JSON -> a persisted row, then
  a one-time TTS pass to attach playable audio), not once per Section start. A Section's queue state
  is pure in-memory JSON round-tripped through `vocabulary_section_attempts.queue_state_json` on every
  answer - `SectionQueue.applyResult` is a pure function, the DB is only ever a snapshot of its
  before/after state. Grading feeds `PracticeService#redo` exactly like `VocabLearnFlow`'s `VLFeed`,
  except **unfiltered by word** - every `vocabulary_section_answers` row becomes its own
  `PracticeAttemptRequest`, so a word drilled three times in one Section produces three requests
  batched into the same `redo` call, letting `WeakPointScoringEngine`'s same-batch recurrence boost
  see the in-session repetition the whole Section design exists to create.
- **Dictation's published `learning.gap.analyzed` no longer always uses category `"vocabulary"`.**
  `DictationServiceImpl.publishWeakPoints`/`toLearningCategory` now map each missed word's
  `DictationAnalyzer`-assigned root cause - `LEXICON`/`GRAMMAR`/`PHONOLOGY` (the same `errorTable`
  categories `DictationAttemptResultDto` returns) - onto `LearningCategories.VOCABULARY`/`GRAMMAR`/
  `PRONUNCIATION` respectively, so a dictation session's misses now fan out across all three existing
  domain consumers/tables instead of only ever landing in `vocabulary_weak_points`. A word with no
  `errorTable` entry (only possible for a sentence-mode-retry miss, scored via `ScoreSentenceMistakes`
  rather than `DictationAnalyzer`) still defaults to `vocabulary`, preserving the old behavior for that
  one case. This is a pure category-routing change - the published event's shape, the
  `learning.gap.analyzed` topic, and the downstream consumer fan-out are otherwise unchanged.
- **ai-service's pronunciation-scoring stage (`app/pronunciation/`), consumed by `SpeakingLearnFlow`
  above.** Mirrors the style of ai-service's own STT/forgetting-score pipeline documented in
  [ai-service-data-flow.md](ai-service-data-flow.md), but that file does not yet cover this new
  endpoint - summarized here since `speaking`'s `SLScore` step is its only caller today.
  `POST /api/v1/pronunciation/score` (multipart: learner `audio` + `expected_text`, `lang`) runs: (1)
  **G2P** (`app/pronunciation/g2p.py`, `g2p_en` backed) turns `expected_text` into its expected ARPAbet
  phoneme sequence, one `WordPhonemes{word, phones[]}` per word (stress digits stripped - GOP scores
  phone identity, not lexical stress); (2) the audio (already-converted 16kHz mono WAV) is run through
  **`facebook/wav2vec2-lv-60-espeak-cv-ft`** (`app/pronunciation/gop_model.py`, `Wav2Vec2ForCTC`) to get
  per-~20ms-frame log-probabilities over its own phoneme vocabulary (`log_probs[T, V]`) - deliberately
  bypassing `Wav2Vec2Processor`'s phonemizer/espeak-ng-dependent tokenizer, using only the acoustic
  logits plus a raw `vocab.json` id->label map; (3) the **GOP scorer** (`app/pronunciation/gop_scorer.py`,
  a simplified GOP - see its module docstring for the tradeoff vs. textbook forced-alignment GOP)
  greedy-decodes the model's own most-likely phone per frame and collapses repeats into segments, then
  Wagner-Fischer edit-distance-aligns that recognized sequence against the expected phone sequence
  (same algorithm `DictationScorer.java` uses for words, applied to phones); each aligned expected
  phone is scored by the mean posterior *of that expected phone* over its aligned frames (a phone with
  no alignment match scores 0); (4) per-word scores are the mean of their phones' scores, `overall` is
  the mean across all phones, and phones below `WEAK_PHONEME_THRESHOLD` (0.4) are surfaced in
  `weak_phonemes` (deduplicated). Separately (not part of GOP scoring itself), the same Whisper engine
  `/api/v1/upload` uses re-transcribes the audio to a plain word-level `transcript`, so the learner can
  compare what they actually said against what they meant to say. The endpoint's JSON response (`PronunciationScoreResponse` -
  `{overall, words: [{word, score, phonemes: [{ipa, score}]}], transcript, weak_phonemes[]}`) is decoded
  Java-side by `common.ai.pronunciation.aiservice.AiServicePronunciationScoringClient`, the concrete
  `PronunciationScoringClient` `SpeakingLearnServiceImpl` calls.
- **New: `grammar.library` (60-topic catalog + AI theory page/pool, generated once, pass/retry/
  unlock-next-topic progression), crossing `vocabulary.library`'s "generate content once, reuse
  forever" pattern with `grammar.learn`'s question types/scoring.** Unlike `vocabulary_topics`, the
  60-row `grammar_library_topics` catalog is fixed and hand-seeded
  (`V17__grammar_library.sql`) - nothing about the topic list itself is ever AI-generated; only each
  topic's *content* (theory page + 8-10 question pool) is, exactly once, the first time
  `GET .../topics/{topicId}` is called for it. A session snapshots full question content inline
  (`GrammarLibrarySessionQuestion`, not a foreign-key reference) so a `RETRY` session's freshly
  AI-regenerated replacement questions never need to touch the shared, reusable
  `grammar_library_questions` pool - they are one-off, session-scoped content. Finishing a session
  always feeds every question (not deduped) into `PracticeService.redo(...)` exactly like
  `LibFeed` does for vocabulary Sections, and either marks the topic `PASSED` + unlocks the next
  topic by `sequence_order` (a guarded upsert that never regresses a topic already past `LOCKED`), or
  returns a brand-new `RETRY` session covering only the questions still wrong. See
  [../sequence/English_service/grammar-library.md](../sequence/English_service/grammar-library.md)
  for the call-order view.
