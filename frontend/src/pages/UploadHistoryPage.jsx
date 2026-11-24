import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { UploadStatusBadge } from '../components/Badges';
import Pagination from '../components/Pagination';
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../components/States';
import useAsync from '../hooks/useAsync';
import { formatBytes, formatDateTime, formatNumber } from '../utils';

export default function UploadHistoryPage() {
  const [page, setPage] = useState(0);
  const { data, error, loading, reload } = useAsync(
    () => api.listUploads({ page, size: 20 }),
    [page],
  );

  return (
    <div className="page">
      <PageHeader
        eyebrow="Data intake"
        title="Upload history"
        description="Every processed file, its real line counts and the incidents it produced."
        actions={
          <Link className="button button-primary" to="/upload">
            Upload another log
          </Link>
        }
      />

      {loading ? <LoadingState label="Loading upload history…" /> : null}
      {error ? <ErrorState message={error.message} onRetry={reload} /> : null}

      {data && !loading ? (
        <section className="panel table-panel">
          <h2>{formatNumber(data.totalElements)} upload(s)</h2>
          {data.content.length === 0 ? (
            <EmptyState
              title="No uploads yet"
              message="Uploaded files will appear here with their parse results."
              action={
                <Link className="button button-primary" to="/upload">
                  Upload a log file
                </Link>
              }
            />
          ) : (
            <>
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>File</th>
                      <th>Status</th>
                      <th>Uploaded at</th>
                      <th className="numeric">Lines</th>
                      <th className="numeric">Parsed</th>
                      <th className="numeric">Malformed</th>
                      <th className="numeric">Incidents</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.content.map((upload) => (
                      <tr key={upload.id}>
                        <td>
                          <Link to={`/uploads/${upload.id}`} className="table-link">
                            {upload.originalFilename}
                          </Link>
                          <div className="cell-subtitle">
                            {formatBytes(upload.fileSizeBytes)} · #{upload.id}
                          </div>
                        </td>
                        <td><UploadStatusBadge status={upload.status} /></td>
                        <td>{formatDateTime(upload.uploadedAt)}</td>
                        <td className="numeric">{formatNumber(upload.totalLines)}</td>
                        <td className="numeric">{formatNumber(upload.parsedEntries)}</td>
                        <td className="numeric">{formatNumber(upload.malformedEntries)}</td>
                        <td className="numeric">{formatNumber(upload.incidentCount)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination
                page={data.page}
                totalPages={data.totalPages}
                totalElements={data.totalElements}
                onPageChange={setPage}
              />
            </>
          )}
        </section>
      ) : null}
    </div>
  );
}
