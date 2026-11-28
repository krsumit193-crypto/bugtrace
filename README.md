# BugTrace — Smart Error Log Analyzer

BugTrace turns raw application log files into searchable incidents. Upload a real
`.log`/`.txt` file and the application parses it line by line, classifies severity,
normalizes volatile values (IDs, timestamps, numbers), groups repeated failures into
global incidents with a deterministic fingerprint, and exposes all of it through a REST
API and a React dashboard.

> **Live demo (permanent):** https://bugtrace-ui.onrender.com
> Backend API: https://bugtrace-api.onrender.com
>
> The demo runs on Render with the `demo` profile (in-memory H2, no MySQL server).
> Data resets when the backend restarts; upload the sample logs again to repopulate it.

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
| D | Incident management | List, search, filter (severity/status/tag/assignee/snooze/date), sort (latest/count), status changes, bulk status updates, tags, assignee ownership, investigation notes, snooze/mute, an audit activity trail, underlying entries, and CSV export of the filtered list. |
| E | Dashboard | Totals, top repeated errors, errors by service, severity distribution, a selectable 7/14/30/90-day window with incidents/resolutions in range, MTTR, error trend and recent incidents — all computed from stored rows. |
| F | Upload history | Every file with status and counts; open a past upload to see the incidents it produced. |
| G | Log entry search | Full-text search over every stored line across all uploads, including INFO/DEBUG lines that never become incidents; filters for severity, service, upload, incident and date range, three sort orders and pagination. |

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
│       │   ├── controller/    UploadController, IncidentController, LogEntryController, DashboardController
│       │   ├── dto/request/   Status, note, tag and bulk-update request DTOs
│       │   ├── dto/response/  Records for every API response + PageResponse
│       │   ├── entity/        LogUpload, LogEntry, Incident, IncidentNote, IncidentTag, IncidentActivity
│       │   ├── enums/         Severity, IncidentStatus, UploadStatus, IncidentActivityType
│       │   ├── exception/     ApiError, GlobalExceptionHandler, custom exceptions
│       │   ├── parser/        LogLineParser, SeverityDetector, MessageNormalizer,
│       │   │                  FingerprintGenerator, SimilarityScorer, ParsedLogLine, ParseResult
│       │   ├── repository/    6 Spring Data repositories + IncidentSpecifications,
│       │   │                  LogEntrySpecifications
│       │   └── service/       UploadService, IncidentService, LogEntrySearchService,
│       │                      DashboardService, grouping helpers
│       ├── main/resources/   application.yml, db/migration/V1–V4 SQL migrations
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
 MySQL 8     ── schema owned by Flyway (V1__init.sql, V2__triage.sql)
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
| `incident` | Global problem: fingerprint, title, sample, service, severity, status, count, first/last seen, `resolved_at`, `version`. | 1 → N `log_entry` (`ON DELETE SET NULL`), 1 → N `incident_note` / `incident_tag` / `incident_activity` (`CASCADE`) |
| `log_entry` | One parsed line: upload, optional incident, line number, timestamp, severity, flags, message, stack trace, raw line. | N → 1 upload / 0..1 incident |
| `incident_note` | Investigation notes. | N → 1 incident |
| `incident_tag` | Lowercase labels (`database`, `payment`) with a unique `(incident_id, tag)` pair. | N → 1 incident |
| `incident_activity` | Append-only audit trail for status changes, notes and tag edits. | N → 1 incident |

Migrations live in `backend/src/main/resources/db/migration/`: `V1__init.sql` creates the
core schema and `V2__triage.sql` adds tags, the activity trail and `resolved_at`. Hibernate
uses `ddl-auto=validate`, so entity and migration drift fails startup instead of being
silently repaired.

`resolved_at` is set when an incident moves to `RESOLVED` and cleared when it is reopened.
That keeps MTTR a single indexed column read instead of a status-history join, at the cost
of only tracking the **current** resolution (see Limitations).

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

On startup Flyway creates the six tables and Hibernate validates them.

### 2. Start the backend

```bash
cd backend
mvn clean verify                 # compiles, runs 60 tests, builds target/*.jar
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
| GET | `/api/incidents?q=&severity=&status=&tag=&assignee=&snoozed=&from=&to=&sort=&page=&size=` | Search and filter (`assignee` is case-insensitive, `snoozed=true/false`) | 200 / 400 |
| GET | `/api/incidents/tags` | Distinct tags for filters and suggestions | 200 |
| GET | `/api/incidents/assignees` | Distinct assignees for the owner filter | 200 |
| GET | `/api/incidents/export?q=&severity=&status=&tag=&assignee=&snoozed=&from=&to=&sort=` | Filtered incidents as a CSV download (includes `assignee`) | 200 / 400 |
| GET | `/api/incidents/{id}` | Incident detail, tags, notes, activity, related | 200 / 404 |
| GET | `/api/incidents/{id}/entries?page=0&size=20` | Underlying log entries | 200 / 404 |
| GET | `/api/entries?q=&severity=&serviceName=&uploadId=&incidentId=&from=&to=&sort=&page=&size=` | Search raw log lines across all uploads (`sort=latest/oldest/line`) | 200 / 400 |
| PATCH | `/api/incidents/{id}/status` | Body `{"status":"INVESTIGATING"}` | 200 / 400, 404 |
| PATCH | `/api/incidents/{id}/snooze` | Body `{"hours":24}` (1–720), mutes the incident | 200 / 400, 404 |
| DELETE | `/api/incidents/{id}/snooze` | Wakes a snoozed incident immediately | 200 / 404 |
| PATCH | `/api/incidents/{id}/assignee` | Body `{"assignee":"alice"}` (1–100 characters, trimmed) | 200 / 400, 404 |
| DELETE | `/api/incidents/{id}/assignee` | Clears the current owner | 200 / 404 |
| PATCH | `/api/incidents/bulk-status` | Body `{"ids":[1,2],"status":"RESOLVED"}` (max 100 ids) | 200 / 400, 404 |
| PUT | `/api/incidents/{id}/tags` | Body `{"tags":["database","payment"]}` (replaces the set) | 200 / 400, 404 |
| POST | `/api/incidents/{id}/notes` | Body `{"noteText":"..."}` | 201 / 400, 404 |
| GET | `/api/dashboard/stats?days=14` | All dashboard metrics for a 1–90 day window | 200 / 400 |

### Entry search

`GET /api/entries` answers a different question from incident search: which exact line said
this. The free-text term is matched case-insensitively (with `LIKE` wildcards escaped)
against the parsed message, the raw line, the service and the logger, so INFO and DEBUG
entries that never became incidents are searchable too. Severity, partial service name,
upload id, incident id and an inclusive date range (`from`/`to`, interpreted in the
configured `app.log.timezone`) combine into one query; `sort=line` restores original file
order. Paging is capped at 100 rows per request, and an inverted range returns `400`
instead of a silent empty page. The **Log entries** page exposes the same filters and
links each hit back to its upload and, when present, its incident.

### Triage workflow

Tags are lowercase, 1–32 characters of `a-z`, `0-9`, `-` or `_`, de-duplicated per incident
and capped at 10. `PUT /api/incidents/{id}/tags` replaces the complete set, so removing a
tag is just omitting it from the list. Bulk status updates are all-or-nothing: if any id is
missing the whole request fails with `404` instead of applying a partial change.

Every status change, note, ownership change and tag addition/removal appends a row to
`incident_activity`, which the detail page renders as the **Activity** panel. `resolved_at`
and the dashboard MTTR use the same status transition, so the audit trail and the metric
cannot disagree.

### Ownership

`PATCH /api/incidents/{id}/assignee` with `{"assignee":"alice"}` sets a free-text owner
(1–100 characters, trimmed); `DELETE` clears it. Re-applying the same owner is a no-op,
so the audit trail only records real changes. The incident list and CSV export accept
`assignee=alice` as a case-insensitive exact match, and `GET /api/incidents/assignees`
returns the distinct owners used by the filter dropdown.

### Snoozing

`PATCH /api/incidents/{id}/snooze` with `{"hours":24}` mutes an incident for 1–720
hours. Snoozed incidents stay in the database and keep accumulating occurrences, but
`GET /api/incidents?snoozed=false` returns only active ones and `snoozed=true` returns
only muted ones. Expiry needs no scheduler: every query compares `snoozed_until` with
the current time, so an expired snooze is active again automatically. `DELETE` on the
same path wakes an incident early. Snoozing a `RESOLVED` incident is rejected, and
resolving an incident clears any snooze. Both actions are recorded in the activity trail,
and the dashboard shows the number of currently snoozed incidents.

### CSV export

`GET /api/incidents/export` accepts the same filters as the list endpoint and
returns a UTF-8 CSV file (with a BOM, so Excel opens it correctly):

```bash
curl -o incidents.csv "http://localhost:8080/api/incidents/export?severity=ERROR&sort=count"
```

- `Content-Disposition: attachment; filename="bugtrace-incidents-YYYYMMDD.csv"` is set.
- `X-Exported-Rows` reports how many rows were written and `X-Export-Truncated: true`
  means the 5000-row cap was reached.
- Log text is untrusted, so cells beginning with `=`, `+`, `-` or `@` are prefixed
  with an apostrophe to block spreadsheet formula injection.
- In the dashboard, **Export CSV** uses the filters currently applied to the table,
  not unsaved edits in the form.

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

Actual output from this project on 2026-11-28:

```text
Tests run: 19, Failures: 0, Errors: 0, Skipped: 0 -- com.bugtrace.controller.IncidentControllerTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- com.bugtrace.controller.LogEntryControllerTest
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- com.bugtrace.integration.LogEntrySearchIntegrationTest
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- com.bugtrace.integration.UploadFlowIntegrationTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- com.bugtrace.parser.FingerprintGeneratorTest
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 -- com.bugtrace.parser.LogLineParserTest
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- com.bugtrace.parser.MessageNormalizerTest
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- com.bugtrace.parser.SeverityDetectorTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- com.bugtrace.parser.SimilarityScorerTest
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- com.bugtrace.service.IncidentCsvExporterTest
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- com.bugtrace.service.IncidentGrouperTest
Tests run: 29, Failures: 0, Errors: 0, Skipped: 0 -- com.bugtrace.service.IncidentServiceTest
Tests run: 86, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Frontend build:

```text
vite v8.3.8 building client environment for production...
✓ 435 modules transformed.
dist/index.html                   0.57 kB │ gzip:   0.35 kB
dist/assets/index-*.css          12.81 kB │ gzip:   3.37 kB
dist/assets/index-*.js          737.91 kB │ gzip: 213.41 kB
✓ built in 2.75s
```

Tests cover: valid and malformed parsing, stack traces, severity detection and
inference, normalization stability, fingerprint stability and level separation,
duplicate grouping, service behaviour (Mockito), tag normalization and validation,
bulk status semantics, activity recording, controller validation/status codes and
the full upload → incident → tag → bulk-resolve → dashboard HTTP flow on H2,
including the same-fingerprint-across-two-uploads case. Entry-search tests assert
combined text/severity/service/upload/date filters, INFO-line visibility, `sort=line`
ordering, paging limits and the inverted-range `400`; the two integration classes use
private H2 databases so their counts cannot leak into each other.

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
- **MTTR tracks the current resolution only.** Reopening an incident clears `resolved_at`, so
  a later resolution replaces the earlier one; there is no immutable status-history table.
- `V2__triage.sql` was validated by Hibernate against the H2 schema used in tests. It has
  not yet been replayed against a live MySQL 8 server (the V1 verification below predates
  tags and activities).
- The frontend production bundle emits Vite's 500 kB chunk warning because Recharts is
  large; it still builds and runs correctly.

## Future work

1. Upgrade to Spring Boot 4.0 when stable toolchains are available.
2. Re-run the Flyway/validate check against MySQL 8.4 LTS when that version is available.
3. Add `FULLTEXT` indexes for message search.
4. Move upload processing to an asynchronous queue with progress polling.
5. Add authentication (Spring Security) and per-user workspaces.
6. Add alerting rules, saved dashboard filters and scheduled reports.
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
- [x] `10-log-entries.jpg` — raw log entry search with severity filter and incident links.

## Recent improvements

A small frontend quality pass was applied after the first release:

- `api.js`, `useAsync.js`, `utils.js` and `Pagination.jsx` now carry JSDoc comments
  explaining the error contract, race-cancellation, display fallbacks and labels.
- `useAsync` also catches **synchronous** throws from its loader, so a broken
  caller lands in the error state instead of crashing the page.
- The upload dropzone has an accessible name and no longer scrolls the page when
  activated with the Space key.
- Pagination buttons have explicit `aria-label`s and their summaries are
  `aria-live` regions.
- The incident list keeps its filter draft in sync with the displayed page.
- The dashboard no longer prints "Most recent incident seen at -" when uploads
  exist but none produced incidents.

Follow-up: incident CSV export.

- Backend `GET /api/incidents/export` reuses the exact list filters, caps output at
  5000 rows and reports truncation via headers.
- `IncidentCsvExporter` follows RFC 4180 quoting, writes CRLF + BOM and neutralises
  spreadsheet formula injection in untrusted log text.
- The incidents page gained an **Export CSV** button with busy/error/success states;
  failures render in an `role="alert"` region and successful downloads announce the
  row count.
- Twelve new tests cover CSV escaping, the export endpoint headers and service-level
  truncation/validation; the upload integration test now also downloads the CSV.
  `mvn test` and `npm run build` still pass.

Build still passes with `npm run build` (the documented Recharts chunk warning remains).

Follow-up: triage workflow and dashboard ranges.

- Incidents now carry up to 10 lowercase tags. The list has a tag filter, a tag column and
  multi-select checkboxes with a bulk status toolbar (all-or-nothing, max 100 ids).
- The detail page has an inline tag editor, a **Resolved** row with time-to-resolve and an
  **Activity** panel fed by an append-only audit trail (status, notes, tags).
- The dashboard accepts `?days=7|14|30|90` and adds incidents in range, resolutions in
  range and MTTR. MTTR is the mean first-seen → resolved time for incidents resolved
  inside the selected window; it renders `—` when there are none.
- Backend: `V2__triage.sql`, `IncidentTag`/`IncidentActivity` entities, two repositories,
  a correlated-subquery tag filter, and status transitions that maintain `resolved_at`.
- Frontend: new API methods, tag chips, selection/bulk states, activity timeline, range
  selector and `formatDuration`, all with busy/error/empty states.
- Tests grew from 35 to 60: tag normalization/removal, bulk status counting and missing-id
  failure, resolved-at clearing on reopen, controller validation, and the integration test
  now runs tag → filter → bulk resolve → activity → ranged dashboard. `mvn test` and
  `npm run build` pass.

Follow-up: global log entry search.

- New `GET /api/entries` endpoint searches the `log_entry` table directly, so raw lines can
  be found without going through an incident. Free text matches message, raw line, service
  and logger; severity, service, upload, incident and date range filters compose, with
  `latest`/`oldest`/`line` sort orders and paging capped at 100.
- `LogEntrySpecifications` builds only parameterized `LIKE`/equality predicates and escapes
  user-supplied wildcards, mirroring the incident filter behaviour.
- `LogEntryResponse` now exposes `incidentId`, letting the UI link a hit to its incident.
- The frontend gained a **Log entries** page (sidebar + `/entries` route) with a collapsible
  raw line/stack trace view and links to the source upload, plus a "Search all entries"
  shortcut from incident detail.
- Tests grew from 70 to 75: four MockMvc tests for filter delegation, invalid severity and
  paging validation, and one H2 integration test covering every filter, INFO-only lines,
  original-order sorting and inverted-range rejection. Both integration classes now use
  separate in-memory databases after the shared one caused cross-test count leakage.
  `mvn clean verify` → 75/75 pass; `npm run build` succeeds.

## Documentation index

- `docs/ABSTRACT.md` — abstract, problem statement, objectives and scope.
- `docs/VIVA_QUESTIONS.md` — 23 viva questions with answers.
- `docs/BugTrace.postman_collection.json` — importable Postman collection.
- `docs/screenshots/` — screenshot folder (checklist above).
- `sample-logs/README.md` — verified expected counts for every sample file.
