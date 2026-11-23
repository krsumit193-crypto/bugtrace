# BugTrace — Smart Error Log Analyzer

BugTrace turns raw application log files into searchable incidents. Upload a real
`.log`/`.txt` file and the application parses it line by line, classifies severity,
normalizes volatile values (IDs, timestamps, numbers), groups repeated failures into
global incidents with a deterministic fingerprint, and exposes all of it through a REST
API and a React dashboard.

> **Temporary public demo:** https://sitemap-fireplace-utc-occupational.trycloudflare.com
> (Cloudflare quick tunnel to the dev server — works while the demo machine is online.
> For a permanent URL, deploy the jar and the `frontend/dist` build to a hosting provider.)

> Every dashboard number comes from the database. There is no mock data and no
> placeholder screen: an empty database shows zero-valued cards and honest empty states.

---

## Table of contents

- [Features](#features)
- [Technology stack](#technology-stack)
- [Project structure](#project-structure)
- [Architecture](#architecture)
- [How grouping works](#how-grouping-works)
- [Database design](#database-design)
- [Prerequisites](#prerequisites)
- [Setup and run](#setup-and-run)
- [REST API](#rest-api)
- [Sample logs](#sample-logs)
- [Testing and verification](#testing-and-verification)
- [Security notes](#security-notes)
- [Limitations](#limitations)
- [Future work](#future-work)
- [Screenshots checklist](#screenshots-checklist)
- [Documentation index](#documentation-index)

---

## Features

| ID | Feature | What it does |
| --- | --- | --- |
| A | Upload and parse | Validates `.log`/`.txt`, max 10 MB, rejects binary (NUL bytes), streams line by line, keeps raw lines, counts total/parsed/malformed lines. |
| B | Severity | FATAL, ERROR, WARN, INFO, DEBUG; transparent keyword inference when a level is missing, flagged with `severityInferred`. |
| C | Grouping | Normalizes messages, builds SHA-256 fingerprints, tracks count, first/last seen, severity and service; Jaccard similarity only suggests related incidents. |
| D | Incident management | List, search, filter (severity/status/date), sort (latest/count), status changes, investigation notes, underlying entries. |
| E | Dashboard | Totals, top repeated errors, errors by service, severity distribution, 14-day error trend and recent incidents — all computed from stored rows. |
| F | Upload history | Every file with status and counts; open a past upload to see the incidents it produced. |

Cross-upload behaviour: the same fingerprint in a later upload **updates** the existing
incident instead of creating a duplicate. This is asserted by an integration test.

## Technology stack

| Area | Choice | Note |
| --- | --- | --- |
| Language | Java 21 (LTS) | Use a real JDK 21; the machine used for development also had JDK 26 installed, so `JAVA_HOME` was set explicitly. |
| Framework | Spring Boot 3.5.16 | 3.5 reached OSS end of life on 30 June 2026; upgrading to Boot 4.0 is future work. |
| ORM | Spring Data JPA / Hibernate 6.6 | Version comes from the Boot BOM. |
| Database | MySQL 8 | Brief requested 8.4 LTS; the available local server was **8.0.46** (see Limitations). |
| Migrations | Flyway 11.7.2 | `flyway-core` + `flyway-mysql`, `spring.jpa.hibernate.ddl-auto=validate`. |
| Validation | Jakarta Bean Validation | DTO and query-parameter validation. |
| Build | Maven 3.10 (3.9+ required) | `mvn clean verify` builds the executable jar. |
| Tests | JUnit 5, Mockito, H2 2.x | H2 in MySQL mode, test scope, no Docker required. |
| Frontend | React 19.2.9 + Vite 8.3.8 | Plain CSS, React Router 7, Recharts 3.11. Node 24 used (20.19+/22.12+ required). |
| API testing | Postman collection in `docs/` | No authentication. |

No Lombok, Kafka, microservices, Kubernetes or authentication are used.

## Project structure

```text
bugtrace/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/bugtrace/
│       │   ├── BugTraceApplication.java
│       │   ├── config/        AppProperties, CorsConfig
│       │   ├── controller/    UploadController, IncidentController, DashboardController
│       │   ├── dto/request/   UpdateIncidentStatusRequest, CreateIncidentNoteRequest
│       │   ├── dto/response/  Records for every API response + PageResponse
│       │   ├── entity/        LogUpload, LogEntry, Incident, IncidentNote
│       │   ├── enums/         Severity, IncidentStatus, UploadStatus
│       │   ├── exception/     ApiError, GlobalExceptionHandler, custom exceptions
│       │   ├── parser/        LogLineParser, SeverityDetector, MessageNormalizer,
│       │   │                  FingerprintGenerator, SimilarityScorer, ParsedLogLine, ParseResult
│       │   ├── repository/    4 Spring Data repositories + IncidentSpecifications
│       │   └── service/       UploadService, IncidentService, DashboardService, grouping helpers
│       ├── main/resources/   application.yml, db/migration/V1__init.sql
│       └── test/             parser, service, controller and integration tests; H2 config; sample log
├── frontend/
│   ├── package.json, vite.config.js, index.html
│   └── src/                  api.js, hooks/, components/, pages/, styles.css
├── sample-logs/              three real log files + expected-results README
├── docs/                     abstract, viva Q&A, Postman collection, screenshots checklist
├── .gitignore
└── README.md
```

## Architecture

```text
 React 19 + Vite 8 (sidebar UI)
        │  REST / JSON only
        ▼
 Controller  ── validates request DTOs, maps status codes
        │
        ▼
 Service     ── transactions, upload flow, grouping, search, dashboard aggregates
        │
        ├── Parser utilities (pure, unit-tested)
        │     LogLineParser · SeverityDetector · MessageNormalizer
        │     FingerprintGenerator · SimilarityScorer
        │
        ▼
 Repository  ── parameterized Spring Data JPA queries / Specifications
        │
        ▼
 MySQL 8     ── schema owned by Flyway (V1__init.sql)
```

`GlobalExceptionHandler` is a single `@RestControllerAdvice` that converts every
exception into this JSON shape:

```json
{
  "timestamp": "2026-11-23T08:51:06.665Z",
  "status": 404,
  "error": "Not Found",
  "message": "Incident 99 not found",
  "path": "/api/incidents/99",
  "fieldErrors": {}
}
```

Validation failures put field names and messages into `fieldErrors`.

## How grouping works

1. **Parse** — a line such as
   `2026-11-23 10:15:20 ERROR [payment-service] PaymentService - Database connection failed for order 5512`
   yields timestamp (UTC `Instant`), level, service, logger and message. Continuation
   lines starting with `at `, `Caused by:`, `Suppressed:` or `... ` become the previous
   entry's stack trace.
2. **Classify** — explicit level tokens are used directly. If the level is missing,
   keyword rules infer one and `severityInferred = TRUE` is stored.
3. **Normalize** — timestamps, UUIDs, `0x…` addresses, request/trace/span/correlation
   IDs, IPv4 addresses and numbers become placeholders. Whitespace is collapsed and
   text lower-cased.
4. **Fingerprint** — `SHA-256(service | level | normalizedMessage)`, 64 hex characters.
   Level is included so WARN and ERROR never merge.
5. **Group** — WARN/ERROR/FATAL lines with the same fingerprint form one incident;
   INFO/DEBUG stay as plain entries. Count, first/last seen, sample message, severity
   and service are aggregated.
6. **Upsert** — a unique constraint on `fingerprint` makes concurrent inserts safe; on
   a duplicate key the transaction retries once and the existing incident is updated.
7. **Suggest** — token Jaccard similarity ≥ 0.45 produces "possibly related" links only;
   nothing is merged automatically.

Severity inference is intentionally simple and documented as a limitation.

## Database design

| Table | Purpose | Key relationships |
| --- | --- | --- |
| `log_upload` | One row per accepted file: UUID, filename, size, status, counts, timestamps. | 1 → N `log_entry` (`ON DELETE CASCADE`) |
| `incident` | Global problem: fingerprint, title, sample, service, severity, status, count, first/last seen, `version`. | 1 → N `log_entry` (`ON DELETE SET NULL`), 1 → N `incident_note` (`CASCADE`) |
| `log_entry` | One parsed line: upload, optional incident, line number, timestamp, severity, flags, message, stack trace, raw line. | N → 1 upload / 0..1 incident |
| `incident_note` | Investigation notes. | N → 1 incident |

Migrations live in `backend/src/main/resources/db/migration/V1__init.sql`. Hibernate
uses `ddl-auto=validate`, so entity and migration drift fails startup instead of being
silently repaired.

**Documented deviation from the brief's sample SQL:** `severity_inferred` is declared
`BIT(1)` rather than `BOOLEAN`. Hibernate 6.6 maps a Java `boolean` to MySQL `bit`, while
`BOOLEAN` is an alias for `TINYINT(1)`; with `BOOLEAN` the schema validator reported a
type mismatch. `BIT(1)` stores the same true/false value and validates correctly.

## Prerequisites

- JDK 21 and Maven 3.9+ (`java -version`, `mvn -version`)
- MySQL 8.x server (8.4 LTS recommended; local verification machine had 8.0.46)
- Node 20.19+ / 22.12+ (Node 24 LTS recommended) and npm
- Optional: Postman, or use the curl commands below

## Setup and run

### 1. Create the database and a dedicated user

```sql
CREATE DATABASE IF NOT EXISTS bugtrace
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'bugtrace'@'localhost' IDENTIFIED BY 'choose-a-strong-password';
GRANT ALL PRIVILEGES ON bugtrace.* TO 'bugtrace'@'localhost';
FLUSH PRIVILEGES;
```

Do **not** commit the password. Supply it through environment variables:

```powershell
# PowerShell
$env:DB_URL='jdbc:mysql://localhost:3306/bugtrace?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8'
$env:DB_USERNAME='bugtrace'
$env:DB_PASSWORD='choose-a-strong-password'
```

```bash
# macOS / Linux
export DB_URL='jdbc:mysql://localhost:3306/bugtrace?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8'
export DB_USERNAME='bugtrace'
export DB_PASSWORD='choose-a-strong-password'
```

On startup Flyway creates the four tables and Hibernate validates them.

### 2. Start the backend

```bash
cd backend
mvn clean verify                 # compiles, runs 35 tests, builds target/*.jar
java -jar target/bugtrace-backend-1.0.0.jar
# or: mvn spring-boot:run
```

The API listens on `http://localhost:8080`. Override the port with `SERVER_PORT`.

### 3. Start the frontend

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. Vite proxies `/api` to port 8080 in development. For a
separately hosted production build, set `VITE_API_BASE_URL` to the backend origin and
run `npm run build` (output in `frontend/dist`).

### 4. Quick H2 demo without MySQL

For a local UI walkthrough you can run the app with the test profile and H2 in-memory.
H2 is deliberately test-scoped, so the packaged jar does not contain it; use the test
classpath instead:

```powershell
cd backend
$env:JAVA_HOME='C:\path\to\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -q dependency:build-classpath "-Dmdep.outputFile=cp.txt" "-Dmdep.includeScope=test"
$deps=Get-Content cp.txt -Raw
java -cp "target\classes;target\test-classes;$deps" com.bugtrace.BugTraceApplication --spring.profiles.active=test
```

`application-test.yml` uses `MODE=MySQL`, `ddl-auto=create-drop` and Flyway disabled.
This is for demonstration only; production schema verification requires MySQL.

## REST API

All list endpoints return the same envelope:
`{ "content": [...], "page": 0, "size": 20, "totalElements": 13, "totalPages": 1, "first": true, "last": true }`

| Method | Path | Purpose | Success / error codes |
| --- | --- | --- | --- |
| POST | `/api/uploads` | Multipart upload (`file`) | 201 / 400, 413, 415 |
| GET | `/api/uploads?page=0&size=20` | Upload history | 200 / 400 |
| GET | `/api/uploads/{id}` | Upload detail + its incidents | 200 / 404 |
| GET | `/api/incidents?q=&severity=&status=&from=&to=&sort=&page=&size=` | Search and filter | 200 / 400 |
| GET | `/api/incidents/{id}` | Incident detail, notes, related | 200 / 404 |
| GET | `/api/incidents/{id}/entries?page=0&size=20` | Underlying log entries | 200 / 404 |
| PATCH | `/api/incidents/{id}/status` | Body `{"status":"INVESTIGATING"}` | 200 / 400, 404 |
| POST | `/api/incidents/{id}/notes` | Body `{"noteText":"..."}` | 201 / 400, 404 |
| GET | `/api/dashboard/stats` | All dashboard metrics | 200 |

Example upload and incident response:

```bash
curl -F "file=@sample-logs/sample-app.log" http://localhost:8080/api/uploads
```

```json
{
  "id": 1,
  "originalFilename": "sample-app.log",
  "status": "COMPLETED",
  "totalLines": 18,
  "parsedEntries": 13,
  "malformedEntries": 1,
  "incidentCount": 5,
  "uploadedAt": "2026-11-23T09:11:56.164Z"
}
```

```json
{
  "id": 2,
  "title": "Database connection failed for order 5512",
  "serviceName": "payment-service",
  "severity": "ERROR",
  "status": "OPEN",
  "occurrenceCount": 3,
  "firstSeen": "2026-11-23T10:15:23Z",
  "lastSeen": "2026-11-23T10:15:29Z"
}
```

A ready-to-import collection is at `docs/BugTrace.postman_collection.json`.

## Sample logs

Three real sample files are in `sample-logs/`. Full expected counts are documented in
`sample-logs/README.md`; the highlights:

| File | Lines | Parsed | Malformed | Incidents |
| --- | ---: | ---: | ---: | ---: |
| `sample-app.log` | 18 | 13 | 1 | 5 |
| `payment-timeouts.log` | 11 | 8 | 1 | 4 |
| `auth-failures.log` | 6 | 6 | 0 | 4 |

After all three uploads the dashboard reported 35 lines, 27 parsed entries,
13 incidents, 12 ERROR and 2 FATAL entries. These numbers were produced by actually
uploading the files, not by hand-written fixture data.

## Testing and verification

```bash
cd backend
mvn clean verify
cd ../frontend
npm run build
```

Actual output from this project on 2026-11-23:

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- com.bugtrace.controller.IncidentControllerTest
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- com.bugtrace.integration.UploadFlowIntegrationTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- com.bugtrace.parser.FingerprintGeneratorTest
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 -- com.bugtrace.parser.LogLineParserTest
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- com.bugtrace.parser.MessageNormalizerTest
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- com.bugtrace.parser.SeverityDetectorTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- com.bugtrace.parser.SimilarityScorerTest
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- com.bugtrace.service.IncidentGrouperTest
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- com.bugtrace.service.IncidentServiceTest
Tests run: 35, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Frontend build:

```text
vite v8.3.8 building client environment for production...
✓ 434 modules transformed.
dist/index.html                   0.57 kB
dist/assets/index-*.css           9.98 kB
dist/assets/index-*.js          720.30 kB (gzip: 209.48 kB)
✓ built in 486ms
```

Tests cover: valid and malformed parsing, stack traces, severity detection and
inference, normalization stability, fingerprint stability and level separation,
duplicate grouping, service behaviour (Mockito), controller validation/status codes and
the full upload → incident → note → dashboard HTTP flow on H2, including the
same-fingerprint-across-two-uploads case.

### Real MySQL verification (completed)

Because no credentials for the pre-installed MySQL service were available, an isolated
MySQL 8.0.46 instance was initialized with a fresh data directory on port 3307. The
packaged jar was started with `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` pointing to it:

```text
Successfully validated 1 migration
Migrating schema `bugtrace` to version "1 - init"
Successfully applied 1 migration to schema `bugtrace`, now at version v1
Started BugTraceApplication in 8.041 seconds
```

`SHOW TABLES` confirmed `flyway_schema_history`, `incident`, `incident_note`, `log_entry`
and `log_upload`. A second start against the existing schema proved `ddl-auto=validate`:

```text
Successfully validated 1 migration
Current version of schema `bugtrace`: 1
Schema `bugtrace` is up to date. No migration necessary.
Started BugTraceApplication in 7.643 seconds
```

All three sample logs were uploaded to real MySQL (HTTP 201) and the dashboard returned
the same values as H2: 3 uploads, 35 lines, 27 parsed entries, 2 malformed lines,
13 incidents, 12 ERROR and 2 FATAL entries.

## Security notes

- Only `.log`/`.txt` extensions and text-ish content types are accepted; size is capped
  at 10 MB at both Spring and application level.
- Uploaded bytes are never executed or written to disk; they are streamed and discarded.
- Filenames are sanitized by stripping any client path, removing control characters and
  truncating to 255 characters, so path traversal is not possible.
- Binary files containing NUL bytes are rejected before parsing.
- All JPA queries are parameterized; dynamic filters use Specifications, not string
  concatenation.
- Request DTOs, enums and pagination bounds are validated; errors never leak stack traces
  (`server.error.include-stacktrace=never`).
- Database credentials come from environment variables and are git-ignored.
- Notes and messages are rendered as text, not HTML.
- **Honest limitation:** there is no authentication or authorization. Any client that
  can reach port 8080 can read and modify data. Do not expose this app to the internet.

## Limitations

- **MySQL verification has been completed on 8.0.46** (temporary isolated instance on
  port 3307): Flyway applied `V1__init.sql`, Hibernate `ddl-auto=validate` passed on both
  the first boot and a restart, and all sample-log uploads worked. The pre-installed
  MySQL service had no known credentials, so a separate instance was used.
- The verified server is MySQL **8.0.46**, not the 8.4 LTS requested in the brief. The
  migration should be re-checked on 8.4 LTS when that version is available.
- Spring Boot **3.5.16** is past OSS end of life (30 June 2026) as noted in the brief.
- H2 and MySQL differ in some DDL and NULL/last-inserted-ID behaviour; tests use
  Hibernate-generated schema with Flyway disabled.
- Processing is **synchronous**; large files block the request until parsing finishes.
- MySQL `AUTO_INCREMENT` limits Hibernate JDBC batching, so very large files are slower;
  the 10 MB cap mitigates this.
- Search uses `LIKE %term%` and cannot use a normal index; fine at student scale.
- Keyword-based severity inference is heuristic and will not understand every message.
- Related-incident suggestions are heuristic and are never merged automatically.
- The frontend production bundle emits Vite's 500 kB chunk warning because Recharts is
  large; it still builds and runs correctly.

## Future work

1. Upgrade to Spring Boot 4.0 when stable toolchains are available.
2. Re-run the Flyway/validate check against MySQL 8.4 LTS when that version is available.
3. Add `FULLTEXT` indexes for message search.
4. Move upload processing to an asynchronous queue with progress polling.
5. Add authentication (Spring Security) and per-user workspaces.
6. Add alerting rules, CSV export and saved dashboard filters.
7. Code-split the Recharts bundle to remove the chunk-size warning.

## Screenshots checklist

Capture these after uploading the three sample logs (`docs/screenshots/`):

- [ ] `01-overview-empty.png` — dashboard with an empty database and upload call to action.
- [ ] `02-upload.png` — drag-and-drop screen with a processing summary.
- [x] `03-overview-populated.png` — metric cards, trend line, severity pie, service bars.
- [ ] `04-incidents.png` — filtered incident table with severity/status badges.
- [x] `05-incident-detail.png` — metadata, status selector, occurrence timeline and stack trace.
- [ ] `06-notes.png` — investigation notes added to an incident.
- [ ] `07-upload-history.png` — list of the three uploads and their counts.
- [ ] `08-upload-detail.png` — one upload with its incident list.
- [ ] `09-mobile.png` — responsive sidebar/navigation at phone width.

## Documentation index

- `docs/ABSTRACT.md` — abstract, problem statement, objectives and scope.
- `docs/VIVA_QUESTIONS.md` — 23 viva questions with answers.
- `docs/BugTrace.postman_collection.json` — importable Postman collection.
- `docs/screenshots/` — screenshot folder (checklist above).
- `sample-logs/README.md` — verified expected counts for every sample file.
