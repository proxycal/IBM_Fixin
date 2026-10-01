# Debug-to-Fix Agent Project Plan

## Top-Level Overview

Built a **Spring Boot backend + React frontend** agent that accepts a bug report and a remote Git
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
