# Storage abstraction

`StorageClient` is the vendor-neutral contract for reading/writing binary assets (audio clips,
generated speech, ...) addressed by an opaque string `key`. Depend on the **interface**, never a
concrete implementation, so the backing store can move from local disk to the cloud later without
touching business code.

## Selecting a provider

Set `reme.storage.provider` in the service's `application.yml`:

| Value             | Bean                                  | Notes                                                        |
|-------------------|---------------------------------------|--------------------------------------------------------------|
| `local` (default) | `local.LocalStorageClient`            | Plain filesystem rooted at `reme.storage.local.root`.        |
| `s3`              | `s3.S3StorageAdapter`                  | S3/MinIO, all keys under `reme.storage.s3.bucket`.           |

Exactly one bean is registered at a time: `LocalStorageClient` uses
`@ConditionalOnProperty(havingValue = "local", matchIfMissing = true)` and `S3StorageAdapter` uses
`havingValue = "s3"` with **no** `matchIfMissing`, so setting `provider=s3` deactivates the local
bean and vice-versa (this deliberately avoids the double-registration bug the `CacheClient` beans
have).

```yaml
reme:
  storage:
    provider: ${STORAGE_PROVIDER:local}   # local | s3
    local:
      root: ${STORAGE_LOCAL_ROOT:./data}
    s3:
      bucket: ${STORAGE_S3_BUCKET:}
```

## Notes

- `LocalStorageClient.url(key)` returns the key itself — local files aren't directly web-reachable,
  so the owning service streams them through its own endpoint. `S3StorageAdapter.url(key)` returns a
  real object URL.
- Local keys are validated to stay inside the configured root; a key containing `..` is rejected.
- This is **separate from** the older `S3StorageClient` (a thin `S3Client` wrapper that takes an
  explicit bucket per call, still used by recording-service). New code should use `StorageClient`.

## Fallback reader/lister (`storage.fallback`)

`FallbackFileReader`/`FallbackDirectoryLister` are a different shape of abstraction from
`StorageClient` above: instead of picking one active provider via configuration, every call cascades
through **all three** sources in a fixed order - S3, then Google Drive (`storage.drive`), then the
local filesystem - returning the first one that succeeds and logging which source served the
request (or failed) at each step. Built for content libraries (e.g. `content.dictation`) that may be
split across sources during a migration, or where some environments only have local files.

```java
byte[] audio = fallbackFileReader.readFile(s3Bucket, s3Key, driveFileId, localPath);
List<String> topics = fallbackDirectoryLister.listFolders(s3Bucket, s3Prefix, driveFolderId, localDir);
```

Any parameter can be blank/null to skip that source entirely (e.g. no `s3Bucket` configured yet). If
every non-blank source fails, both throw `BusinessException` (`EXTERNAL_SERVICE_ERROR`).

Google Drive access goes through `storage.drive.GoogleDriveClient`, authenticated via a
service-account JSON key at `reme.drive.credentials-file` (see that package for details). Unlike
`S3ClientConfig`'s eager `S3Client` bean, the Drive client connects lazily on first use, so a service
that never actually reaches Drive can start without the credentials file configured.
