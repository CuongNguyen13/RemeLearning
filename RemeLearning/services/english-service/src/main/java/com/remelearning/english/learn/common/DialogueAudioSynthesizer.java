package com.remelearning.english.learn.common;

import com.remelearning.common.ai.audio.AudioTranscodeClient;
import com.remelearning.common.ai.tts.TtsAudio;
import com.remelearning.common.ai.tts.TtsClient;
import com.remelearning.common.ai.tts.TtsRequest;
import com.remelearning.english.dictation.audio.WavAudioMerger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
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
 * (deterministic within a call, varied across calls), each line synthesized individually, merged
 * via {@link WavAudioMerger}, then transcoded to Opus - callers store {@link SynthesizedDialogue}'s
 * {@code audioBytes} verbatim under a {@code .opus} key.
 */
@Component
@RequiredArgsConstructor
public class DialogueAudioSynthesizer {

	// The ten Supertonic preset voices (ai-service's SUPPORTED_VOICES).
	private static final List<String> VOICE_POOL = List.of("F1", "F2", "F3", "F4", "F5", "M1", "M2", "M3", "M4", "M5");

	private final TtsClient ttsClient;
	private final AudioTranscodeClient audioTranscodeClient;

	/**
	 * @param lines  the passage's lines in order; must not be empty
	 * @param ttsLang the TTS language code (e.g. "en")
	 */
	public SynthesizedDialogue synthesize(List<DialogueLine> lines, String ttsLang) {
		Map<String, String> speakerVoices = assignVoicesToSpeakers(lines);
		List<byte[]> clips = new ArrayList<>();

		for (DialogueLine line : lines) {
			// Speaker labels are shown in the transcript (see DialogueTextRenderer) but must never be
			// spoken by TTS - only the line's own text is synthesized.
			TtsAudio audio = ttsClient.synthesize(TtsRequest.builder()
					.text(line.text()).languageCode(ttsLang).voice(speakerVoices.get(line.speaker())).build());
			clips.add(audio.getAudioBytes());
		}

		// Same transcript/translation rendering a caller gets from DialogueTextRenderer without
		// synthesizing (listening practice persists the text at generation time, the audio only on
		// first play) - kept in one place so the two paths can never drift apart.
		DialogueText text = DialogueTextRenderer.render(lines);
		byte[] mergedAudio = WavAudioMerger.merge(clips);
		byte[] opusAudio = audioTranscodeClient.toOpus(new ByteArrayInputStream(mergedAudio), "dialogue.wav");
		return new SynthesizedDialogue(opusAudio, text.transcriptText(), text.translationText());
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
