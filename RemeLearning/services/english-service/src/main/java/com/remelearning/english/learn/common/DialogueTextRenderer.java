package com.remelearning.english.learn.common;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders a passage's {@link DialogueLine}s into its flat transcript/translation text, without
 * touching TTS.
 *
 * <p>Extracted from {@link DialogueAudioSynthesizer} (which still uses it) because listening
 * practice now persists a passage's text at generation time while deferring its audio to the first
 * play - so the two steps can no longer share one method that does both.
 */
public final class DialogueTextRenderer {

	private DialogueTextRenderer() {
	}

	/** Joins the lines in order; speaker labels are prefixed only when more than one speaker occurs. */
	public static DialogueText render(List<DialogueLine> lines) {
		boolean multiSpeaker = distinctSpeakerCount(lines) > 1;
		StringBuilder transcriptText = new StringBuilder();
		StringBuilder translationText = new StringBuilder();
		boolean anyTranslation = false;

		for (DialogueLine line : lines) {
			if (!transcriptText.isEmpty()) {
				transcriptText.append('\n');
				translationText.append('\n');
			}
			transcriptText.append(renderLine(line.speaker(), line.text(), multiSpeaker));
			if (line.translation() != null) {
				anyTranslation = true;
				translationText.append(renderLine(line.speaker(), line.translation(), multiSpeaker));
			}
		}
		return new DialogueText(transcriptText.toString(), anyTranslation ? translationText.toString() : null);
	}

	/**
	 * One line's display text, speaker-prefixed only in a multi-speaker passage. Used to build the
	 * persisted transcript/translation - NOT for TTS input, since speaker labels must never be spoken
	 * aloud (see {@link DialogueAudioSynthesizer#synthesize}).
	 */
	public static String renderLine(String speaker, String text, boolean multiSpeaker) {
		return multiSpeaker ? speaker + ": " + text : text;
	}

	/** How many distinct speaker labels the passage uses - 1 for a monologue. */
	public static int distinctSpeakerCount(List<DialogueLine> lines) {
		Set<String> speakers = new HashSet<>();
		for (DialogueLine line : lines) {
			speakers.add(line.speaker());
		}
		return speakers.size();
	}
}
