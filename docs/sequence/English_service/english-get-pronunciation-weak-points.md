# GET /api/v1/pronunciation/weak-points/{userId} and /{userId}/grouped

Returns the pronunciation "weak points" analyzed and persisted for a user, written by
pronunciation's `LearningGapAnalyzedConsumer` (see
[english-learning-gap-analyzed-pronunciation.md](english-learning-gap-analyzed-pronunciation.md)).
See `english-service`'s `pronunciation/controller/PronunciationWeakPointController.java`.

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
sequenceDiagram
    participant Caller
    participant Ctrl as PronunciationWeakPointController
    participant Svc as PronunciationWeakPointServiceImpl
    participant Mapper as PronunciationWeakPointMapper (MyBatis)
    participant DB as reme_english DB

    alt GET /api/v1/pronunciation/weak-points/{userId}?type={PronunciationType}
        Caller->>Ctrl: GET .../{userId}?type=STRESS (optional)
        Ctrl->>Svc: getWeakPoints(userId, type)
        Svc->>Mapper: findByUserId(userId, type==null ? null : type.name())
        Mapper->>DB: SELECT pronunciation_weak_points<br/>WHERE user_id = ? [AND pronunciation_type = ?]<br/>ORDER BY forgetting_score DESC
        DB-->>Mapper: rows (empty list if none)
        Mapper-->>Svc: List[PronunciationWeakPoint]
        Svc-->>Ctrl: List[PronunciationWeakPoint]
        Ctrl-->>Caller: 200 List[PronunciationWeakPoint]
    else GET /api/v1/pronunciation/weak-points/{userId}/grouped
        Caller->>Ctrl: GET .../{userId}/grouped
        Ctrl->>Svc: getWeakPoints(userId, null)
        Svc->>Mapper: findByUserId(userId, null)
        Mapper->>DB: SELECT pronunciation_weak_points WHERE user_id = ?
        DB-->>Mapper: rows
        Mapper-->>Svc: List[PronunciationWeakPoint]
        Svc-->>Ctrl: List[PronunciationWeakPoint]
        Ctrl->>Ctrl: Collectors.groupingBy(PronunciationType)
        Ctrl-->>Caller: 200 Map[PronunciationType, List[PronunciationWeakPoint]]
    end
```

## Notes

- `PronunciationType`: `VOWEL, CONSONANT, STRESS, INTONATION, LINKING, RHYTHM, OTHER`.
- `PronunciationWeakPoint` fields: `id, recordingId, userId, itemId, label, pronunciationType,
  forgettingScore, recommendation, updatedAt`.
- No validation/exception path beyond a normal DB query — no matching data simply returns an empty
  list, not a 404.
