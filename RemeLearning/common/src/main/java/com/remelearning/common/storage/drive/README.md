# Google Drive client

`GoogleDriveClient` is a narrow, read-only contract over the Drive API v3: downloading a file's
bytes by id, and listing a folder's immediate (non-trashed) children by id. It exists purely as one
of the three sources behind `storage.fallback.FallbackFileReader`/`FallbackDirectoryLister` - depend
on the interface, never on `GoogleDriveClientImpl`.

## Configuration

```yaml
reme:
  drive:
    credentials-file: ${DRIVE_CREDENTIALS_FILE:}   # path to a service-account JSON key
    application-name: RemeLearning
```

Grant the service account "Viewer" access to whatever Drive folder(s) it needs to read (share the
folder with the service account's email, found in the JSON key file).

## Notes

- The `Drive` API client is built lazily on first call, not at startup - a service that never
  actually reaches Drive can start with `credentials-file` unset. Only an actual `downloadFile`/
  `listChildren` call fails in that case, which the fallback reader/lister treat like any other
  unreachable source and fall through to the next one.
- Folders and files are told apart by MIME type (`application/vnd.google-apps.folder`).
- Drive addresses content by opaque file/folder **id**, not by path - callers that need to resolve a
  child by name (e.g. `content.dictation.DictationContentServiceImpl`) list the parent's children and
  match by name themselves; a failed/unconfigured lookup resolves to `null` rather than throwing, so
  it naturally falls through to the next source.
