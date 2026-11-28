import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, exportIncidents } from '../api';
import { SeverityBadge, SnoozeBadge, StatusBadge } from '../components/Badges';
import Pagination from '../components/Pagination';
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../components/States';
import useAsync from '../hooks/useAsync';
import { downloadBlob, formatDateTime, formatNumber } from '../utils';

const STATUSES = ['OPEN', 'INVESTIGATING', 'RESOLVED'];

const initialFilters = {
  q: '',
  severity: '',
  status: '',
  tag: '',
  assignee: '',
  snoozed: '',
  from: '',
  to: '',
  sort: 'latest',
  page: 0,
  size: 20,
};

function TagChips({ tags }) {
  if (!tags?.length) {
    return <span className="muted">—</span>;
  }
  return (
    <span className="tag-list">
      {tags.map((tag) => (
        <span key={tag} className="tag-chip">{tag}</span>
      ))}
    </span>
  );
}

export default function IncidentsPage() {
  const [draft, setDraft] = useState(initialFilters);
  const [filters, setFilters] = useState(initialFilters);
  const [selected, setSelected] = useState([]);
  const [bulkStatus, setBulkStatus] = useState('INVESTIGATING');
  const [bulkState, setBulkState] = useState({ busy: false, error: '', message: '' });
  const [exportState, setExportState] = useState({ busy: false, error: '', message: '' });

  const { data, error, loading, reload } = useAsync(
    () => api.listIncidents(filters),
    [filters],
  );
  const tagsState = useAsync(() => api.listIncidentTags(), []);
  const availableTags = tagsState.data ?? [];
  const assigneesState = useAsync(() => api.listIncidentAssignees(), []);
  const availableAssignees = assigneesState.data ?? [];

  function update(key, value) {
    setDraft((current) => ({ ...current, [key]: value }));
  }

  function apply(event) {
    event.preventDefault();
    setFilters({ ...draft, page: 0 });
    setSelected([]);
    setBulkState({ busy: false, error: '', message: '' });
  }

  function reset() {
    setDraft(initialFilters);
    setFilters(initialFilters);
    setSelected([]);
    setBulkState({ busy: false, error: '', message: '' });
    setExportState({ busy: false, error: '', message: '' });
  }

  function toggleSelected(id) {
    setSelected((current) =>
      current.includes(id) ? current.filter((value) => value !== id) : [...current, id]);
  }

  const pageIds = data?.content.map((incident) => incident.id) ?? [];
  const allOnPageSelected =
    pageIds.length > 0 && pageIds.every((id) => selected.includes(id));

  function toggleAllOnPage() {
    setSelected((current) => {
      if (allOnPageSelected) {
        return current.filter((id) => !pageIds.includes(id));
      }
      return [...new Set([...current, ...pageIds])];
    });
  }

  async function applyBulkStatus() {
    if (selected.length === 0) {
      return;
    }
    setBulkState({ busy: true, error: '', message: '' });
    try {
      const result = await api.bulkUpdateIncidentStatus(selected, bulkStatus);
      setSelected([]);
      setBulkState({
        busy: false,
        error: '',
        message: `${formatNumber(result.updated)} incident(s) marked ${result.status}.`,
      });
      reload();
    } catch (bulkError) {
      setBulkState({ busy: false, error: bulkError.message, message: '' });
    }
  }

  /**
   * Export uses the *applied* filters, so the file always matches the table
   * currently on screen (not unsubmitted edits in the form).
   */
  async function handleExport() {
    setExportState({ busy: true, error: '', message: '' });
    try {
      // Paging belongs to the table, not the file: export every matching row.
      const { page, size, ...exportFilters } = filters;
      const { blob, filename, rowCount, truncated } = await exportIncidents(exportFilters);
      downloadBlob(blob, filename);
      setExportState({
        busy: false,
        error: '',
        message: truncated
          ? `Downloaded the first ${formatNumber(rowCount)} incidents (export limit reached).`
          : `Downloaded ${formatNumber(rowCount)} incident(s) as CSV.`,
      });
    } catch (exportError) {
      setExportState({ busy: false, error: exportError.message, message: '' });
    }
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
              {STATUSES.map((status) => (
                <option key={status}>{status}</option>
              ))}
            </select>
          </label>
          <label>
            Tag
            <select value={draft.tag} onChange={(event) => update('tag', event.target.value)}>
              <option value="">All</option>
              {availableTags.map((tag) => (
                <option key={tag}>{tag}</option>
              ))}
            </select>
          </label>
          <label>
            Assignee
            <select value={draft.assignee} onChange={(event) => update('assignee', event.target.value)}>
              <option value="">Any</option>
              {availableAssignees.map((assignee) => (
                <option key={assignee}>{assignee}</option>
              ))}
            </select>
          </label>
          <label>
            Snooze
            <select value={draft.snoozed} onChange={(event) => update('snoozed', event.target.value)}>
              <option value="">Any</option>
              <option value="false">Active only</option>
              <option value="true">Snoozed only</option>
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
            <button
              type="button"
              className="button"
              onClick={handleExport}
              disabled={exportState.busy || loading}
              title="Download the incidents matching the applied filters"
            >
              {exportState.busy ? 'Exporting…' : 'Export CSV'}
            </button>
          </div>
        </div>
      </form>

      {exportState.error ? (
        <p className="inline-error" role="alert">
          Export failed: {exportState.error}
        </p>
      ) : null}
      {exportState.message ? (
        <p className="muted" role="status">
          {exportState.message}
        </p>
      ) : null}

      {loading ? <LoadingState label="Loading incidents…" /> : null}
      {error ? <ErrorState message={error.message} onRetry={reload} /> : null}

      {data && !loading ? (
        <section className="panel table-panel" aria-busy={loading}>
          <h2 role="status">
            {formatNumber(data.totalElements)} incident(s) found
          </h2>

          {selected.length > 0 ? (
            <div className="bulk-bar" role="region" aria-label="Bulk actions">
              <span>
                <strong>{formatNumber(selected.length)}</strong> selected
              </span>
              <label>
                Set status
                <select
                  value={bulkStatus}
                  onChange={(event) => setBulkStatus(event.target.value)}
                  disabled={bulkState.busy}
                >
                  {STATUSES.map((status) => (
                    <option key={status}>{status}</option>
                  ))}
                </select>
              </label>
              <button
                type="button"
                className="button button-primary"
                onClick={applyBulkStatus}
                disabled={bulkState.busy}
              >
                {bulkState.busy ? 'Updating…' : 'Apply to selected'}
              </button>
              <button
                type="button"
                className="button button-ghost"
                onClick={() => setSelected([])}
                disabled={bulkState.busy}
              >
                Clear selection
              </button>
            </div>
          ) : null}

          {bulkState.error ? (
            <p className="inline-error" role="alert">{bulkState.error}</p>
          ) : null}
          {bulkState.message ? (
            <p className="muted" role="status">{bulkState.message}</p>
          ) : null}

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
                      <th className="select-cell">
                        <input
                          type="checkbox"
                          className="table-select"
                          aria-label="Select all incidents on this page"
                          checked={allOnPageSelected}
                          onChange={toggleAllOnPage}
                        />
                      </th>
                      <th>Incident</th>
                      <th>Service</th>
                      <th>Severity</th>
                      <th>Status</th>
                      <th>Assignee</th>
                      <th>Tags</th>
                      <th className="numeric">Occurrences</th>
                      <th>Last seen</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.content.map((incident) => (
                      <tr key={incident.id} className={selected.includes(incident.id) ? 'row-selected' : undefined}>
                        <td className="select-cell">
                          <input
                            type="checkbox"
                            className="table-select"
                            aria-label={`Select incident ${incident.id}`}
                            checked={selected.includes(incident.id)}
                            onChange={() => toggleSelected(incident.id)}
                          />
                        </td>
                        <td>
                          <div className="title-line">
                            <Link to={`/incidents/${incident.id}`} className="table-link">
                              {incident.title}
                            </Link>
                            <SnoozeBadge snoozed={incident.snoozed} until={incident.snoozedUntil} />
                          </div>
                          <div className="cell-subtitle">
                            #{incident.id}
                            {incident.snoozed
                              ? ` · until ${formatDateTime(incident.snoozedUntil)}`
                              : ''}
                          </div>
                        </td>
                        <td>{incident.serviceName ?? 'unknown'}</td>
                        <td><SeverityBadge severity={incident.severity} /></td>
                        <td><StatusBadge status={incident.status} /></td>
                        <td>{incident.assignee ?? <span className="muted">—</span>}</td>
                        <td><TagChips tags={incident.tags} /></td>
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
                  setSelected([]);
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
