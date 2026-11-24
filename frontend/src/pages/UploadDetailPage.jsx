import { Link, useParams } from 'react-router-dom';
import { api } from '../api';
import { SeverityBadge, StatusBadge, UploadStatusBadge } from '../components/Badges';
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../components/States';
import useAsync from '../hooks/useAsync';
import { formatBytes, formatDateTime, formatNumber } from '../utils';

export default function UploadDetailPage() {
  const { id } = useParams();
  const uploadId = Number(id);
  const { data, error, loading, reload } = useAsync(() => api.getUpload(uploadId), [uploadId]);

  if (loading) {
    return <LoadingState label="Loading upload details…" />;
  }
  if (error) {
    return <ErrorState message={error.message} onRetry={reload} />;
  }

  const upload = data;

  return (
    <div className="page">
      <PageHeader
        eyebrow={`Upload #${upload.id}`}
        title={upload.originalFilename}
        description="Results produced from this file. Repeated fingerprints update a global incident instead of creating a duplicate."
        actions={
          <Link className="button button-ghost" to="/uploads">
            ← Back to upload history
          </Link>
        }
      />

      <section className="panel">
        <div className="detail-grid">
          <div className="detail-row"><span>Status</span><UploadStatusBadge status={upload.status} /></div>
          <div className="detail-row"><span>Size</span><strong>{formatBytes(upload.fileSizeBytes)}</strong></div>
          <div className="detail-row"><span>Uploaded at</span><strong>{formatDateTime(upload.uploadedAt)}</strong></div>
          <div className="detail-row"><span>Completed at</span><strong>{formatDateTime(upload.completedAt)}</strong></div>
          <div className="detail-row"><span>Total lines</span><strong>{formatNumber(upload.totalLines)}</strong></div>
          <div className="detail-row"><span>Parsed entries</span><strong>{formatNumber(upload.parsedEntries)}</strong></div>
          <div className="detail-row"><span>Malformed lines</span><strong>{formatNumber(upload.malformedEntries)}</strong></div>
          <div className="detail-row"><span>Incidents in this file</span><strong>{formatNumber(upload.incidentCount)}</strong></div>
          <div className="detail-row"><span>UUID</span><code className="fingerprint">{upload.uploadUuid}</code></div>
        </div>
        {upload.errorMessage ? <p className="inline-error">{upload.errorMessage}</p> : null}
      </section>

      <section className="panel table-panel">
        <h2>Incidents found in this upload</h2>
        {upload.incidents.length === 0 ? (
          <EmptyState
            title="No incidents in this file"
            message="Only WARN, ERROR and FATAL lines create incidents. INFO and DEBUG lines are stored as entries."
          />
        ) : (
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
                {upload.incidents.map((incident) => (
                  <tr key={incident.id}>
                    <td>
                      <Link to={`/incidents/${incident.id}`} className="table-link">
                        {incident.title}
                      </Link>
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
        )}
      </section>
    </div>
  );
}
