package com.remelearning.english.vocabulary.library.generator;

/** One LLM-generated word before persistence - {@code wordType} is the raw string, parsed by the caller. */
public record GeneratedLibraryWord(String word, String wordType, String meaningVi, String exampleEn) {
}
