# Dictation content library

`DictationContentService` gives read-only access to the audio-dictation content library: one folder
per **topic** ("chủ đề"), one audio file per **lesson** ("bài học") within it, plus a matching
transcript script used to split the lesson into per-sentence dictation prompts.

## Layout convention

Reference layout (see `D:\Personal Project\Audio\english-conversations` for a real example):

```
<root>/
  <topic-id>/
    <lesson-id>.mp3
    scripts/
      <lesson-id>.txt
```

- `<topic-id>` is the topic folder's name; `<lesson-id>` is the audio file's name without extension.
- Each script file has one sentence per line, in the order the dictation UI should step through them
  (blank lines are ignored).
- The same convention is applied to whichever source is currently serving the content: an S3 prefix,
  a Google Drive folder, or a local directory - see `reme.dictation.*` below.

## Configuration

```yaml
reme:
  dictation:
    audio-extension: .mp3
    scripts-subfolder: scripts
    s3:
      bucket: ${DICTATION_S3_BUCKET:}
      root-prefix: ${DICTATION_S3_ROOT_PREFIX:}
    drive:
      root-folder-id: ${DICTATION_DRIVE_ROOT_FOLDER_ID:}
    local:
      root-path: ${DICTATION_LOCAL_ROOT:./data/dictation}
```

## API

- `listTopics()` - every topic (folder) in the library.
- `listLessons(topicId)` - every lesson (audio file) directly under a topic, as lightweight
  `DictationLessonSummary` (id + title only, no content).
- `getLesson(topicId, lessonId)` - the full `DictationLesson`: audio bytes plus the script parsed
  into `sentences()`, one per dictation step.

Each call delegates to `storage.fallback.FallbackFileReader`/`FallbackDirectoryLister`, so it
transparently falls back S3 -> Google Drive -> local filesystem exactly the way those do. Because
Google Drive addresses content by folder/file id rather than by name, every Drive lookup here first
resolves the id of the matching-named child under its parent folder (see
`DictationContentServiceImpl.resolveDriveId`); if Drive is unreachable, unconfigured, or the child
isn't found, that resolves to `null` and the call falls straight through to the local filesystem, the
same as if Drive had failed outright.

This module intentionally stops at the read/listing layer - it has no REST controller or Kafka
consumer of its own yet; a future dictation-service (or an existing service) is expected to expose
these through its own API and add the per-sentence attempt/hint-count flow described in the product
requirements.
