import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { UploadStatusBadge } from '../components/Badges';
import { EmptyState, PageHeader } from '../components/States';
import { formatBytes, formatDateTime, formatNumber } from '../utils';

const MAX_BYTES = 10 * 1024 * 1024;

export default function UploadPage() {
  const inputRef = useRef(null);
  const [file, setFile] = useState(null);
  const [dragging, setDragging] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [uploading, setUploading] = useState(false);

  function pickFile(candidate) {
    setResult(null);
    setError(null);
    if (!candidate) {
      return;
    }
    const name = candidate.name.toLowerCase();
    if (!name.endsWith('.log') && !name.endsWith('.txt')) {
      setError('Only .log and .txt files are accepted.');
      setFile(null);
      return;
    }
    if (candidate.size > MAX_BYTES) {
      setError('The file is larger than the 10 MB limit.');
      setFile(null);
      return;
    }
    if (candidate.size === 0) {
      setError('The file is empty.');
      setFile(null);
      return;
    }
    setFile(candidate);
  }

  async function submit(event) {
    event.preventDefault();
    if (!file) {
      setError('Choose a log file first.');
      return;
    }
    setUploading(true);
    setError(null);
    try {
      const uploaded = await api.uploadLog(file);
      setResult(uploaded);
      setFile(null);
      if (inputRef.current) {
        inputRef.current.value = '';
      }
    } catch (cause) {
      setError(cause.message);
    } finally {
      setUploading(false);
    }
  }

  return (
    <div className="page">
      <PageHeader
        eyebrow="Data intake"
        title="Upload logs"
        description="Files are parsed line by line on the server. Nothing is stored on disk and no data is faked."
      />

      <div className="upload-grid">
        <form className="panel" onSubmit={submit}>
          <h2>Choose a log file</h2>
          <p className="panel-subtitle">Accepted: .log or .txt · Maximum 10 MB · UTF-8 text</p>

          <div
            className={`dropzone${dragging ? ' dragging' : ''}`}
            onDragOver={(event) => {
              event.preventDefault();
              setDragging(true);
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={(event) => {
              event.preventDefault();
              setDragging(false);
              pickFile(event.dataTransfer.files?.[0]);
            }}
            onClick={() => inputRef.current?.click()}
            role="button"
            tabIndex={0}
            onKeyDown={(event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                inputRef.current?.click();
              }
            }}
          >
            <div className="dropzone-icon" aria-hidden="true">
              ↑
            </div>
            <p>{file ? file.name : 'Drag and drop a log file here'}</p>
            <p className="muted">
              {file ? `${formatBytes(file.size)} selected` : 'or click to browse your computer'}
            </p>
            <input
              ref={inputRef}
              type="file"
              accept=".log,.txt,text/plain"
              hidden
              onChange={(event) => pickFile(event.target.files?.[0])}
            />
          </div>

          {error ? <p className="inline-error" role="alert">{error}</p> : null}

          <button
            type="submit"
            className="button button-primary button-wide"
            disabled={!file || uploading}
          >
            {uploading ? 'Analyzing logs…' : 'Analyze log file'}
          </button>
        </form>

        <div className="panel">
          <h2>Processing summary</h2>
          <p className="panel-subtitle">Result of the most recent upload in this session</p>
          {result ? (
            <div className="summary">
              <div className="summary-row">
                <span>Status</span>
                <UploadStatusBadge status={result.status} />
              </div>
              <div className="summary-row">
                <span>File</span>
                <strong title={result.originalFilename}>{result.originalFilename}</strong>
              </div>
              <div className="summary-row">
                <span>Size</span>
                <strong>{formatBytes(result.fileSizeBytes)}</strong>
              </div>
              <div className="summary-row">
                <span>Total lines</span>
                <strong>{formatNumber(result.totalLines)}</strong>
              </div>
              <div className="summary-row">
                <span>Parsed entries</span>
                <strong>{formatNumber(result.parsedEntries)}</strong>
              </div>
              <div className="summary-row">
                <span>Malformed lines</span>
                <strong>{formatNumber(result.malformedEntries)}</strong>
              </div>
              <div className="summary-row">
                <span>Incidents in this file</span>
                <strong>{formatNumber(result.incidentCount)}</strong>
              </div>
              <div className="summary-row">
                <span>Uploaded at</span>
                <strong>{formatDateTime(result.uploadedAt)}</strong>
              </div>
              <div className="summary-actions">
                <Link className="button" to={`/uploads/${result.id}`}>
                  View upload details
                </Link>
                <Link className="button button-ghost" to="/incidents">
                  Browse incidents
                </Link>
              </div>
            </div>
          ) : (
            <EmptyState
              title="No upload yet"
              message="After a file is analyzed, its real counts and links appear here."
            />
          )}
        </div>
      </div>
    </div>
  );
}
