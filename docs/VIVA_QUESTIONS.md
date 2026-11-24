# Viva Questions and Answers

## Architecture

**Q1. What is BugTrace in one sentence?**
A: A layered REST application that uploads real log files, groups repeated errors into
incidents and shows investigation data on a React dashboard.

**Q2. Draw the data flow.**
A: Browser → Controller (HTTP + validation) → Service (business logic and transactions)
→ Parser utilities (pure functions) → Repository (Spring Data JPA) → MySQL. A single
`GlobalExceptionHandler` converts exceptions into one JSON error shape.

**Q3. Why are DTOs used instead of entities?**
A: Entities belong to the persistence layer and can contain lazy associations. Returning
them directly leaks the database model, can trigger lazy loading during JSON serialization
and makes the API contract unstable. Records such as `IncidentSummaryResponse` are
explicit, immutable API contracts.

**Q4. Why no Lombok?**
A: Constructors and getters are written explicitly so every student can see what the
compiler generates; it also avoids annotation-processor surprises in the viva.

## Parsing and severity

**Q5. Which log format is supported?**
A: Primary: `2026-11-23 10:15:20 ERROR [payment-service] PaymentService - Database connection failed`.
Variations: `T` separator, fractional seconds, level-only lines without a timestamp, and
lines with no level where severity is inferred. A logger name is optional.

**Q6. What happens to a malformed line?**
A: It is counted in `malformedEntries` and skipped. The upload still completes. Blank
lines are ignored, not counted as malformed.

**Q7. How are stack traces handled?**
A: A line starting with whitespace plus `at `, `Caused by:`, `Suppressed:` or `... ` is a
continuation line. It is appended to the previous entry's `stack_trace` field and is not
counted as a malformed line. Traces are capped at 1,000,000 characters and messages at
16,000 characters so MySQL TEXT/MEDIUMTEXT columns are safe.

**Q8. How is severity inferred, and how is that shown?**
A: `SeverityDetector` applies transparent keyword rules (for example "failed" → ERROR,
"out of memory" → FATAL, "retry" → WARN). The line is stored with
`severity_inferred = TRUE`, and the UI renders a separate "inferred" badge so nobody
mistakes a guess for an explicit log level. The classifier is deliberately simple and
does not understand every error.

## Grouping

**Q9. What is a fingerprint?**
A: SHA-256 hex (64 chars) of `service | level | normalizedMessage`. Because the level is
included, a WARN and an ERROR with identical text never merge. The service name is
lower-cased first.

**Q10. What does normalization do?**
A: It replaces timestamps, UUIDs, `0x…` hex addresses, request/trace/span/correlation IDs,
IPv4 addresses and numbers with stable placeholders, then lower-cases and collapses
whitespace. This makes "timeout after 3000ms" and "timeout after 5000ms" the same bug.

**Q11. Why is grouping exact rather than fuzzy?**
A: Exact fingerprint grouping is deterministic and explainable in a viva. The Jaccard
token-similarity scorer is used only for "possibly related" suggestions (score ≥ 0.45);
it never auto-merges incidents.

**Q12. What happens when the same fingerprint appears in a second upload?**
A: `upsertIncidents` finds the existing row and increments `occurrence_count`, extends
`last_seen` and possibly pulls `first_seen` earlier. One global incident remains. The
unique constraint `uk_incident_fingerprint` protects against a concurrent insert race;
the code retries once after a duplicate-key failure.

## Database

**Q13. Which tables exist and how are they related?**
A: `log_upload` 1→N `log_entry` (ON DELETE CASCADE); `incident` 1→N `log_entry`
(optional, ON DELETE SET NULL); `incident` 1→N `incident_note` (ON DELETE CASCADE).

**Q14. Why `ddl-auto=validate` with Flyway?**
A: Flyway owns the schema via `V1__init.sql`; Hibernate must not silently create or
change tables. `validate` makes the application fail fast if the entity model and the
migration disagree.

**Q15. Why is `severity_inferred` declared `BIT(1)` instead of `BOOLEAN` in the migration?**
A: Hibernate 6.6 maps Java `boolean` to MySQL `bit`. `BOOLEAN` is an alias for `TINYINT(1)`
in MySQL, and Hibernate's schema validator treated that as a type mismatch. `BIT(1)`
keeps the validation passing while expressing the same true/false value. This is a
documented deviation from the brief's sample SQL.

**Q16. Why does the project use H2 for tests?**
A: H2 in MySQL mode removes the need for Docker or a live MySQL instance in CI. The
integration test uses Hibernate-generated schema (`create-drop`) with Flyway disabled,
so it tests application behaviour rather than MySQL-specific DDL. Differences between
H2 and MySQL are documented as a limitation.

**Q17. Why isn't JDBC batching effective for very large files?**
A: MySQL `AUTO_INCREMENT` requires an immediate round trip to obtain each generated ID,
so Hibernate cannot batch those INSERTs as effectively. Entries are saved in chunks of
1000 and the 10 MB upload cap keeps this acceptable at student scale.

## API and security

**Q18. Which status codes can an upload return?**
A: 201 on success, 400 for an empty/binary file, 413 when larger than 10 MB, 415 for
an unsupported extension or content type, and 409/500-style errors are normalized by
the exception handler. Missing resources return 404 with the same JSON error shape.

**Q19. How is path traversal prevented?**
A: The client-supplied filename is sanitized: backslashes are converted to slashes and
everything before the last slash is discarded. Control characters are replaced and the
name is capped at 255 characters. The file itself is never written to disk.

**Q20. What validation exists besides file checks?**
A: Jakarta Bean Validation on request DTOs (`@NotBlank`, enum status), `@Min/@Max` on
pagination parameters, SQL parameters via Spring Data (no string concatenation), and a
uniform error JSON containing `timestamp`, `status`, `error`, `message`, `path` and
`fieldErrors`.

**Q21. Why is LIKE '%term%' acceptable here?**
A: A leading wildcard cannot use a normal B-tree index, so search scans rows. At the
expected student-scale data volume this is fine; `FULLTEXT` indexing is listed as a
future enhancement.

## Frontend

**Q22. Does the frontend contain fake data?**
A: No. Every view calls one of the nine REST endpoints. Loading, error and empty states
are explicit components. Charts are fed from `/api/dashboard/stats`, which itself only
aggregates stored rows.

**Q23. What happens with an empty database?**
A: The dashboard shows zero-valued metric cards and a "No logs analyzed yet" panel
with an upload call to action. Incident, upload and detail pages show tailored empty
messages instead of blank tables.
