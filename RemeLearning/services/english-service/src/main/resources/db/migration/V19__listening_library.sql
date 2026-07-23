-- Listening Library: fixed 60-topic taxonomy mirroring grammar_library_topics' topic set (same
-- names/order, independent ids/table), each topic backed by one or more listening sections (a
-- passage + audio) with a reusable multiple-choice question pool, gated per learner via a
-- persisted topic-progress state machine identical in shape to grammar_topic_progress.

CREATE TABLE listening_library_topics (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    level VARCHAR(16) NOT NULL,
    sequence_order INT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO listening_library_topics (code, name, description, level, sequence_order) VALUES
    ('present_simple', 'Present Simple', 'Describes habits, routines, facts and permanent states.', 'beginner', 1),
    ('present_continuous', 'Present Continuous', 'Describes actions happening right now or around the present time.', 'beginner', 2),
    ('past_simple', 'Past Simple', 'Describes completed actions or states at a specific time in the past.', 'beginner', 3),
    ('past_continuous', 'Past Continuous', 'Describes an action in progress at a specific moment in the past.', 'beginner', 4),
    ('simple_future_will', 'Simple Future (will)', 'Expresses future predictions, promises and spontaneous decisions with will.', 'beginner', 5),
    ('going_to_future', 'Future (going to)', 'Expresses planned intentions and predictions based on present evidence.', 'beginner', 6),
    ('present_perfect', 'Present Perfect', 'Connects a past action or state to the present moment.', 'beginner', 7),
    ('present_perfect_continuous', 'Present Perfect Continuous', 'Emphasizes the duration of an action that started in the past and continues now.', 'beginner', 8),
    ('past_perfect', 'Past Perfect', 'Describes an action that finished before another past action.', 'beginner', 9),
    ('past_perfect_continuous', 'Past Perfect Continuous', 'Emphasizes the duration of an action ongoing before another past action.', 'beginner', 10),
    ('future_perfect', 'Future Perfect', 'Describes an action that will be completed before a specific future time.', 'beginner', 11),
    ('future_continuous', 'Future Continuous', 'Describes an action that will be in progress at a specific future time.', 'beginner', 12),
    ('articles_a_an_the', 'Articles: a/an/the', 'Covers the rules for using indefinite and definite articles before nouns.', 'beginner', 13),
    ('plural_nouns', 'Plural Nouns', 'Covers regular and irregular ways to form the plural of nouns.', 'beginner', 14),
    ('countable_uncountable_nouns', 'Countable & Uncountable Nouns', 'Distinguishes nouns that can be counted from those that cannot.', 'beginner', 15),
    ('demonstratives_this_that', 'Demonstratives: this/that/these/those', 'Covers pointing to near and far objects, singular and plural.', 'beginner', 16),
    ('personal_pronouns', 'Personal Pronouns', 'Covers subject and object pronouns replacing nouns.', 'beginner', 17),
    ('possessive_adjectives_pronouns', 'Possessive Adjectives & Pronouns', 'Covers showing ownership with words like my/mine, your/yours.', 'beginner', 18),
    ('there_is_there_are', 'There is / There are', 'Covers stating the existence of something using there is/are.', 'beginner', 19),
    ('prepositions_of_place', 'Prepositions of Place', 'Covers words describing the location of something, like in/on/at.', 'beginner', 20),
    ('prepositions_of_time', 'Prepositions of Time', 'Covers words describing when something happens, like in/on/at.', 'beginner', 21),
    ('basic_conjunctions', 'Basic Conjunctions: and/but/or/so', 'Covers joining words and clauses with basic coordinating conjunctions.', 'beginner', 22),
    ('imperatives', 'Imperatives', 'Covers giving commands, instructions and requests.', 'beginner', 23),
    ('can_could_ability', 'Can/Could - Ability', 'Covers expressing present and past ability with can and could.', 'beginner', 24),
    ('modal_verbs_obligation', 'Modal Verbs: must/have to', 'Covers expressing obligation and necessity.', 'beginner', 25),
    ('comparative_adjectives', 'Comparative Adjectives', 'Covers comparing two things using comparative forms of adjectives.', 'beginner', 26),
    ('superlative_adjectives', 'Superlative Adjectives', 'Covers comparing three or more things using superlative forms of adjectives.', 'beginner', 27),
    ('adverbs_of_frequency', 'Adverbs of Frequency', 'Covers words like always/usually/never describing how often something happens.', 'beginner', 28),
    ('question_words_wh', 'Wh- Question Words', 'Covers forming questions with what/where/when/why/who/how.', 'beginner', 29),
    ('yes_no_questions', 'Yes/No Questions', 'Covers forming questions that are answered with yes or no.', 'beginner', 30),
    ('modal_verbs_advice', 'Modal Verbs: should/ought to', 'Covers giving advice and recommendations.', 'intermediate', 31),
    ('modal_verbs_deduction', 'Modal Verbs: Deduction', 'Covers expressing certainty and possibility about the present or past.', 'intermediate', 32),
    ('passive_voice_present_past', 'Passive Voice: Present & Past', 'Covers forming the passive voice in present and past tenses.', 'intermediate', 33),
    ('passive_voice_other_tenses', 'Passive Voice: Other Tenses', 'Covers forming the passive voice in perfect and future tenses.', 'intermediate', 34),
    ('reported_speech_statements', 'Reported Speech: Statements', 'Covers reporting what someone said without quoting directly.', 'intermediate', 35),
    ('reported_speech_questions', 'Reported Speech: Questions', 'Covers reporting questions someone asked without quoting directly.', 'intermediate', 36),
    ('first_conditional', 'First Conditional', 'Covers real and likely future conditions and their results.', 'intermediate', 37),
    ('second_conditional', 'Second Conditional', 'Covers unreal or hypothetical present/future conditions and their results.', 'intermediate', 38),
    ('third_conditional', 'Third Conditional', 'Covers unreal past conditions and their imagined results.', 'intermediate', 39),
    ('zero_conditional', 'Zero Conditional', 'Covers general truths and facts that are always the result of a condition.', 'intermediate', 40),
    ('relative_clauses_defining', 'Defining Relative Clauses', 'Covers adding essential identifying information about a noun.', 'intermediate', 41),
    ('relative_clauses_non_defining', 'Non-defining Relative Clauses', 'Covers adding extra, non-essential information about a noun.', 'intermediate', 42),
    ('gerunds_and_infinitives', 'Gerunds and Infinitives', 'Covers choosing between the -ing form and the to-infinitive after certain verbs.', 'intermediate', 43),
    ('phrasal_verbs', 'Phrasal Verbs', 'Covers verbs combined with particles that create new meanings.', 'intermediate', 44),
    ('used_to_would', 'Used to / Would', 'Covers describing past habits and states that no longer happen.', 'intermediate', 45),
    ('so_such', 'So / Such', 'Covers intensifying adjectives and nouns with so and such.', 'intermediate', 46),
    ('too_enough', 'Too / Enough', 'Covers expressing excess and sufficiency with too and enough.', 'intermediate', 47),
    ('quantifiers', 'Quantifiers: much/many/few/little', 'Covers expressing quantity with countable and uncountable nouns.', 'intermediate', 48),
    ('question_tags', 'Question Tags', 'Covers short questions added to the end of a statement to confirm information.', 'intermediate', 49),
    ('causative_form', 'Causative Form: have/get something done', 'Covers describing an action arranged to be done by someone else.', 'intermediate', 50),
    ('mixed_conditionals', 'Mixed Conditionals', 'Covers combining different time references between the if-clause and the result clause.', 'advanced', 51),
    ('subjunctive_mood', 'Subjunctive Mood', 'Covers expressing wishes, demands and hypothetical situations grammatically.', 'advanced', 52),
    ('inversion', 'Inversion', 'Covers reversing the normal subject-verb order for emphasis, especially after negative adverbials.', 'advanced', 53),
    ('cleft_sentences', 'Cleft Sentences', 'Covers splitting a sentence into two clauses to emphasize part of it.', 'advanced', 54),
    ('ellipsis', 'Ellipsis', 'Covers omitting words that are understood from context to avoid repetition.', 'advanced', 55),
    ('participle_clauses', 'Participle Clauses', 'Covers using -ing and -ed clauses to shorten and combine sentences.', 'advanced', 56),
    ('reported_speech_advanced', 'Reported Speech: Advanced Structures', 'Covers reporting commands, suggestions and complex tense shifts.', 'advanced', 57),
    ('wish_if_only', 'Wish / If only', 'Covers expressing regrets and hypothetical wishes about the present, past and future.', 'advanced', 58),
    ('emphasis_structures', 'Emphasis Structures', 'Covers structures like do/does/did and what-clauses used to add emphasis.', 'advanced', 59),
    ('discourse_markers', 'Discourse Markers', 'Covers linking words and phrases that organize and connect ideas across sentences.', 'advanced', 60);

-- A topic's listening content: one or more sections, each a passage + optional audio, backed by
-- its own multiple-choice question pool (mirrors grammar_library_questions' shape).
CREATE TABLE listening_library_sections (
    id BIGSERIAL PRIMARY KEY,
    topic_id BIGINT NOT NULL REFERENCES listening_library_topics(id),
    passage_text TEXT NOT NULL,
    audio_storage_key VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE listening_library_questions (
    id BIGSERIAL PRIMARY KEY,
    section_id BIGINT NOT NULL REFERENCES listening_library_sections(id),
    question_text TEXT NOT NULL,
    options_json TEXT NOT NULL,
    correct_option VARCHAR(8) NOT NULL,
    explanation TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Persisted topic-gating state machine, structurally identical to grammar_topic_progress (see
-- GrammarTopicProgressMapper.xml): status is a plain VARCHAR (the enum mapping/validation lives in
-- Java, not the schema), and (user_id, topic_id) is unique so the mapper's ON CONFLICT upserts work.
CREATE TABLE listening_topic_progress (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    topic_id BIGINT NOT NULL REFERENCES listening_library_topics(id),
    status VARCHAR(20) NOT NULL,
    unlocked_at TIMESTAMPTZ,
    passed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, topic_id)
);

CREATE INDEX idx_listening_topic_progress_user_id ON listening_topic_progress(user_id);

CREATE TABLE listening_library_attempts (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    section_id BIGINT NOT NULL REFERENCES listening_library_sections(id),
    score DOUBLE PRECISION NOT NULL,
    correct_count INT NOT NULL,
    total_questions INT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_listening_library_sections_topic ON listening_library_sections(topic_id);
CREATE INDEX idx_listening_library_questions_section ON listening_library_questions(section_id);
CREATE INDEX idx_listening_library_attempts_user ON listening_library_attempts(user_id);
