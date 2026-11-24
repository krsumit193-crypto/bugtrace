# Abstract

BugTrace is a web application that turns raw application log files into actionable
incident information. Developers upload a `.log` or `.txt` file; the backend validates
it, streams it line by line, classifies each line by severity, normalizes variable
values such as timestamps and request IDs, and groups repeated failures into global
incidents using a deterministic SHA-256 fingerprint. The same failure seen in a later
upload updates the existing incident's occurrence count and last-seen time instead of
creating a duplicate.

The system is built as a layered Java 21 / Spring Boot 3.5.16 REST application backed
by MySQL 8 and Flyway migrations. A React 19 + Vite 8 frontend calls the REST API only;
all dashboard numbers are aggregates computed from stored log entries, so an empty
database produces honest zero values and meaningful empty states rather than mock data.
Incidents can be searched, filtered, sorted, assigned a status and annotated with
investigation notes. A token-similarity scorer suggests possibly related incidents but
never merges them automatically.

The project demonstrates a complete, testable pipeline: parsing and grouping utilities
have focused unit tests, and an H2 integration test exercises upload → parse → group →
incident detail → dashboard over real HTTP requests. The result is a portfolio-ready
application suitable for a college viva and for further extension with asynchronous
processing, full-text search and authentication.

## Problem statement

Application logs contain the evidence needed to diagnose production faults, but raw
files are repetitive, noisy and hard to search. Manually scanning thousands of lines is
slow and error-prone: the same error appears hundreds of times with different IDs and
timestamps, and there is no simple way to see which failure is most frequent, which
service is failing or whether a problem is recurring across files.

## Objectives and scope

1. Accept real `.log`/`.txt` uploads (maximum 10 MB) without storing the file on disk.
2. Parse common line formats, record malformed lines without aborting, and support
   stack-trace continuation lines.
3. Detect severity from explicit level tokens, with transparent keyword inference when
   no level is present.
4. Group repeated WARN/ERROR/FATAL messages into global incidents using normalization
   plus SHA-256 fingerprints; keep INFO/DEBUG as entries only.
5. Provide a searchable, filterable incident API and a dashboard with real aggregates.
6. Offer a responsive React UI with loading, error and empty states for every view.

**Out of scope:** authentication, microservices, Kafka, Kubernetes, distributed file
storage, alerting and automatic incident merging. These are listed as future work.
