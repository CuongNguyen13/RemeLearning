package com.remelearning.english.speaking.generator;

/** Result of {@link SpeakingPracticeGenerator#generate} - not yet synthesized to sample audio. */
public record GeneratedSpeakingPractice(String topic, String targetText, String translation) {
}
