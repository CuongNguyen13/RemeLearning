package com.remelearning.english.learn.common;

import com.remelearning.common.ai.tts.TtsAudio;
import com.remelearning.common.ai.tts.TtsClient;
import com.remelearning.common.ai.tts.TtsRequest;
import com.remelearning.english.dictation.audio.WavAudioMerger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Synthesizes a listening passage (monologue or multi-speaker dialogue) into one continuous audio
 * file, for any "learn" skill that needs Supertonic-voiced content (currently listening; dictation
 * keeps its own equivalent, already-tested {@code synthesizeDialoguePracticeItem} rather than being
 * migrated onto this shared version - see {@code common.event.LearningGapPublisher}'s Javadoc for
 * the same "don't touch a working path" rationale). One random voice per distinct speaker
 * (deterministic within a call, varied across calls), each line synthesized individually and
 * merged via {@link WavAudioMerger}.
 */
@Component
@RequiredArgsConstructor
public class DialogueAudioSynthesizer {

	// The ten Supertonic preset voices (ai-service's SUPPORTED_VOICES).
	private static final List<String> VOICE_POOL = List.of("F1", "F2", "F3", "F4", "F5", "M1", "M2", "M3", "M4", "M5");

	private final TtsClient ttsClient;

	/**
	 * @param lines  the passage's lines in order; must not be empty
	 * @param ttsLang the TTS language code (e.g. "en")
	 */
	public SynthesizedDialogue synthesize(List<DialogueLine> lines, String ttsLang) {
		Map<String, String> speakerVoices = assignVoicesToSpeakers(lines);
		boolean multiSpeaker = speakerVoices.size() > 1;
		List<byte[]> clips = new ArrayList<>();
		StringBuilder transcriptText = new StringBuilder();
		StringBuilder translationText = new StringBuilder();
		boolean anyTranslation = false;

		for (DialogueLine line : lines) {
			String lineText = multiSpeaker ? line.speaker() + ": " + line.text() : line.text();
			TtsAudio audio = ttsClient.synthesize(TtsRequest.builder()
					.text(lineText).languageCode(ttsLang).voice(speakerVoices.get(line.speaker())).build());
			clips.add(audio.getAudioBytes());

			if (!transcriptText.isEmpty()) {
				transcriptText.append('\n');
				translationText.append('\n');
			}
			transcriptText.append(lineText);
			if (line.translation() != null) {
				anyTranslation = true;
				translationText.append(multiSpeaker ? line.speaker() + ": " + line.translation() : line.translation());
			}
		}

		byte[] mergedAudio = WavAudioMerger.merge(clips);
		return new SynthesizedDialogue(mergedAudio, transcriptText.toString(), anyTranslation ? translationText.toString() : null);
	}

	// Picks one random Supertonic voice preset per distinct speaker, without repeats until the
	// ten-voice pool is exhausted (then wraps around); a monologue simply gets one random voice.
	private Map<String, String> assignVoicesToSpeakers(List<DialogueLine> lines) {
		List<String> shuffledPool = new ArrayList<>(VOICE_POOL);
		Collections.shuffle(shuffledPool);
		Map<String, String> speakerVoices = new LinkedHashMap<>();
		for (DialogueLine line : lines) {
			speakerVoices.computeIfAbsent(line.speaker(), speaker -> shuffledPool.get(speakerVoices.size() % shuffledPool.size()));
		}
		return speakerVoices;
	}
}
