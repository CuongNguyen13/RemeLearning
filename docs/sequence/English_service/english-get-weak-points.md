# GET /api/v1/vocabulary/weak-points/{userId} and /{userId}/grouped

Returns the vocabulary "weak points" analyzed and persisted for a user, written by
`LearningGapAnalyzedConsumer` (see
[english-learning-gap-analyzed.md](english-learning-gap-analyzed.md)).
See `english-service`'s `vocabulary/controller/VocabularyWeakPointController.java`.

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
sequenceDiagram
    participant Caller
    participant Ctrl as VocabularyWeakPointController
    participant Svc as VocabularyWeakPointServiceImpl
    participant Mapper as VocabularyWeakPointMapper (MyBatis)
    participant DB as reme_english DB

    alt GET /api/v1/vocabulary/weak-points/{userId}?type={VocabularyType}
        Caller->>Ctrl: GET .../{userId}?type=NOUN (optional)
        Ctrl->>Svc: getWeakPoints(userId, type)
        Svc->>Mapper: findByUserId(userId, type==null ? null : type.name())
        Mapper->>DB: SELECT vocabulary_weak_points<br/>WHERE user_id = ? [AND vocabulary_type = ?]<br/>ORDER BY forgetting_score DESC
        DB-->>Mapper: rows (empty list if none)
        Mapper-->>Svc: List[VocabularyWeakPoint]
        Svc-->>Ctrl: List[VocabularyWeakPoint]
        Ctrl-->>Caller: 200 List[VocabularyWeakPoint]
    else GET /api/v1/vocabulary/weak-points/{userId}/grouped
        Caller->>Ctrl: GET .../{userId}/grouped
        Ctrl->>Svc: getWeakPoints(userId, null)
        Svc->>Mapper: findByUserId(userId, null)
        Mapper->>DB: SELECT vocabulary_weak_points WHERE user_id = ?
        DB-->>Mapper: rows
        Mapper-->>Svc: List[VocabularyWeakPoint]
        Svc-->>Ctrl: List[VocabularyWeakPoint]
        Ctrl->>Ctrl: Collectors.groupingBy(VocabularyType)
        Ctrl-->>Caller: 200 Map[VocabularyType, List[VocabularyWeakPoint]]
    end
```

## Notes

- `VocabularyType`: `NOUN, VERB, ADJECTIVE, ADVERB, PHRASAL_VERB, COLLOCATION, IDIOM, OTHER`.
- `VocabularyWeakPoint` fields: `id, recordingId, userId, itemId, label, vocabularyType,
  forgettingScore, recommendation, updatedAt`.
- No validation/exception path beyond a normal DB query — no matching data simply returns an empty
  list, not a 404.
