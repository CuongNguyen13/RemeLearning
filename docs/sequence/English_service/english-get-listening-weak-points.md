# GET /api/v1/listening/weak-points/{userId} and /{userId}/grouped

Returns the listening "weak points" for a user, merged from two sources distinguished by
`sourceType`: `DICTATION` (dual-written by dictation's `LearningGapAnalyzedConsumer`, see
[english-learning-gap-analyzed-listening.md](english-learning-gap-analyzed-listening.md)) and
`COMPREHENSION` (scored directly by the practice/redo flow's Java engine, see
[practice-redo.md](practice-redo.md) and [listening-learn.md](listening-learn.md)). See
`english-service`'s `listening/weakpoint/controller/ListeningWeakPointController.java`.

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
sequenceDiagram
    participant Caller
    participant Ctrl as ListeningWeakPointController
    participant Svc as ListeningWeakPointServiceImpl
    participant Mapper as ListeningWeakPointMapper (MyBatis)
    participant DB as reme_english DB

    alt GET /api/v1/listening/weak-points/{userId}?sourceType={ListeningSourceType}
        Caller->>Ctrl: GET .../{userId}?sourceType=DICTATION (optional)
        Ctrl->>Svc: getWeakPoints(userId, sourceType)
        Svc->>Mapper: findByUserId(userId, sourceType==null ? null : sourceType.name())
        Mapper->>DB: SELECT listening_weak_points<br/>WHERE user_id = ? [AND source_type = ?]<br/>ORDER BY forgetting_score DESC
        DB-->>Mapper: rows (empty list if none)
        Mapper-->>Svc: List[ListeningWeakPoint]
        Svc-->>Ctrl: List[ListeningWeakPoint]
        Ctrl-->>Caller: 200 List[ListeningWeakPoint]
    else GET /api/v1/listening/weak-points/{userId}/grouped
        Caller->>Ctrl: GET .../{userId}/grouped
        Ctrl->>Svc: getWeakPoints(userId, null)
        Svc->>Mapper: findByUserId(userId, null)
        Mapper->>DB: SELECT listening_weak_points WHERE user_id = ?
        DB-->>Mapper: rows
        Mapper-->>Svc: List[ListeningWeakPoint]
        Svc-->>Ctrl: List[ListeningWeakPoint]
        Ctrl->>Ctrl: Collectors.groupingBy(ListeningSourceType)
        Ctrl-->>Caller: 200 Map[ListeningSourceType, List[ListeningWeakPoint]]
    end
```

## Notes

- `ListeningSourceType`: `DICTATION` (dictation dual-write, always `scoreSource = PYTHON_LEGACY`),
  `COMPREHENSION` (practice/redo Java engine, always `scoreSource = JAVA_ENGINE`).
- `ListeningWeakPoint` fields: `id, recordingId, userId, itemId, label, sourceType, forgettingScore,
  recommendation, masteryLevel, nextReviewAt, scoreSource, updatedAt`.
- Unlike `GrammarWeakPointService`, there is no `getTopWeakPoints`/`findTopByUserId` method - nothing
  in this domain (yet) needs a "top N" query the way dictation's grammar-suggestion flow does.
- No validation/exception path beyond a normal DB query — no matching data simply returns an empty
  list, not a 404.
