import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { SeverityBadge, StatusBadge } from '../components/Badges';
import Pagination from '../components/Pagination';
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../components/States';
import useAsync from '../hooks/useAsync';
import { formatDateTime, formatNumber } from '../utils';

const initialFilters = {
  q: '',
  severity: '',
  status: '',
  from: '',
  to: '',
  sort: 'latest',
  page: 0,
  size: 20,
};

export default function IncidentsPage() {
  const [draft, setDraft] = useState(initialFilters);
  const [filters, setFilters] = useState(initialFilters);

  const { data, error, loading, reload } = useAsync(
    () => api.listIncidents(filters),
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
        title="Incidents"
        description="Repeated WARN, ERROR and FATAL lines are grouped into incidents. Click one for details."
      />

      <form className="panel filter-panel" onSubmit={apply}>
        <div className="filter-row">
          <label>
            Search message or service
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
            </select>
          </label>
          <label>
            Status
            <select value={draft.status} onChange={(event) => update('status', event.target.value)}>
              <option value="">All</option>
              <option>OPEN</option>
              <option>INVESTIGATING</option>
              <option>RESOLVED</option>
            </select>
          </label>
          <label>
            Sort by
            <select value={draft.sort} onChange={(event) => update('sort', event.target.value)}>
              <option value="latest">Latest seen</option>
              <option value="count">Occurrence count</option>
            </select>
          </label>
        </div>
        <div className="filter-row">
          <label>
            From
            <input type="date" value={draft.from} onChange={(event) => update('from', event.target.value)} />
          </label>
          <label>
            To
            <input type="date" value={draft.to} onChange={(event) => update('to', event.target.value)} />
          </label>
          <div className="filter-buttons">
            <button type="submit" className="button button-primary">
              Apply filters
            </button>
            <button type="button" className="button button-ghost" onClick={reset}>
              Reset
            </button>
          </div>
        </div>
      </form>

      {loading ? <LoadingState label="Loading incidents…" /> : null}
      {error ? <ErrorState message={error.message} onRetry={reload} /> : null}

      {data && !loading ? (
        <section className="panel table-panel">
          <h2>
            {formatNumber(data.totalElements)} incident(s) found
          </h2>
          {data.content.length === 0 ? (
            <EmptyState
              title="No incidents match these filters"
              message="Try a different search term, widen the date range or upload more logs."
            />
          ) : (
            <>
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>Incident</th>
                      <th>Service</th>
                      <th>Severity</th>
                      <th>Status</th>
                      <th className="numeric">Occurrences</th>
                      <th>Last seen</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.content.map((incident) => (
                      <tr key={incident.id}>
                        <td>
                          <Link to={`/incidents/${incident.id}`} className="table-link">
                            {incident.title}
                          </Link>
                          <div className="cell-subtitle">#{incident.id}</div>
                        </td>
                        <td>{incident.serviceName ?? 'unknown'}</td>
                        <td><SeverityBadge severity={incident.severity} /></td>
                        <td><StatusBadge status={incident.status} /></td>
                        <td className="numeric">{formatNumber(incident.occurrenceCount)}</td>
                        <td>{formatDateTime(incident.lastSeen)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination
                page={data.page}
                totalPages={data.totalPages}
                totalElements={data.totalElements}
                onPageChange={(page) => {
                  // Keep the form draft in sync with the applied filters so
                  // the form always reflects the page currently on screen.
                  // (Applying a *new* filter still resets to page 0 by design.)
                  setDraft((current) => ({ ...current, page }));
                  setFilters((current) => ({ ...current, page }));
                }}
              />
            </>
          )}
        </section>
      ) : null}
    </div>
  );
}
