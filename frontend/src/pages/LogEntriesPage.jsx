import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api';
import { SeverityBadge } from '../components/Badges';
import Pagination from '../components/Pagination';
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../components/States';
import useAsync from '../hooks/useAsync';
import { formatDateTime, formatNumber } from '../utils';

const initialFilters = {
  q: '',
  severity: '',
  serviceName: '',
  uploadId: '',
  incidentId: '',
  from: '',
  to: '',
  sort: 'latest',
  page: 0,
  size: 20,
};

/**
 * Searches raw log lines across all uploads. Incident search groups similar
 * failures; this page finds the exact line, including INFO/DEBUG entries that
 * never become incidents.
 */
export default function LogEntriesPage() {
  const [searchParams] = useSearchParams();
  const presetQuery = searchParams.get('q') ?? '';
  const [draft, setDraft] = useState({ ...initialFilters, q: presetQuery });
  const [filters, setFilters] = useState({ ...initialFilters, q: presetQuery });

  const { data, error, loading, reload } = useAsync(
    () => api.searchEntries(filters),
    [filters],
  );

  function update(key, value) {
    setDraft((current) => ({ ...current, [key]: value }));
  }

  function apply(event) {
    event.preventDefault();
    setFilters({ ...draft, page: 0 });
  }

  function reset() {
    setDraft(initialFilters);
    setFilters(initialFilters);
  }

  return (
    <div className="page">
      <PageHeader
        eyebrow="Investigation"
        title="Log entries"
        description="Full-text search over every parsed line in every upload — including INFO and DEBUG lines that never become incidents."
      />

      <form className="panel filter-panel" onSubmit={apply}>
        <div className="filter-row">
          <label>
            Search message, raw line, service or logger
            <input
              type="search"
              value={draft.q}
              onChange={(event) => update('q', event.target.value)}
              placeholder="e.g. connection failed"
            />
          </label>
          <label>
            Severity
            <select value={draft.severity} onChange={(event) => update('severity', event.target.value)}>
              <option value="">All</option>
              <option>FATAL</option>
              <option>ERROR</option>
              <option>WARN</option>
              <option>INFO</option>
              <option>DEBUG</option>
            </select>
          </label>
          <label>
            Service (partial)
            <input
              value={draft.serviceName}
              onChange={(event) => update('serviceName', event.target.value)}
              placeholder="payment-service"
            />
          </label>
          <label>
            Sort by
            <select value={draft.sort} onChange={(event) => update('sort', event.target.value)}>
              <option value="latest">Newest first</option>
              <option value="oldest">Oldest first</option>
              <option value="line">File line number</option>
            </select>
          </label>
        </div>
        <div className="filter-row">
          <label>
            Upload ID
            <input
              type="number"
              min="1"
              value={draft.uploadId}
              onChange={(event) => update('uploadId', event.target.value)}
              placeholder="any"
            />
          </label>
          <label>
            Incident ID
            <input
              type="number"
              min="1"
              value={draft.incidentId}
              onChange={(event) => update('incidentId', event.target.value)}
              placeholder="any"
            />
          </label>
          <label>
            From
            <input type="date" value={draft.from} onChange={(event) => update('from', event.target.value)} />
          </label>
          <label>
            To
            <input type="date" value={draft.to} onChange={(event) => update('to', event.target.value)} />
          </label>
          <div className="filter-buttons">
            <button type="submit" className="button button-primary">Search</button>
            <button type="button" className="button button-ghost" onClick={reset}>Reset</button>
          </div>
        </div>
      </form>

      {loading ? <LoadingState label="Searching log entries…" /> : null}
      {error ? <ErrorState message={error.message} onRetry={reload} /> : null}

      {data && !loading ? (
        <section className="panel table-panel">
          <h2>{formatNumber(data.totalElements)} log entry(s) found</h2>
          {data.content.length === 0 ? (
            <EmptyState
              title="No log entries match these filters"
              message="Try a broader search term, clear the severity filter or upload more logs."
            />
          ) : (
            <>
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>Message</th>
                      <th>Severity</th>
                      <th>Service</th>
                      <th>Source</th>
                      <th>Line</th>
                      <th>Logged at</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.content.map((entry) => (
                      <tr key={entry.id}>
                        <td className="entry-message">
                          <span>{entry.message}</span>
                          <details>
                            <summary>Details</summary>
                            <pre>{entry.rawLine}</pre>
                            {entry.stackTrace ? <pre>{entry.stackTrace}</pre> : null}
                          </details>
                          {entry.incidentId ? (
                            <Link className="entry-incident-link" to={`/incidents/${entry.incidentId}`}>
                              View incident →
                            </Link>
                          ) : null}
                        </td>
                        <td>
                          <SeverityBadge severity={entry.severity} inferred={entry.severityInferred} />
                        </td>
                        <td>{entry.serviceName ?? 'unknown'}</td>
                        <td>
                          <Link className="table-link" to={`/uploads/${entry.uploadId}`}>
                            {entry.uploadFilename}
                          </Link>
                        </td>
                        <td className="numeric">{entry.lineNumber}</td>
                        <td>{formatDateTime(entry.loggedAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination
                page={data.page}
                totalPages={data.totalPages}
                totalElements={data.totalElements}
                onPageChange={(page) => setFilters((current) => ({ ...current, page }))}
              />
            </>
          )}
        </section>
      ) : null}
    </div>
  );
}
