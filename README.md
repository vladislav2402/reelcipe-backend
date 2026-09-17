# Reelcipe backend

Initial backend skeleton for task B01.

## Requirements

- JDK 21
- Gradle Wrapper (`gradlew` / `gradlew.bat`)

The project has one codebase and two launch roles:

- `api` — REST API on port 8080;
- `worker` — reserved worker process on port 8081, without user-facing controllers.

## Run

```powershell
.\gradlew.bat test
.\gradlew.bat bootRun --args='--app.role=api --server.port=8080 --spring.profiles.active=local'
.\gradlew.bat bootRun --args='--app.role=worker --server.port=8081 --spring.profiles.active=worker'
```

## E2E tests

E2E tests use an already running API and do not start Spring Boot, PostgreSQL or Testcontainers.
Upload scenarios also require the local S3Mock from `infra/compose.local.yml` on port 9090.
The B23 full mock pipeline scenario additionally requires the Worker and the `media-tools`
Compose service.
Start Compose, API and Worker separately, then run:

```powershell
.\gradlew.bat e2eTest
```

The B23 scenario generates a short H.264/AAC video fixture inside `media-tools`, uploads it
through the presigned S3 flow and waits for ASR, mock LLM, validation, Save and shopping.
No provider network calls are made. The mock ASR/LLM responses are selected by the explicit
fixture marker `reelcipe-b23-fixture:recipe-video-v1`.

The real Groq ASR adapter is disabled by default. To enable it explicitly for a local run,
provide the key through the environment without committing it:

```powershell
$env:APP_PROVIDERS_REAL_CALLS_ENABLED = 'true'
$env:APP_ASR_PROVIDER = 'groq'
$env:GROQ_API_KEY = '<local-secret>'
```

The adapter sends only normalized audio to Groq Whisper and requests `verbose_json` segment
timestamps. The downstream pipeline remains provider-independent.

To run only the full pipeline scenario, use its Cucumber tag. The independent pipeline check is:

```powershell
.\gradlew.bat "-Dcucumber.filter.tags=@b23" e2eTest
.\gradlew.bat integrationTest --tests com.reelcipe.imports.MockPipelineIntegrationTest
```

The integration check uses PostgreSQL Testcontainers and the mock providers. The external E2E
check uses real local PostgreSQL, S3Mock and Compose media conversion:

```powershell
docker-compose --env-file infra/.env.local -f infra/compose.local.yml up -d --build --wait
.\gradlew.bat e2eTest
```

If the Docker installation exposes Compose as `docker compose`, set
`$env:E2E_COMPOSE_COMMAND = 'docker'`; the E2E fixture generator will append the `compose`
subcommand automatically.

For another API URL:

```powershell
$env:E2E_BASE_URL = 'http://localhost:9090'
.\gradlew.bat e2eTest
```

The API diagnostic endpoint is `GET /v1/config`. Both roles expose Actuator health; the worker does not expose
`/v1/config`.

Provider integrations and persistence are intentionally not part of B01.

## IntelliJ IDEA

After opening the `backend` folder as a Gradle project, select one of these run configurations:

- `Reelcipe API (local)` — API on `http://localhost:8080`;
- `Reelcipe Worker (local)` — worker health on `http://localhost:8081/actuator/health`.

Run both configurations simultaneously when developing the two-process backend.
