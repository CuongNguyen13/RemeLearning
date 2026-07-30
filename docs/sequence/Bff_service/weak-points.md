# GET /api/v1/learners/{userId}/weak-points

`LearnerController.getWeakPoints` delegates to `WeakPointAggregationService.getWeakPoints`, which
fans out to english-service's four weak-point endpoints (all served by the one merged
`english-service` on port 8085) in parallel via `Mono.zip`, merging them into one map keyed by
category. See `bff-service`'s `service/WeakPointAggregationService.java` /
`client/EnglishServiceClient.java`.

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
sequenceDiagram
    participant Caller
    participant Ctrl as LearnerController
    participant Svc as WeakPointAggregationService
    participant EngClient as EnglishServiceClient
    participant English as english-service :8085

    Caller->>Ctrl: GET /api/v1/learners/{userId}/weak-points
    Ctrl->>Svc: getWeakPoints(userId)

    par vocabulary
        Svc->>EngClient: getVocabularyWeakPoints(userId)
        EngClient->>English: GET /api/v1/vocabulary/weak-points/{userId}
        English-->>EngClient: ApiResponse<VocabularyWeakPoint[]>
        EngClient->>EngClient: map to WeakPointDto, stamp category="vocabulary"
        alt error
            EngClient-->>Svc: Mono.error
            Svc->>Svc: onErrorResume -> empty List
        else success
            EngClient-->>Svc: Mono<List<WeakPointDto>>
        end
    and grammar
        Svc->>EngClient: getGrammarWeakPoints(userId)
        EngClient->>English: GET /api/v1/grammar/weak-points/{userId}
        English-->>EngClient: ApiResponse<GrammarWeakPoint[]>
        EngClient->>EngClient: map to WeakPointDto, stamp category="grammar"
        alt error
            EngClient-->>Svc: Mono.error
            Svc->>Svc: onErrorResume -> empty List
        else success
            EngClient-->>Svc: Mono<List<WeakPointDto>>
        end
    and pronunciation
        Svc->>EngClient: getPronunciationWeakPoints(userId)
        EngClient->>English: GET /api/v1/pronunciation/weak-points/{userId}
        English-->>EngClient: ApiResponse<PronunciationWeakPoint[]>
        EngClient->>EngClient: map to WeakPointDto, stamp category="pronunciation"
        alt error
            EngClient-->>Svc: Mono.error
            Svc->>Svc: onErrorResume -> empty List
        else success
            EngClient-->>Svc: Mono<List<WeakPointDto>>
        end
    and listening
        Svc->>EngClient: getListeningWeakPoints(userId)
        EngClient->>English: GET /api/v1/listening/weak-points/{userId}
        English-->>EngClient: ApiResponse<ListeningWeakPoint[]>
        EngClient->>EngClient: map to WeakPointDto, stamp category="listening"<br/>(sourceType binds automatically via Jackson - same field name both sides)
        alt error
            EngClient-->>Svc: Mono.error
            Svc->>Svc: onErrorResume -> empty List
        else success
            EngClient-->>Svc: Mono<List<WeakPointDto>>
        end
    end

    Svc->>Svc: Mono.zip(vocabularyMono, grammarMono, pronunciationMono, listeningMono)<br/>-> Map{"vocabulary": [...], "grammar": [...], "pronunciation": [...], "listening": [...]}
    Svc-->>Ctrl: Mono<Map<String, List<WeakPointDto>>>
    Ctrl-->>Caller: 200 ApiResponse<Map<String, List<WeakPointDto>>>
```

## External calls

| # | Call | From -> To | Notes |
|---|------|-----------|-------|
| 1 | `GET /api/v1/vocabulary/weak-points/{userId}` | bff-service -> english-service | defaults to `[]` on failure |
| 2 | `GET /api/v1/grammar/weak-points/{userId}` | bff-service -> english-service | defaults to `[]` on failure |
| 3 | `GET /api/v1/pronunciation/weak-points/{userId}` | bff-service -> english-service | defaults to `[]` on failure |
| 4 | `GET /api/v1/listening/weak-points/{userId}` | bff-service -> english-service | defaults to `[]` on failure |

## Notes

- All four calls hit the same physical `english-service` instance (port 8085) but are still issued
  as four independent HTTP requests, run concurrently — english-service has no combined endpoint
  today that returns all four domains in one call.
- `category` isn't present in english-service's per-domain JSON (each uses its own
  `vocabularyType`/`grammarType`/`pronunciationType`/`sourceType` field) — `EnglishServiceClient`
  stamps the literal category string itself right after deserializing, based on which endpoint it
  called, so the merged map can be built. `sourceType` (listening only) is the one exception: it
  binds automatically via Jackson since english-service's JSON field is already named `sourceType`.
- Any one domain failing degrades only that entry to `[]`; the other three still populate normally.
