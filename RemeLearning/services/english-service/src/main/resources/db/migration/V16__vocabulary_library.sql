-- Thư viện từ vựng theo chủ đề + luyện tập theo Section (Leitner-lite trong phiên). Mở rộng
-- english.vocabulary.learn: từ vựng cố định theo chủ đề (mới) chia sẻ mastery với
-- vocabulary_weak_points qua cùng quy ước item_id = "vocab:" + word (không có bảng mastery riêng).

CREATE TABLE vocabulary_topics (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(60) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    level VARCHAR(10),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO vocabulary_topics (code, name, description, level) VALUES
    ('travel', 'Travel', 'Từ vựng về du lịch, sân bay, khách sạn, chuyến đi.', 'B1'),
    ('business', 'Business', 'Từ vựng về công việc, họp hành, email công sở.', 'B1'),
    ('daily-life', 'Daily Life', 'Từ vựng sinh hoạt hàng ngày.', 'A2'),
    ('food', 'Food', 'Từ vựng về ẩm thực, nấu ăn, nhà hàng.', 'A2'),
    ('technology', 'Technology', 'Từ vựng về công nghệ, thiết bị, internet.', 'B1'),
    ('health', 'Health', 'Từ vựng về sức khỏe, y tế, thể dục.', 'B1'),
    ('education', 'Education', 'Từ vựng về học tập, trường lớp, thi cử.', 'B1'),
    ('environment', 'Environment', 'Từ vựng về môi trường, thiên nhiên, khí hậu.', 'B2');

CREATE TABLE vocabulary_library_words (
    id BIGSERIAL PRIMARY KEY,
    topic_id BIGINT NOT NULL REFERENCES vocabulary_topics (id),
    word VARCHAR(200) NOT NULL,
    word_type VARCHAR(20) NOT NULL,
    meaning_vi VARCHAR(500) NOT NULL,
    example_en VARCHAR(500) NOT NULL,
    ipa VARCHAR(100),
    audio_storage_key VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_vocabulary_library_words_topic_id ON vocabulary_library_words (topic_id);

CREATE TABLE vocabulary_section_attempts (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    topic_id BIGINT NOT NULL REFERENCES vocabulary_topics (id),
    status VARCHAR(20) NOT NULL,
    section_size INT NOT NULL,
    library_word_ids TEXT NOT NULL,
    queue_state TEXT NOT NULL,
    correct_count INT NOT NULL DEFAULT 0,
    total_answers INT NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_vocabulary_section_attempts_user_id ON vocabulary_section_attempts (user_id);

CREATE TABLE vocabulary_section_answers (
    id BIGSERIAL PRIMARY KEY,
    section_attempt_id BIGINT NOT NULL REFERENCES vocabulary_section_attempts (id),
    library_word_id BIGINT NOT NULL REFERENCES vocabulary_library_words (id),
    exercise_type VARCHAR(30) NOT NULL,
    submitted_answer VARCHAR(500),
    score DOUBLE PRECISION NOT NULL,
    correct BOOLEAN NOT NULL,
    answered_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_vocabulary_section_answers_attempt_id ON vocabulary_section_answers (section_attempt_id);
