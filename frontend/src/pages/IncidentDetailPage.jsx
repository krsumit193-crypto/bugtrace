import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api';
import { SeverityBadge, StatusBadge } from '../components/Badges';
import Pagination from '../components/Pagination';
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../components/States';
import useAsync from '../hooks/useAsync';
import { formatDateTime, formatDuration, formatNumber } from '../utils';

const STATUSES = ['OPEN', 'INVESTIGATING', 'RESOLVED'];
const MAX_TAGS = 10;

const ACTIVITY_LABELS = {
  STATUS_CHANGED: 'Status changed',
  NOTE_ADDED: 'Note added',
  TAG_ADDED: 'Tag added',
  TAG_REMOVED: 'Tag removed',
};

function timeToResolve(incident) {
  if (!incident.resolvedAt || !incident.firstSeen) {
    return null;
  }
  const minutes =
    (new Date(incident.resolvedAt).getTime() - new Date(incident.firstSeen).getTime()) / 60000;
  return Number.isFinite(minutes) && minutes >= 0 ? minutes : null;
}

export default function IncidentDetailPage() {
  const { id } = useParams();
  const incidentId = Number(id);
  const [entriesPage, setEntriesPage] = useState(0);
  const [noteText, setNoteText] = useState('');
  const [tagInput, setTagInput] = useState('');
  const [actionError, setActionError] = useState(null);
  const [busy, setBusy] = useState(false);

  const incidentState = useAsync(() => api.getIncident(incidentId), [incidentId]);
  const entriesState = useAsync(
    () => api.getIncidentEntries(incidentId, { page: entriesPage, size: 20 }),
    [incidentId, entriesPage],
  );

  async function changeStatus(status) {
    setBusy(true);
    setActionError(null);
    try {
      await api.updateIncidentStatus(incidentId, status);
      incidentState.reload();
    } catch (cause) {
      setActionError(cause.message);
    } finally {
      setBusy(false);
    }
  }

  async function submitNote(event) {
    event.preventDefault();
    if (!noteText.trim()) {
      setActionError('Note text must not be empty.');
      return;
    }
    setBusy(true);
    setActionError(null);
    try {
      await api.addIncidentNote(incidentId, noteText.trim());
      setNoteText('');
      incidentState.reload();
    } catch (cause) {
      setActionError(cause.message);
    } finally {
      setBusy(false);
    }
  }

  async function saveTags(nextTags) {
    setBusy(true);
    setActionError(null);
    try {
      await api.updateIncidentTags(incidentId, nextTags);
      incidentState.reload();
      return true;
    } catch (cause) {
      setActionError(cause.message);
      return false;
    } finally {
      setBusy(false);
    }
  }

  function addTag(event) {
    event.preventDefault();
    const tag = tagInput.trim().toLowerCase();
    if (!tag) {
      return;
    }
    if (incidentState.data.tags.includes(tag)) {
      setTagInput('');
      return;
    }
    if (incidentState.data.tags.length >= MAX_TAGS) {
      setActionError(`An incident can have at most ${MAX_TAGS} tags.`);
      return;
    }
    saveTags([...incidentState.data.tags, tag]).then((saved) => {
      if (saved) {
        setTagInput('');
      }
    });
  }

  function removeTag(tag) {
    saveTags(incidentState.data.tags.filter((value) => value !== tag));
  }

  if (incidentState.loading) {
    return <LoadingState label="Loading incident…" />;
  }
  if (incidentState.error) {
    return <ErrorState message={incidentState.error.message} onRetry={incidentState.reload} />;
  }

  const incident = incidentState.data;
  const resolutionTime = timeToResolve(incident);

  return (
    <div className="page">
      <PageHeader
        eyebrow={`Incident #${incident.id}`}
        title={incident.title}
        description="Exact fingerprint matches only. Similar incidents below are suggestions, never merged automatically."
        actions={
          <Link className="button button-ghost" to="/incidents">
            ← Back to incidents
          </Link>
        }
      />

      {actionError ? <p className="inline-error" role="alert">{actionError}</p> : null}

      <section className="panel">
        <div className="detail-grid">
          <div className="detail-row">
            <span>Fingerprint</span>
            <code className="fingerprint">{incident.fingerprint}</code>
          </div>
          <div className="detail-row">
            <span>Service</span>
            <strong>{incident.serviceName ?? 'unknown'}</strong>
          </div>
          <div className="detail-row">
            <span>Severity</span>
            <SeverityBadge severity={incident.severity} />
          </div>
          <div className="detail-row">
            <span>Occurrences</span>
            <strong>{formatNumber(incident.occurrenceCount)}</strong>
          </div>
          <div className="detail-row">
            <span>First seen</span>
            <strong>{formatDateTime(incident.firstSeen)}</strong>
          </div>
          <div className="detail-row">
            <span>Last seen</span>
            <strong>{formatDateTime(incident.lastSeen)}</strong>
          </div>
          {incident.resolvedAt ? (
            <div className="detail-row">
              <span>Resolved</span>
              <strong>
                {formatDateTime(incident.resolvedAt)}
                {resolutionTime !== null ? (
                  <span className="muted"> · {formatDuration(resolutionTime)} to resolve</span>
                ) : null}
              </strong>
            </div>
          ) : null}
          <div className="detail-row">
            <span>Status</span>
            <div className="inline-control">
              <StatusBadge status={incident.status} />
              <select
                aria-label="Change incident status"
                value={incident.status}
                disabled={busy}
                onChange={(event) => changeStatus(event.target.value)}
              >
                {STATUSES.map((status) => (
                  <option key={status}>{status}</option>
                ))}
              </select>
            </div>
          </div>
          <div className="detail-row detail-row-wide">
            <span>Tags</span>
            <div className="tag-editor">
              {incident.tags.map((tag) => (
                <span key={tag} className="tag-chip">
                  {tag}
                  <button
                    type="button"
                    className="tag-remove"
                    aria-label={`Remove tag ${tag}`}
                    disabled={busy}
                    onClick={() => removeTag(tag)}
                  >
                    ×
                  </button>
                </span>
              ))}
              <form className="tag-form" onSubmit={addTag}>
                <input
                  type="text"
                  value={tagInput}
                  maxLength={32}
                  placeholder="add tag"
                  aria-label="Add tag"
                  disabled={busy}
                  onChange={(event) => setTagInput(event.target.value)}
                />
                <button type="submit" className="button button-ghost" disabled={busy || !tagInput.trim()}>
                  Add
                </button>
              </form>
            </div>
          </div>
        </div>
        <div className="sample-block">
          <h3>Sample message</h3>
          <pre>{incident.sampleMessage}</pre>
        </div>
      </section>

      <div className="detail-columns">
        <section className="panel">
          <h2>Occurrence timeline</h2>
          <p className="panel-subtitle">Underlying log entries for this incident</p>
          {entriesState.loading ? <LoadingState label="Loading entries…" /> : null}
          {entriesState.error ? (
            <ErrorState message={entriesState.error.message} onRetry={entriesState.reload} />
          ) : null}
          {entriesState.data && !entriesState.loading ? (
            entriesState.data.content.length === 0 ? (
              <EmptyState title="No entries" message="This incident has no stored entries." />
            ) : (
              <>
                <ol className="timeline">
                  {entriesState.data.content.map((entry) => (
                    <li key={entry.id}>
                      <div className="timeline-head">
                        <strong>{formatDateTime(entry.loggedAt)}</strong>
                        <span className="muted">
                          {entry.originalFilename ?? entry.uploadFilename} · line {entry.lineNumber}
                        </span>
                      </div>
                      <p>{entry.message}</p>
                      {entry.stackTrace ? (
                        <details>
                          <summary>Stack trace</summary>
                          <pre>{entry.stackTrace}</pre>
                        </details>
                      ) : null}
                      <details>
                        <summary>Raw line</summary>
                        <pre>{entry.rawLine}</pre>
                      </details>
                    </li>
                  ))}
                </ol>
                <Pagination
                  page={entriesState.data.page}
                  totalPages={entriesState.data.totalPages}
                  totalElements={entriesState.data.totalElements}
                  onPageChange={setEntriesPage}
                />
              </>
            )
          ) : null}
        </section>

        <div className="detail-side">
          <section className="panel">
            <h2>Activity</h2>
            <p className="panel-subtitle">Status changes, notes and tag edits</p>
            {incident.activities.length === 0 ? (
              <p className="muted">No activity recorded yet.</p>
            ) : (
              <ol className="activity-list">
                {incident.activities.map((activity) => (
                  <li key={activity.id}>
                    <span
                      className={`activity-dot activity-${activity.type.toLowerCase()}`}
                      aria-hidden="true"
                    />
                    <div>
                      <div className="activity-head">
                        <strong>{ACTIVITY_LABELS[activity.type] ?? activity.type}</strong>
                        <span className="muted">{formatDateTime(activity.createdAt)}</span>
                      </div>
                      <p>{activity.detail}</p>
                    </div>
                  </li>
                ))}
              </ol>
            )}
          </section>

          <section className="panel">
            <h2>Investigation notes</h2>
            {incident.notes.length === 0 ? (
              <p className="muted">No notes yet. Add what you investigated.</p>
            ) : (
              <ul className="notes-list">
                {incident.notes.map((note) => (
                  <li key={note.id}>
                    <p>{note.noteText}</p>
                    <small>{formatDateTime(note.createdAt)}</small>
                  </li>
                ))}
              </ul>
            )}
            <form onSubmit={submitNote} className="note-form">
              <label htmlFor="note">Add a note</label>
              <textarea
                id="note"
                rows={3}
                maxLength={2000}
                value={noteText}
                onChange={(event) => setNoteText(event.target.value)}
                placeholder="What did you check? What was the root cause?"
              />
              <button type="submit" className="button button-primary" disabled={busy}>
                Save note
              </button>
            </form>
          </section>

          <section className="panel">
            <h2>Possibly related</h2>
            <p className="panel-subtitle">Token-similarity suggestions (score ≥ 0.45)</p>
            {incident.relatedIncidents.length === 0 ? (
              <p className="muted">No similar incidents found.</p>
            ) : (
              <ul className="related-list">
                {incident.relatedIncidents.map((related) => (
                  <li key={related.id}>
                    <Link to={`/incidents/${related.id}`}>{related.title}</Link>
                    <span className="muted">
                      <SeverityBadge severity={related.severity} /> · score {related.similarity}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}
