package com.remelearning.english.learn.common;

/** Result of {@link DialogueAudioSynthesizer#synthesize} - merged audio plus the rendered text. */
public record SynthesizedDialogue(byte[] audioBytes, String transcriptText, String translationText) {
}
