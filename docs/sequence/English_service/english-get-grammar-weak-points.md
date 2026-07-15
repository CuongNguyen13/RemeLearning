# GET /api/v1/grammar/weak-points/{userId} and /{userId}/grouped

Returns the grammar "weak points" analyzed and persisted for a user, written by grammar's
`LearningGapAnalyzedConsumer` (see
[english-learning-gap-analyzed-grammar.md](english-learning-gap-analyzed-grammar.md)).
See `english-service`'s `grammar/controller/GrammarWeakPointController.java`.

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
sequenceDiagram
    participant Caller
    participant Ctrl as GrammarWeakPointController
    participant Svc as GrammarWeakPointServiceImpl
    participant Mapper as GrammarWeakPointMapper (MyBatis)
    participant DB as reme_english DB

    alt GET /api/v1/grammar/weak-points/{userId}?type={GrammarType}
        Caller->>Ctrl: GET .../{userId}?type=TENSE (optional)
        Ctrl->>Svc: getWeakPoints(userId, type)
        Svc->>Mapper: findByUserId(userId, type==null ? null : type.name())
        Mapper->>DB: SELECT grammar_weak_points<br/>WHERE user_id = ? [AND grammar_type = ?]<br/>ORDER BY forgetting_score DESC
        DB-->>Mapper: rows (empty list if none)
        Mapper-->>Svc: List[GrammarWeakPoint]
        Svc-->>Ctrl: List[GrammarWeakPoint]
        Ctrl-->>Caller: 200 List[GrammarWeakPoint]
    else GET /api/v1/grammar/weak-points/{userId}/grouped
        Caller->>Ctrl: GET .../{userId}/grouped
        Ctrl->>Svc: getWeakPoints(userId, null)
        Svc->>Mapper: findByUserId(userId, null)
        Mapper->>DB: SELECT grammar_weak_points WHERE user_id = ?
        DB-->>Mapper: rows
        Mapper-->>Svc: List[GrammarWeakPoint]
        Svc-->>Ctrl: List[GrammarWeakPoint]
        Ctrl->>Ctrl: Collectors.groupingBy(GrammarType)
        Ctrl-->>Caller: 200 Map[GrammarType, List[GrammarWeakPoint]]
    end
```

## Notes

- `GrammarType`: `TENSE, SUBJECT_VERB_AGREEMENT, ARTICLE, PREPOSITION, WORD_ORDER, PLURAL,
  PUNCTUATION, OTHER`.
- `GrammarWeakPoint` fields: `id, recordingId, userId, itemId, label, grammarType,
  forgettingScore, recommendation, updatedAt`.
- No validation/exception path beyond a normal DB query — no matching data simply returns an empty
  list, not a 404.
