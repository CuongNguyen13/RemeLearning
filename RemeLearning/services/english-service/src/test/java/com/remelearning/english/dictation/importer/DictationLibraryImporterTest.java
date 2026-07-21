package com.remelearning.english.dictation.importer;

import com.remelearning.common.storage.StorageClient;
import com.remelearning.english.dictation.domain.DictationClip;
import com.remelearning.english.dictation.mapper.DictationMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DictationLibraryImporterTest {

	private final StorageClient storageClient = mock(StorageClient.class);
	private final DictationMapper dictationMapper = mock(DictationMapper.class);
	private final DictationLibraryImporter importer = new DictationLibraryImporter(storageClient, dictationMapper);

	// Derives exam-type from the top folder and a humanized topic/title from the filename for a flat clip.
	@Test
	void importsFlatClipDerivingExamTypeAndTopicFromNaming() {
		String audioKey = "english-conversations/english-conversations-0001-1-at-home-1.mp3";
		String scriptKey = "english-conversations/scripts/english-conversations-0001-1-at-home-1.txt";
		when(storageClient.list("")).thenReturn(List.of(audioKey));
		when(storageClient.exists(scriptKey)).thenReturn(true);
		when(storageClient.read(scriptKey)).thenReturn(script("Where is Jane?\nShe is in the living room."));

		importer.run(null);

		DictationClip clip = captureUpsertedClip();
		assertThat(clip.getCode()).isEqualTo("english-conversations-0001-1-at-home-1");
		assertThat(clip.getExamType()).isEqualTo("English conversations");
		assertThat(clip.getTopic()).isEqualTo("At home 1");
		assertThat(clip.getSkill()).isEqualTo("Listening");
		assertThat(clip.getLevel()).isNull();
		assertThat(clip.getStorageKey()).isEqualTo(audioKey);
		assertThat(clip.getScriptText()).contains("Where is Jane?");
	}

	// Splits the script into one sentence per non-blank line and upserts each keyed by (clipId, seq),
	// leaving out blank lines entirely.
	@Test
	void importsSentencesSkippingBlankLines() {
		String audioKey = "english-conversations/english-conversations-0001-1-at-home-1.mp3";
		String scriptKey = "english-conversations/scripts/english-conversations-0001-1-at-home-1.txt";
		when(storageClient.list("")).thenReturn(List.of(audioKey));
		when(storageClient.exists(scriptKey)).thenReturn(true);
		when(storageClient.read(scriptKey)).thenReturn(script("Where is Jane?\n\nShe is in the living room.\n"));
		doAnswer(invocation -> {
			DictationClip clip = invocation.getArgument(0);
			clip.setId(9L);
			return null;
		}).when(dictationMapper).upsertClip(any());

		importer.run(null);

		verify(dictationMapper).upsertSentence(9L, 1, "Where is Jane?");
		verify(dictationMapper).upsertSentence(9L, 2, "She is in the living room.");
		verify(dictationMapper, never()).upsertSentence(eq(9L), eq(3), anyString());
	}

	// The direct parent folder of the audio file becomes the clip's folder, for folder->file browsing.
	@Test
	void derivesFolderFromDirectParentDirectory() {
		String flatAudioKey = "english-conversations/english-conversations-0001-1-at-home-1.mp3";
		String flatScriptKey = "english-conversations/scripts/english-conversations-0001-1-at-home-1.txt";
		String nestedAudioKey = "toeic/B1/Speaking/toeic-0009-a-topic.mp3";
		String nestedScriptKey = "toeic/B1/Speaking/scripts/toeic-0009-a-topic.txt";
		when(storageClient.list("")).thenReturn(List.of(flatAudioKey, nestedAudioKey));
		when(storageClient.exists(flatScriptKey)).thenReturn(true);
		when(storageClient.read(flatScriptKey)).thenReturn(script("Hi."));
		when(storageClient.exists(nestedScriptKey)).thenReturn(true);
		when(storageClient.read(nestedScriptKey)).thenReturn(script("Hello."));

		importer.run(null);

		ArgumentCaptor<DictationClip> captor = ArgumentCaptor.forClass(DictationClip.class);
		verify(dictationMapper, times(2)).upsertClip(captor.capture());
		assertThat(captor.getAllValues())
				.extracting(DictationClip::getFolder)
				.containsExactly("english-conversations", "Speaking");
	}

	// Uses the folder-path convention <examType>/<level>/<skill>/... to tag level and skill.
	@Test
	void importsClipUsingFolderConventionForLevelAndSkill() {
		String audioKey = "toeic/B1/Speaking/toeic-0009-a-topic.mp3";
		String scriptKey = "toeic/B1/Speaking/scripts/toeic-0009-a-topic.txt";
		when(storageClient.list("")).thenReturn(List.of(audioKey));
		when(storageClient.exists(scriptKey)).thenReturn(true);
		when(storageClient.read(scriptKey)).thenReturn(script("Hello."));

		importer.run(null);

		DictationClip clip = captureUpsertedClip();
		assertThat(clip.getExamType()).isEqualTo("TOEIC");
		assertThat(clip.getLevel()).isEqualTo("B1");
		assertThat(clip.getSkill()).isEqualTo("Speaking");
	}

	// A clip whose script file is missing is skipped without upserting.
	@Test
	void skipsClipWithoutScript() {
		String audioKey = "toeic/toeic-0001-conversation-1.mp3";
		when(storageClient.list("")).thenReturn(List.of(audioKey));
		when(storageClient.exists(anyString())).thenReturn(false);

		importer.run(null);

		verify(dictationMapper, never()).upsertClip(any());
	}

	// Non-audio keys (e.g. the .txt scripts themselves) are ignored during the scan.
	@Test
	void ignoresNonAudioKeys() {
		when(storageClient.list("")).thenReturn(List.of("english-conversations/scripts/a.txt"));

		importer.run(null);

		verify(dictationMapper, never()).upsertClip(any());
	}

	private DictationClip captureUpsertedClip() {
		ArgumentCaptor<DictationClip> captor = ArgumentCaptor.forClass(DictationClip.class);
		verify(dictationMapper).upsertClip(captor.capture());
		return captor.getValue();
	}

	private ByteArrayInputStream script(String text) {
		return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
	}
}
