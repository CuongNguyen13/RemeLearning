# GET /api/v1/recommendations/{userId} and /{userId}/grouped

Returns the recommendation rows persisted for a user, written by `LearningGapAnalyzedConsumer` (see
[learning-gap-analyzed.md](learning-gap-analyzed.md)). Covers every category (vocabulary, grammar,
pronunciation) since `recommendation-service` doesn't filter by category on ingestion. See
`recommendation-service`'s `controller/RecommendationController.java`.

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
sequenceDiagram
    participant Caller
    participant Ctrl as RecommendationController
    participant Svc as RecommendationServiceImpl
    participant Mapper as RecommendationMapper (MyBatis)
    participant DB as reme_recommendation DB

    alt GET /api/v1/recommendations/{userId}?category={category}
        Caller->>Ctrl: GET .../{userId}?category=grammar (optional)
        Ctrl->>Svc: getByUserId(userId, category)
        Svc->>Mapper: findByUserId(userId, category)
        Mapper->>DB: SELECT recommendations<br/>WHERE user_id = ? [AND category = ?]<br/>ORDER BY forgetting_score DESC
        DB-->>Mapper: rows (empty list if none)
        Mapper-->>Svc: List[Recommendation]
        Svc-->>Ctrl: List[Recommendation]
        Ctrl-->>Caller: 200 List[Recommendation]
    else GET /api/v1/recommendations/{userId}/grouped
        Caller->>Ctrl: GET .../{userId}/grouped
        Ctrl->>Svc: getByUserIdGrouped(userId)
        Svc->>Mapper: findByUserId(userId, null)
        Mapper->>DB: SELECT recommendations WHERE user_id = ?
        DB-->>Mapper: rows
        Mapper-->>Svc: List[Recommendation]
        Svc->>Svc: Collectors.groupingBy(Recommendation::getCategory)
        Svc-->>Ctrl: Map[String, List[Recommendation]]
        Ctrl-->>Caller: 200 Map[String, List[Recommendation]]
    end
```

## Notes

- `category` is a free-form string (`vocabulary`, `grammar`, `pronunciation`) mirrored from
  `WeakPointPayload.category` as-is, not a Java enum like `english-service`'s per-domain
  `VocabularyType`/`GrammarType`/`PronunciationType` — recommendation-service aggregates across
  domains rather than classifying within one.
- `Recommendation` fields: `id, userId, recordingId, itemId, category, label, forgettingScore,
  recommendationText, updatedAt`.
- No validation/exception path beyond a normal DB query — no matching data simply returns an empty
  list, not a 404.
