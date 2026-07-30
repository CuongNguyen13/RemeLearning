-- Writing Library ("Thư viện" tab of the Luyện viết & Luyện dịch skill): a fixed catalogue of
-- writing/translation prompts, browsable along THREE independent taxonomies rather than the single
-- one every other library uses:
--   'grammar'     - the same 60-topic grammar taxonomy as grammar_library_topics /
--                   listening_library_topics (same codes/names/order, independent ids), each topic
--                   demanding that its structure actually be used
--   'genre'       - 12 real-world text types (email, IELTS task, report, ...)
--   'vocab_theme' - the topic set of vocabulary_topics (V16), for theme-driven writing
-- Progress gating runs INDEPENDENTLY per taxonomy: sequence_order is unique only within a taxonomy,
-- and unlocking the next topic only ever considers topics of the same taxonomy. Structurally this is
-- listening_library_topics + listening_topic_progress (V19) with a taxonomy column added.

CREATE TABLE writing_library_topics (
    id BIGSERIAL PRIMARY KEY,
    -- 'grammar' | 'genre' | 'vocab_theme'. Plain VARCHAR, validated in Java (WritingTaxonomy),
    -- consistent with how every other enum-ish column in this schema is handled.
    taxonomy VARCHAR(20) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    level VARCHAR(16) NOT NULL,
    sequence_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Scoped per taxonomy, not global: the same code may legitimately appear on two axes, and each
    -- axis restarts its ordering at 1.
    UNIQUE (taxonomy, code),
    UNIQUE (taxonomy, sequence_order)
);

-- Axis 1: the 60 grammar topics, copied from V19 so a learner's grammar weak points map straight
-- onto a writing topic of the same name.
INSERT INTO writing_library_topics (taxonomy, code, name, description, level, sequence_order) VALUES
    ('grammar', 'present_simple', 'Present Simple', 'Describes habits, routines, facts and permanent states.', 'beginner', 1),
    ('grammar', 'present_continuous', 'Present Continuous', 'Describes actions happening right now or around the present time.', 'beginner', 2),
    ('grammar', 'past_simple', 'Past Simple', 'Describes completed actions or states at a specific time in the past.', 'beginner', 3),
    ('grammar', 'past_continuous', 'Past Continuous', 'Describes an action in progress at a specific moment in the past.', 'beginner', 4),
    ('grammar', 'simple_future_will', 'Simple Future (will)', 'Expresses future predictions, promises and spontaneous decisions with will.', 'beginner', 5),
    ('grammar', 'going_to_future', 'Future (going to)', 'Expresses planned intentions and predictions based on present evidence.', 'beginner', 6),
    ('grammar', 'present_perfect', 'Present Perfect', 'Connects a past action or state to the present moment.', 'beginner', 7),
    ('grammar', 'present_perfect_continuous', 'Present Perfect Continuous', 'Emphasizes the duration of an action that started in the past and continues now.', 'beginner', 8),
    ('grammar', 'past_perfect', 'Past Perfect', 'Describes an action that finished before another past action.', 'beginner', 9),
    ('grammar', 'past_perfect_continuous', 'Past Perfect Continuous', 'Emphasizes the duration of an action ongoing before another past action.', 'beginner', 10),
    ('grammar', 'future_perfect', 'Future Perfect', 'Describes an action that will be completed before a specific future time.', 'beginner', 11),
    ('grammar', 'future_continuous', 'Future Continuous', 'Describes an action that will be in progress at a specific future time.', 'beginner', 12),
    ('grammar', 'articles_a_an_the', 'Articles: a/an/the', 'Covers the rules for using indefinite and definite articles before nouns.', 'beginner', 13),
    ('grammar', 'plural_nouns', 'Plural Nouns', 'Covers regular and irregular ways to form the plural of nouns.', 'beginner', 14),
    ('grammar', 'countable_uncountable_nouns', 'Countable & Uncountable Nouns', 'Distinguishes nouns that can be counted from those that cannot.', 'beginner', 15),
    ('grammar', 'demonstratives_this_that', 'Demonstratives: this/that/these/those', 'Covers pointing to near and far objects, singular and plural.', 'beginner', 16),
    ('grammar', 'personal_pronouns', 'Personal Pronouns', 'Covers subject and object pronouns replacing nouns.', 'beginner', 17),
    ('grammar', 'possessive_adjectives_pronouns', 'Possessive Adjectives & Pronouns', 'Covers showing ownership with words like my/mine, your/yours.', 'beginner', 18),
    ('grammar', 'there_is_there_are', 'There is / There are', 'Covers stating the existence of something using there is/are.', 'beginner', 19),
    ('grammar', 'prepositions_of_place', 'Prepositions of Place', 'Covers words describing the location of something, like in/on/at.', 'beginner', 20),
    ('grammar', 'prepositions_of_time', 'Prepositions of Time', 'Covers words describing when something happens, like in/on/at.', 'beginner', 21),
    ('grammar', 'basic_conjunctions', 'Basic Conjunctions: and/but/or/so', 'Covers joining words and clauses with basic coordinating conjunctions.', 'beginner', 22),
    ('grammar', 'imperatives', 'Imperatives', 'Covers giving commands, instructions and requests.', 'beginner', 23),
    ('grammar', 'can_could_ability', 'Can/Could - Ability', 'Covers expressing present and past ability with can and could.', 'beginner', 24),
    ('grammar', 'modal_verbs_obligation', 'Modal Verbs: must/have to', 'Covers expressing obligation and necessity.', 'beginner', 25),
    ('grammar', 'comparative_adjectives', 'Comparative Adjectives', 'Covers comparing two things using comparative forms of adjectives.', 'beginner', 26),
    ('grammar', 'superlative_adjectives', 'Superlative Adjectives', 'Covers comparing three or more things using superlative forms of adjectives.', 'beginner', 27),
    ('grammar', 'adverbs_of_frequency', 'Adverbs of Frequency', 'Covers words like always/usually/never describing how often something happens.', 'beginner', 28),
    ('grammar', 'question_words_wh', 'Wh- Question Words', 'Covers forming questions with what/where/when/why/who/how.', 'beginner', 29),
    ('grammar', 'yes_no_questions', 'Yes/No Questions', 'Covers forming questions that are answered with yes or no.', 'beginner', 30),
    ('grammar', 'modal_verbs_advice', 'Modal Verbs: should/ought to', 'Covers giving advice and recommendations.', 'intermediate', 31),
    ('grammar', 'modal_verbs_deduction', 'Modal Verbs: Deduction', 'Covers expressing certainty and possibility about the present or past.', 'intermediate', 32),
    ('grammar', 'passive_voice_present_past', 'Passive Voice: Present & Past', 'Covers forming the passive voice in present and past tenses.', 'intermediate', 33),
    ('grammar', 'passive_voice_other_tenses', 'Passive Voice: Other Tenses', 'Covers forming the passive voice in perfect and future tenses.', 'intermediate', 34),
    ('grammar', 'reported_speech_statements', 'Reported Speech: Statements', 'Covers reporting what someone said without quoting directly.', 'intermediate', 35),
    ('grammar', 'reported_speech_questions', 'Reported Speech: Questions', 'Covers reporting questions someone asked without quoting directly.', 'intermediate', 36),
    ('grammar', 'first_conditional', 'First Conditional', 'Covers real and likely future conditions and their results.', 'intermediate', 37),
    ('grammar', 'second_conditional', 'Second Conditional', 'Covers unreal or hypothetical present/future conditions and their results.', 'intermediate', 38),
    ('grammar', 'third_conditional', 'Third Conditional', 'Covers unreal past conditions and their imagined results.', 'intermediate', 39),
    ('grammar', 'zero_conditional', 'Zero Conditional', 'Covers general truths and facts that are always the result of a condition.', 'intermediate', 40),
    ('grammar', 'relative_clauses_defining', 'Defining Relative Clauses', 'Covers adding essential identifying information about a noun.', 'intermediate', 41),
    ('grammar', 'relative_clauses_non_defining', 'Non-defining Relative Clauses', 'Covers adding extra, non-essential information about a noun.', 'intermediate', 42),
    ('grammar', 'gerunds_and_infinitives', 'Gerunds and Infinitives', 'Covers choosing between the -ing form and the to-infinitive after certain verbs.', 'intermediate', 43),
    ('grammar', 'phrasal_verbs', 'Phrasal Verbs', 'Covers verbs combined with particles that create new meanings.', 'intermediate', 44),
    ('grammar', 'used_to_would', 'Used to / Would', 'Covers describing past habits and states that no longer happen.', 'intermediate', 45),
    ('grammar', 'so_such', 'So / Such', 'Covers intensifying adjectives and nouns with so and such.', 'intermediate', 46),
    ('grammar', 'too_enough', 'Too / Enough', 'Covers expressing excess and sufficiency with too and enough.', 'intermediate', 47),
    ('grammar', 'quantifiers', 'Quantifiers: much/many/few/little', 'Covers expressing quantity with countable and uncountable nouns.', 'intermediate', 48),
    ('grammar', 'question_tags', 'Question Tags', 'Covers short questions added to the end of a statement to confirm information.', 'intermediate', 49),
    ('grammar', 'causative_form', 'Causative Form: have/get something done', 'Covers describing an action arranged to be done by someone else.', 'intermediate', 50),
    ('grammar', 'mixed_conditionals', 'Mixed Conditionals', 'Covers combining different time references between the if-clause and the result clause.', 'advanced', 51),
    ('grammar', 'subjunctive_mood', 'Subjunctive Mood', 'Covers expressing wishes, demands and hypothetical situations grammatically.', 'advanced', 52),
    ('grammar', 'inversion', 'Inversion', 'Covers reversing the normal subject-verb order for emphasis, especially after negative adverbials.', 'advanced', 53),
    ('grammar', 'cleft_sentences', 'Cleft Sentences', 'Covers splitting a sentence into two clauses to emphasize part of it.', 'advanced', 54),
    ('grammar', 'ellipsis', 'Ellipsis', 'Covers omitting words that are understood from context to avoid repetition.', 'advanced', 55),
    ('grammar', 'participle_clauses', 'Participle Clauses', 'Covers using -ing and -ed clauses to shorten and combine sentences.', 'advanced', 56),
    ('grammar', 'reported_speech_advanced', 'Reported Speech: Advanced Structures', 'Covers reporting commands, suggestions and complex tense shifts.', 'advanced', 57),
    ('grammar', 'wish_if_only', 'Wish / If only', 'Covers expressing regrets and hypothetical wishes about the present, past and future.', 'advanced', 58),
    ('grammar', 'emphasis_structures', 'Emphasis Structures', 'Covers structures like do/does/did and what-clauses used to add emphasis.', 'advanced', 59),
    ('grammar', 'discourse_markers', 'Discourse Markers', 'Covers linking words and phrases that organize and connect ideas across sentences.', 'advanced', 60);

-- Axis 2: real-world text types. Ordered easiest-first so the gating chain reads as a curriculum.
INSERT INTO writing_library_topics (taxonomy, code, name, description, level, sequence_order) VALUES
    ('genre', 'personal_message', 'Tin nhắn / email cá nhân', 'Viết tin nhắn, email ngắn cho bạn bè và người thân.', 'beginner', 1),
    ('genre', 'formal_email', 'Email công việc', 'Viết email trang trọng: yêu cầu, xác nhận, trả lời khách hàng.', 'beginner', 2),
    ('genre', 'descriptive_paragraph', 'Đoạn văn miêu tả', 'Miêu tả người, nơi chốn, đồ vật bằng chi tiết cụ thể.', 'beginner', 3),
    ('genre', 'narrative_paragraph', 'Đoạn văn kể chuyện', 'Kể lại một sự việc theo trình tự thời gian.', 'beginner', 4),
    ('genre', 'opinion_essay', 'Bài luận nêu quan điểm', 'Nêu và bảo vệ một quan điểm bằng lý lẽ, ví dụ.', 'intermediate', 5),
    ('genre', 'pros_cons_essay', 'Bài luận lợi ích - hạn chế', 'Trình bày cả hai mặt của một vấn đề rồi kết luận.', 'intermediate', 6),
    ('genre', 'ielts_task1_chart', 'IELTS Writing Task 1 - mô tả biểu đồ', 'Mô tả và so sánh số liệu từ biểu đồ, bảng, sơ đồ.', 'intermediate', 7),
    ('genre', 'ielts_task2_essay', 'IELTS Writing Task 2', 'Bài luận học thuật trả lời trực tiếp một đề bài IELTS.', 'intermediate', 8),
    ('genre', 'complaint_letter', 'Thư khiếu nại', 'Trình bày vấn đề và yêu cầu giải quyết một cách lịch sự nhưng dứt khoát.', 'intermediate', 9),
    ('genre', 'cover_letter', 'Thư xin việc', 'Giới thiệu bản thân và thuyết phục nhà tuyển dụng.', 'intermediate', 10),
    ('genre', 'report', 'Báo cáo ngắn', 'Tóm tắt tình hình, phân tích và đề xuất bằng văn phong khách quan.', 'advanced', 11),
    ('genre', 'argumentative_essay', 'Bài luận tranh luận', 'Phản biện quan điểm đối lập rồi bảo vệ luận điểm của mình.', 'advanced', 12);

-- Axis 3: the vocabulary_topics theme set (V16), same codes, so a theme-driven writing task lines up
-- with the vocabulary the learner has been studying.
INSERT INTO writing_library_topics (taxonomy, code, name, description, level, sequence_order) VALUES
    ('vocab_theme', 'daily-life', 'Daily Life', 'Viết/dịch về sinh hoạt hàng ngày.', 'beginner', 1),
    ('vocab_theme', 'food', 'Food', 'Viết/dịch về ẩm thực, nấu ăn, nhà hàng.', 'beginner', 2),
    ('vocab_theme', 'travel', 'Travel', 'Viết/dịch về du lịch, sân bay, khách sạn, chuyến đi.', 'intermediate', 3),
    ('vocab_theme', 'business', 'Business', 'Viết/dịch về công việc, họp hành, email công sở.', 'intermediate', 4),
    ('vocab_theme', 'technology', 'Technology', 'Viết/dịch về công nghệ, thiết bị, internet.', 'intermediate', 5),
    ('vocab_theme', 'health', 'Health', 'Viết/dịch về sức khỏe, y tế, thể dục.', 'intermediate', 6),
    ('vocab_theme', 'education', 'Education', 'Viết/dịch về học tập, trường lớp, thi cử.', 'intermediate', 7),
    ('vocab_theme', 'environment', 'Environment', 'Viết/dịch về môi trường, thiên nhiên, khí hậu.', 'advanced', 8);

-- A topic's content: a chain of prompts the learner works through in order. Mirrors
-- listening_library_sections, except the content is a writing brief / source passage plus its
-- reference answer instead of a passage plus audio.
CREATE TABLE writing_library_prompts (
    id BIGSERIAL PRIMARY KEY,
    topic_id BIGINT NOT NULL REFERENCES writing_library_topics(id),
    task_type VARCHAR(24) NOT NULL,
    prompt_text TEXT NOT NULL,
    -- Model answer / reference translation, used only for grading; never sent to the client before
    -- the learner submits.
    reference_answer TEXT,
    min_words INT,
    explanation TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Persisted topic-gating state machine, structurally identical to listening_topic_progress. The
-- taxonomy a row belongs to is reached through topic_id, so no extra column is needed here.
CREATE TABLE writing_topic_progress (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    topic_id BIGINT NOT NULL REFERENCES writing_library_topics(id),
    status VARCHAR(20) NOT NULL,
    unlocked_at TIMESTAMPTZ,
    passed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, topic_id)
);

-- One graded attempt at a library prompt. Carries the same grader-output columns as writing_attempts
-- (V26) so the result panel renders identically for both tabs, and so the retry action can read
-- errors from either source.
CREATE TABLE writing_library_attempts (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    prompt_id BIGINT NOT NULL REFERENCES writing_library_prompts(id),
    submitted_text TEXT NOT NULL,
    corrected_text TEXT,
    score DOUBLE PRECISION NOT NULL,
    criteria TEXT NOT NULL,
    errors TEXT NOT NULL,
    feedback TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_writing_library_topics_taxonomy ON writing_library_topics(taxonomy, sequence_order);
CREATE INDEX idx_writing_library_prompts_topic ON writing_library_prompts(topic_id);
CREATE INDEX idx_writing_topic_progress_user ON writing_topic_progress(user_id);
CREATE INDEX idx_writing_library_attempts_user ON writing_library_attempts(user_id);
CREATE INDEX idx_writing_library_attempts_prompt ON writing_library_attempts(prompt_id);
