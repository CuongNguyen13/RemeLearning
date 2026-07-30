package com.remelearning.english.writing.generator;

/**
 * One generated writing/translation prompt, before it is persisted.
 *
 * @param topic           short topic label for history/listing
 * @param promptText      the brief (COMPOSE) or source passage (TRANSLATE_*), including its
 *                        Vietnamese instruction line
 * @param referenceAnswer model answer / reference translation, used only for grading
 */
public record GeneratedWritingPractice(String topic, String promptText, String referenceAnswer) {
}
