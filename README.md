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
The B20 ASR scenario additionally requires the Worker and the `media-tools` Compose service.
Start Compose, API and Worker separately, then run:

```powershell
.\gradlew.bat e2eTest
```

The B20 scenario generates a short known audio fixture inside `media-tools`, uploads it through
the presigned API flow and waits until the Worker reaches `EXTRACTING_RECIPE`.

For another API URL:

```powershell
$env:E2E_BASE_URL = 'http://localhost:9090'
.\gradlew.bat e2eTest
```

The API diagnostic endpoint is `GET /v1/config`. Both roles expose Actuator health; the worker does not expose `/v1/config`.

Provider integrations and persistence are intentionally not part of B01.

## IntelliJ IDEA

After opening the `backend` folder as a Gradle project, select one of these run configurations:

- `Reelcipe API (local)` — API on `http://localhost:8080`;
- `Reelcipe Worker (local)` — worker health on `http://localhost:8081/actuator/health`.

Run both configurations simultaneously when developing the two-process backend.
