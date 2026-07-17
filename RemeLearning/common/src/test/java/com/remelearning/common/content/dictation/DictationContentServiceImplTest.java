package com.remelearning.common.content.dictation;

import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.storage.drive.GoogleDriveClient;
import com.remelearning.common.storage.fallback.FallbackDirectoryLister;
import com.remelearning.common.storage.fallback.FallbackFileReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DictationContentServiceImplTest {

	private final FallbackFileReader fileReader = mock(FallbackFileReader.class);
	private final FallbackDirectoryLister directoryLister = mock(FallbackDirectoryLister.class);
	private final GoogleDriveClient driveClient = mock(GoogleDriveClient.class);
	private final DictationContentProperties properties = new DictationContentProperties();
	private final DictationContentServiceImpl service =
			new DictationContentServiceImpl(fileReader, directoryLister, driveClient, properties);

	@BeforeEach
	void setUp() {
		properties.getLocal().setRootPath("/data/dictation");
	}

	@Test
	void listTopicsMapsFolderNamesFromDirectoryLister() {
		when(directoryLister.listFolders(null, "", null, "/data/dictation"))
				.thenReturn(List.of("english-conversations", "business-english"));

		List<DictationTopic> topics = service.listTopics();

		assertThat(topics).extracting(DictationTopic::id)
				.containsExactly("english-conversations", "business-english");
	}

	@Test
	void listLessonsFiltersToConfiguredAudioExtension() {
		when(directoryLister.listFiles(null, "english-conversations", null, "/data/dictation/english-conversations"))
				.thenReturn(List.of("lesson-1.mp3", "lesson-2.mp3", "scripts"));

		List<DictationLessonSummary> lessons = service.listLessons("english-conversations");

		assertThat(lessons).extracting(DictationLessonSummary::id)
				.containsExactly("lesson-1", "lesson-2");
	}

	@Test
	void listLessonsNeverCallsGoogleDriveWhenNoRootFolderIdConfigured() {
		when(directoryLister.listFiles(null, "english-conversations", null, "/data/dictation/english-conversations"))
				.thenReturn(List.of());

		service.listLessons("english-conversations");

		verify(driveClient, never()).listChildren(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void getLessonReadsAudioAndSplitsScriptIntoSentences() {
		when(fileReader.readFile(null, "english-conversations/lesson-1.mp3", null,
				"/data/dictation/english-conversations/lesson-1.mp3"))
				.thenReturn(new byte[] {1, 2, 3});
		when(fileReader.readFile(null, "english-conversations/scripts/lesson-1.txt", null,
				"/data/dictation/english-conversations/scripts/lesson-1.txt"))
				.thenReturn("Where is Jane?\nShe is in the living room.\n\n".getBytes(StandardCharsets.UTF_8));

		DictationLesson lesson = service.getLesson("english-conversations", "lesson-1");

		assertThat(lesson.audio()).containsExactly(1, 2, 3);
		assertThat(lesson.sentences()).containsExactly("Where is Jane?", "She is in the living room.");
	}

	@Test
	void getLessonRejectsBlankIds() {
		assertThatThrownBy(() -> service.getLesson(" ", "lesson-1")).isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> service.getLesson("topic", " ")).isInstanceOf(BusinessException.class);
	}

	@Test
	void listLessonsRejectsBlankTopicId() {
		assertThatThrownBy(() -> service.listLessons(" ")).isInstanceOf(BusinessException.class);
	}
}
