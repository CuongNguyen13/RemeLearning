# bff-service

Backend-For-Frontend: the single HTTP entry point for the web/mobile client. Composes calls to the
other domain services and shapes the combined response for the UI, instead of the frontend calling
each service directly.

- Port: **8080**
- No database — purely a composition layer over the other services.
- **Reactive (WebFlux)**, the only non-servlet service in the repo — its `pom.xml` excludes
  `spring-boot-starter-web` from the `common` dependency to keep the servlet stack off the classpath.

## Endpoints

Full spec: [`openapi.yaml`](openapi.yaml) / `/swagger-ui.html` when running. Details + JSON shapes:
[`docs/API.md` §6](../../../docs/API.md#6-bff-service-java--rest).

| Method | Path | Kind |
|---|---|---|
| POST | `/api/v1/auth/register` | proxy → user-service |
| POST | `/api/v1/auth/login` | proxy → user-service |
| GET / PATCH | `/api/v1/users/{userId}` | proxy → user-service |
| POST | `/api/v1/recordings` | proxy (multipart streaming) → recording-service |
| GET | `/api/v1/learners/{userId}/overview` | composite — fans out to user-service + dashboard-service + recording-service |
| GET | `/api/v1/learners/{userId}/weak-points` | composite — fans out to english-service's 3 domains |
| GET | `/api/v1/learners/{userId}/recommendations` | proxy → recommendation-service |
| GET/POST | `/api/v1/learners/{userId}/dictation/*` | thin proxy → english-service's `dictation` package (facets/clips/sessions/attempts/history/ai-practice, plus folder → file browsing rev 2: `folders`, `folders/{folderId}/lessons`, `clips/{clipId}` detail (also relays an optional `?translationLang=`), per-attempt History detail: `history/{attemptId}`, per-attempt AI-practice generation: `history/{attemptId}/ai-practice` (`?translationLang=`), and per-item AI-practice sentence-mode detail: `ai-practice/items/{practiceItemId}/detail`; both AI-practice generation endpoints (`ai-practice/generate` and `history/{attemptId}/ai-practice`) also relay the new `level`/`examType`/`translationLang` facets straight through to english-service) |

Every fan-out call is wrapped in `.onErrorResume(...)` — if one downstream service is down, its slice
of the response defaults to empty/null instead of failing the whole request.

## Run locally

```bash
cd RemeLearning
./mvnw -pl services/bff-service -am spring-boot:run
./mvnw -pl services/bff-service -am test
```

Requires the downstream services it proxies to be running at the URLs configured in
`reme.services.*` (`application.yml` — see `config/DownstreamServicesProperties.java`) for a request
to actually succeed end-to-end; the service itself starts fine without them.

## Notes

- `config/WebClientConfig.java` declares one `WebClient` bean per downstream service. Every downstream
  call is wrapped in a thin `client/*Client` class returning `Mono<T>`, so the aggregation services
  (`service/LearnerOverviewService`, etc.) can be unit-tested by mocking the client instead of standing
  up a real HTTP server.
- Defines its own small response DTOs per downstream call (`dto/`) rather than depending on another
  service's domain classes — services only share a JSON contract across a deployable boundary, never
  Java classes (the one exception: reusing `common.response.ApiResponse<T>` to decode the shared
  envelope, since that's a genuinely shared library type).
- No authentication enforcement — `user-service` issues real JWTs but nothing validates them yet,
  here or anywhere else in the repo.
