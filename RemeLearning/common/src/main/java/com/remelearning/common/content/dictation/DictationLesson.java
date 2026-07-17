package com.remelearning.common.content.dictation;

import java.util.List;

/**
 * A single dictation lesson: its audio clip plus the script split into one sentence per line, in
 * playback order - the unit the dictation UI steps through sentence by sentence.
 */
public record DictationLesson(String id, String title, byte[] audio, List<String> sentences) {
}
