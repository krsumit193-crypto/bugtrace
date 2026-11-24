# Sample logs and expected results

These are real plain-text log files used to verify the analyzer. Upload them through the
UI (`Upload logs`) or with curl:

```bash
curl -F "file=@sample-logs/sample-app.log" http://localhost:8080/api/uploads
curl -F "file=@sample-logs/payment-timeouts.log" http://localhost:8080/api/uploads
curl -F "file=@sample-logs/auth-failures.log" http://localhost:8080/api/uploads
curl http://localhost:8080/api/dashboard/stats
```

The numbers below were produced by actually uploading all three files to a running
BugTrace backend — first with the H2 test profile and then against an isolated MySQL
8.0.46 instance on port 3307. Both runs returned the same results. They are
expectations, not mock data.

## 1. `sample-app.log` (upload #1)

| Metric | Expected |
| --- | --- |
| Status | `COMPLETED` |
| Total lines | 18 |
| Parsed entries | 13 |
| Malformed lines | 1 |
| Incidents in this file | 5 |

What it contains: INFO/WARN/ERROR/FATAL lines from `api-gateway`, `payment-service`,
`order-service` and `auth-service`; three repeats of "Database connection failed for
order …"; two repeats of "Payment failed: requestId=… timeout after …ms" (different IDs
and timeouts, normalized into one incident); a three-line stack trace attached to the
first database error; one malformed line (`this line is not a log line`); one blank line
(ignored, not malformed).

Top incident: **Database connection failed for order 5512** — `payment-service`, ERROR,
3 occurrences; its first entry carries the `PaymentRepository.java:42` stack trace.

## 2. `payment-timeouts.log` (upload #2)

| Metric | Expected |
| --- | --- |
| Status | `COMPLETED` |
| Total lines | 11 |
| Parsed entries | 8 |
| Malformed lines | 1 |
| Incidents in this file | 4 |

What it contains: two "Gateway timeout for requestId=… after …ms" lines that normalize
to one incident, two "Order creation failed for order …" lines, a stack trace, a WARN
retry, a WARN token expiry and one malformed line.

## 3. `auth-failures.log` (upload #3)

| Metric | Expected |
| --- | --- |
| Status | `COMPLETED` |
| Total lines | 6 |
| Parsed entries | 6 |
| Malformed lines | 0 |
| Incidents in this file | 4 |

What it contains: a FATAL keystore corruption with a hex address, an ERROR with a
user ID, a WARN login warning with an IP address and two "Rate limit exceeded for ip …"
lines with different IPs that normalize into one incident of count 2.

## Combined dashboard after all three uploads

| Metric | Expected |
| --- | --- |
| totalUploads | 3 |
| totalLines | 35 |
| parsedEntries | 27 |
| malformedEntries | 2 |
| totalEntries | 27 |
| uniqueIncidents | 13 |
| openIncidents | 13 (before any status change) |
| warnEntries | 5 |
| errorEntries | 12 |
| fatalEntries | 2 |
| errorsOverTime points | 14 (configurable, default) |

Most frequent incidents: Database connection failed (3), Rate limit exceeded (2),
Payment failed timeout (2), Order creation failed (2), Gateway timeout (2).

## Optional cross-upload check

Upload this extra line as `second.log` after `sample-app.log`:

```text
2026-11-24 09:00:00 ERROR [payment-service] PaymentService - Database connection failed for order 9999
```

It produces one incident in that upload, does **not** create a new global incident, and
raises the "Database connection failed" occurrence count from 3 to 4. This exact
behaviour is asserted by `UploadFlowIntegrationTest`.
