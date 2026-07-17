package com.remelearning.common.content.dictation;

import java.util.List;

/**
 * Read-only access to the dictation content library: one folder per topic, one audio+script file
 * pair per lesson. Backed by {@link com.remelearning.common.storage.fallback.FallbackFileReader}
 * and {@link com.remelearning.common.storage.fallback.FallbackDirectoryLister}, so each call falls
 * back S3 -> Google Drive -> local filesystem the same way the underlying reads/listings do.
 */
public interface DictationContentService {

	/** Lists every topic (folder) in the library. */
	List<DictationTopic> listTopics();

	/** Lists every lesson (audio file) directly under the given topic. */
	List<DictationLessonSummary> listLessons(String topicId);

	/**
	 * Loads one lesson's audio bytes and its script split into dictation sentences.
	 *
	 * @throws com.remelearning.common.exception.BusinessException if the lesson can't be found/read
	 */
	DictationLesson getLesson(String topicId, String lessonId);
}
