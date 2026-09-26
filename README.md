# Debug-to-Fix Agent — Project Plan

## Confirmed Design Decisions

| Decision | Choice |
|---|---|
| Fix prompt strategy | Single LLM call returning all changed files as a JSON array; per-file iteration is a future fallback |
| IAM token handling | Implement `IamTokenService` now — exchanges `watsonx.api-key` for a Bearer token via `https://iam.cloud.ibm.com/identity/token` |
| Default model ID | `ibm/granite-3-3-8b-instruct` |

---

## Top-Level Overview

Build a **Spring Boot backend + React frontend** agent that accepts a bug report and a remote Git
repository URL, then executes a five-stage pipeline:

1. **Clone** the target repo.
2. **Extract context** (relevant source files) and send it to **IBM watsonx.ai** for root-cause analysis.
3. Ask watsonx.ai for a **fix proposal** (unified diff / patched file content).
4. **Apply the patch** to the cloned repo and ask watsonx.ai to **generate a JUnit regression test**.
5. **Run the test suite** (`mvn test` or `./gradlew test`) in the patched repo and return the full result.

The pipeline is **synchronous and stateless** — every HTTP request is independent, no database.

---

## Project Structure

```
debug-to-fix-agent/
├── backend/                        ← Spring Boot (Maven)
│   ├── src/main/java/com/debugfix/
│   │   ├── controller/
│   │   │   └── PipelineController.java
│   │   ├── dto/
│   │   │   ├── PipelineRequestDTO.java
│   │   │   └── PipelineResultDTO.java
│   │   ├── service/
│   │   │   ├── GitService.java
│   │   │   ├── ContextExtractorService.java
│   │   │   ├── WatsonxService.java
│   │   │   ├── PatchApplierService.java
│   │   │   ├── TestWriterService.java
│   │   │   └── BuildRunnerService.java
│   │   ├── pipeline/
│   │   │   └── PipelineOrchestrator.java
│   │   └── config/
│   │       └── WatsonxConfig.java
│   └── src/main/resources/
│       └── application.properties
└── frontend/                       ← React (Vite or CRA)
    └── src/
        ├── api/pipelineApi.js
        ├── components/
        │   ├── BugReportForm.jsx
        │   └── ResultPanel.jsx
        └── App.jsx
```

---

## REST API Endpoints

### `POST /api/pipeline/run`
Trigger the full pipeline.

**Request body (`PipelineRequestDTO`):**
```json
{
  "repoUrl": "https://github.com/org/repo.git",
  "bugDescription": "NullPointerException in UserService.getUser() when id is null",
  "branch": "main"
}
```

**Response body (`PipelineResultDTO`):**
```json
{
  "rootCause": "...",
  "fixDescription": "...",
  "patchedFiles": [
    { "path": "src/main/java/com/example/UserService.java", "content": "..." }
  ],
  "generatedTest": {
    "path": "src/test/java/com/example/UserServiceTest.java",
    "content": "..."
  },
  "testVerification": {
    "passed": true,
    "output": "BUILD SUCCESS\n..."
  }
}
```

### `GET /api/health`
Liveness check — returns `200 OK` with `{ "status": "UP" }`.

---

## Sub-Tasks

---

### Sub-Task 1 — Spring Boot Project Scaffold

**Status:** `[x] complete`

**Intent**
Create the Maven project skeleton with the correct package structure, `pom.xml` dependencies, and
`application.properties`. All subsequent sub-tasks drop files into this scaffold.

**Expected Outcomes**
- `backend/pom.xml` exists with Spring Boot Web, spring-boot-starter-test, JGit, and an HTTP client
  (Spring's `RestClient` or `WebClient`) declared as dependencies.
- `application.properties` contains placeholder keys for `watsonx.api.url`, `watsonx.api.key`,
  and `watsonx.model.id`.
- All Java packages exist as directories.
- `mvn package -DskipTests` succeeds.

**Todo List**
1. Create `backend/pom.xml` — Spring Boot parent 3.x, add `spring-boot-starter-web`,
   `spring-boot-starter-test`, `org.eclipse.jgit`, and `spring-boot-starter-webflux`
   (for `WebClient` to call watsonx.ai).
2. Create the main application class `DebugFixAgentApplication.java`.
3. Create all service, controller, dto, pipeline, and config packages as empty directories
   (place a `.gitkeep` or stub class in each so the directories are tracked).
4. Add `application.properties` with placeholder properties.
5. Verify `mvn package -DskipTests` passes.

**Relevant Context**
- Spring Boot 3.x requires Java 17+.
- JGit (`org.eclipse.jgit:org.eclipse.jgit`) is the library used in Sub-Task 2 for Git operations.

---

### Sub-Task 2 — GitService

**Status:** `[x] complete`

**Intent**
Implement the service responsible for cloning the target repository to a temporary local directory.
The temp directory is created per-request and cleaned up after the pipeline completes.

**Expected Outcomes**
- `GitService.cloneRepo(String repoUrl, String branch): Path` clones the repo to a `Files.createTempDirectory`
  location and returns the path.
- `GitService.deleteRepo(Path repoPath)` recursively deletes the temp directory.
- A unit test (`GitServiceTest.java`) verifies the clone of a known public repo succeeds and the
  expected directory structure exists.

**Todo List**
1. Implement `GitService.cloneRepo()` using JGit's `Git.cloneRepository()` builder.
2. Implement `GitService.deleteRepo()` using `Files.walk()` to recursively delete.
3. Write `GitServiceTest` — use a real small public repo (or a local bare repo fixture) to verify
   the clone path is non-empty after cloning.

**Relevant Context**
- JGit `CloneCommand` supports `.setBranch()`, `.setDirectory()`, and `.setURI()`.
- For private repos, credentials would be injected via `UsernamePasswordCredentialsProvider` — out of
  scope for this plan (public repos only).

---

### Sub-Task 3 — ContextExtractorService

**Status:** `[x] complete`

**Intent**
Walk the cloned repository and extract the most relevant source files to include as context in the
LLM prompt. The extractor must stay within a token budget so the watsonx.ai prompt does not exceed
model limits.

**Expected Outcomes**
- `ContextExtractorService.extract(Path repoPath, String bugDescription): String` returns a
  string of concatenated file contents, each prefixed with its relative path, capped at a
  configurable character limit (default 30 000 characters).
- Files are ranked by relevance heuristic: files whose name/path contains keywords from the bug
  description are included first; remaining `.java` files are appended until the budget is reached.
- A unit test verifies extraction against a fixture directory.

**Todo List**
1. Implement file discovery — walk the repo with `Files.walk()`, filter for `.java` files, skip
   `target/` and `build/` directories.
2. Implement a simple keyword scorer: tokenise the bug description into words, count matches in each
   file's relative path; sort descending.
3. Concatenate files up to the character budget, prefixing each block with `// FILE: <relative-path>`.
4. Expose `contextCharLimit` as a property in `application.properties`
   (`context.char.limit=30000`).
5. Write `ContextExtractorServiceTest` with a small fixture directory.

**Relevant Context**
- The character limit is intentional — watsonx.ai models have input token limits
  (e.g. ~8 000 tokens ≈ ~32 000 characters for Llama-based models).

---

### Sub-Task 4 — WatsonxService

**Status:** `[x] complete`

**Intent**
Implement the low-level integration with the IBM watsonx.ai text generation REST API. This service
is called three times in the pipeline (root-cause, fix proposal, test generation) with different
system prompts and user messages.

**Expected Outcomes**
- `WatsonxService.generate(String systemPrompt, String userMessage): String` calls the watsonx.ai
  `/ml/v1/text/generation` endpoint and returns the generated text.
- API URL, API key, model ID, and project ID are injected from `application.properties`.
- A `WatsonxConfig` `@ConfigurationProperties` bean holds these values.
- A unit test mocks the `WebClient` and verifies the request payload shape and response parsing.

**Todo List**
1. Create `WatsonxConfig.java` with `@ConfigurationProperties(prefix = "watsonx")` — fields:
   `apiUrl`, `apiKey`, `modelId` (default `ibm/granite-3-3-8b-instruct`), `projectId`.
2. Implement `IamTokenService` — `POST https://iam.cloud.ibm.com/identity/token` with
   `grant_type=urn:ibm:params:oauth:grant-type:apikey&apikey=<key>`, parse `access_token`.
   Cache the token and refresh when `expires_in` has elapsed (store expiry timestamp).
3. Implement `WatsonxService` using `WebClient` — build the JSON body matching the watsonx
   `/ml/v1/text/generation` request schema (`model_id`, `project_id`, `input`, `parameters`).
   Inject the Bearer token from `IamTokenService` for each request.
4. Parse the response — extract `results[0].generated_text`.
5. Write `WatsonxServiceTest` using `MockWebServer` (OkHttp) or `WireMock`; mock both the IAM
   token endpoint and the generation endpoint.

**Relevant Context**
- watsonx.ai text generation endpoint: `POST /ml/v1/text/generation?version=2023-05-29`
- Required body fields: `model_id`, `project_id`, `input`, `parameters.max_new_tokens`.
- IAM token endpoint: `POST https://iam.cloud.ibm.com/identity/token`
  body (form-encoded): `grant_type=urn:ibm:params:oauth:grant-type:apikey&apikey=<YOUR_KEY>`
  response field: `access_token`, `expires_in` (seconds).
- Default model: `ibm/granite-3-3-8b-instruct`.

---

### Sub-Task 5 — PatchApplierService & TestWriterService

**Status:** `[x] complete`

**Intent**
After watsonx.ai returns fix content and test content, these two small services write the files
into the cloned repo on disk so the build runner can compile and test them.

**Expected Outcomes**
- `PatchApplierService.applyPatch(Path repoPath, List<PatchedFile> patches)` writes each patched
  file to its path under `repoPath`, creating parent directories as needed.
- `TestWriterService.writeTest(Path repoPath, GeneratedTest test)` writes the test `.java` file to
  the appropriate path under `src/test/java/`.
- Unit tests verify that files are written to the correct paths with the expected content.

**Todo List**
1. Define `PatchedFile` record: `String relativePath`, `String content`.
2. Define `GeneratedTest` record: `String relativePath`, `String content`.
3. Implement `PatchApplierService.applyPatch()` — for each `PatchedFile`, resolve the full path,
   create parent directories, and write via `Files.writeString()`.
4. Implement `TestWriterService.writeTest()` similarly.
5. Write unit tests for both services against a temp directory.

**Relevant Context**
- These services are intentionally simple: the LLM returns full file content (not unified diffs),
  so no diff-parsing logic is needed.
- The path separator in LLM output may be `/` regardless of OS — normalise with `Path.of(relativePath)`.

---

### Sub-Task 6 — BuildRunnerService

**Status:** `[x] complete`

**Intent**
Run the test suite inside the cloned, patched repo by invoking the appropriate build tool process
and capturing stdout/stderr.

**Expected Outcomes**
- `BuildRunnerService.runTests(Path repoPath): TestVerificationResult` detects whether the repo
  uses Maven (`pom.xml`) or Gradle (`build.gradle` / `build.gradle.kts`), runs the correct command,
  and returns `{ passed: boolean, output: String }`.
- A timeout (default 5 minutes, configurable) prevents the pipeline from hanging.
- Unit test verifies the detection logic; integration test verifies a real Maven build (use the
  scaffold itself as fixture).

**Todo List**
1. Detect build tool: check for `pom.xml` → Maven; check for `build.gradle*` → Gradle; throw
   `UnsupportedBuildToolException` otherwise.
2. Build the `ProcessBuilder` command: Maven → `mvn test -B`; Gradle → `./gradlew test`
   (or `gradlew.bat` on Windows).
3. Redirect stdout + stderr into a `StringBuilder` via a background reader thread.
4. Use `process.waitFor(timeout, TimeUnit.MINUTES)`.
5. Map exit code 0 → `passed=true`, non-zero → `passed=false`.
6. Expose `build.timeout.minutes=5` in `application.properties`.
7. Write unit test for detection logic and an integration test using a minimal Maven project fixture.

**Relevant Context**
- `ProcessBuilder.redirectErrorStream(true)` merges stderr into stdout for a single output string.
- On Windows, Maven must be invoked as `mvn.cmd`; the service should detect the OS.

---

### Sub-Task 7 — PipelineOrchestrator & PipelineController

**Status:** `[x] complete`

**Intent**
Wire all services together in the correct sequence. The orchestrator owns the pipeline flow
(clone → extract → analyse → fix → patch → generate-test → write-test → run → cleanup).
The controller exposes it over HTTP.

**Expected Outcomes**
- `PipelineOrchestrator.run(PipelineRequestDTO request): PipelineResultDTO` calls each service
  in order, assembles the result DTO, and always calls `GitService.deleteRepo()` in a `finally` block.
- `PipelineController` exposes `POST /api/pipeline/run` and `GET /api/health`.
- An integration test (`PipelineControllerTest`) uses `@SpringBootTest` + `MockMvc` with the
  watsonx service mocked via `@MockBean`.

**Todo List**
1. Write the three system prompts as constants (or `@Value`-injected strings from properties):
   - `PROMPT_ROOT_CAUSE` — asks the LLM to identify root cause given file context + bug description.
   - `PROMPT_FIX` — asks for full corrected file content for each affected file.
   - `PROMPT_TEST` — asks for a JUnit 5 regression test for the fix.
2. Implement `PipelineOrchestrator.run()` — sequential calls with `try/finally` cleanup.
3. Parse watsonx responses: root cause → plain text; fix → JSON array of `{path, content}`;
   test → JSON `{path, content}`. Add a `ResponseParserUtil` if needed.
4. Implement `PipelineController` with `@PostMapping("/api/pipeline/run")` and
   `@GetMapping("/api/health")`.
5. Add global exception handler (`@RestControllerAdvice`) returning structured error JSON.
6. Write `PipelineControllerTest` with `@SpringBootTest`, `MockMvc`, and `@MockBean WatsonxService`.

**Relevant Context**
- The LLM is instructed to return fix and test as **JSON** so the backend can parse file paths and
  content without brittle text extraction.
- Fix prompt strategy: single LLM call returning a JSON array of all changed files
  (`[{"path":"...","content":"..."},...]`). Per-file iteration is explicitly deferred.
- The `finally` cleanup ensures temp directories are never left on disk.

---

### Sub-Task 8 — React Frontend

**Status:** `[x] complete`

**Intent**
Build a minimal React UI: a form to submit the bug report + repo URL, and a results panel that
displays each stage's output when the pipeline completes.

**Expected Outcomes**
- `BugReportForm` component submits `POST /api/pipeline/run` and shows a loading indicator.
- `ResultPanel` component displays: root cause, fix description + patched file diffs, generated test,
  and test verification output (pass/fail badge + raw output).
- The API base URL is configured via an environment variable (`VITE_API_BASE_URL`).

**Todo List**
1. Scaffold the React project in `frontend/` (Vite + React).
2. Create `src/api/pipelineApi.js` — `runPipeline(request)` wraps `fetch` to `POST /api/pipeline/run`.
3. Implement `BugReportForm.jsx` — fields: Repo URL, Bug Description, Branch (optional, default `main`);
   submit button with loading state.
4. Implement `ResultPanel.jsx` — sectioned display of `PipelineResultDTO` fields.
5. Wire them together in `App.jsx` with local `useState` for result and loading.
6. Add a Vite proxy entry so `fetch('/api/...')` in dev mode forwards to `http://localhost:8080`.

**Relevant Context**
- No state management library needed — plain `useState` + `useEffect` is sufficient.
- The backend must set `Access-Control-Allow-Origin` for production; add a Spring `CorsConfiguration`
  in `WatsonxConfig` or a dedicated `WebMvcConfigurer` bean.

---

## Cross-Cutting Concerns

| Concern | Approach |
|---|---|
| CORS | `WebMvcConfigurer` bean allowing the React dev origin (`localhost:5173`) |
| Secrets | `application.properties` for local dev; environment variables / secrets manager for prod |
| Prompt engineering | System prompts stored as `static final String` constants in `PipelineOrchestrator`; easy to iterate |
| Token budget | Context extractor caps at 30 000 chars; `max_new_tokens` per stage tuned separately |
| Error surface | `@RestControllerAdvice` returns `{ "error": "...", "stage": "..." }` so the React UI knows which stage failed |
