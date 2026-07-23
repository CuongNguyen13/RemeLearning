package com.remelearning.english.listening.scoring;

/** Result of {@link OpenAnswerGrader#grade}: a 0..1 partial-credit score plus Vietnamese feedback. */
public record OpenAnswerGrade(double score, String feedback) {
}
