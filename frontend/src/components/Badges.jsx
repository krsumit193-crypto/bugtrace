export function SeverityBadge({ severity, inferred = false }) {
  const value = severity ?? 'UNKNOWN';
  return (
    <span className={`badge severity-${value.toLowerCase()}`}>
      {value}
      {inferred ? <span className="badge-note">inferred</span> : null}
    </span>
  );
}

export function StatusBadge({ status }) {
  const value = status ?? 'UNKNOWN';
  return <span className={`badge status-${value.toLowerCase()}`}>{value}</span>;
}

export function SnoozeBadge({ snoozed, until }) {
  if (!snoozed) {
    return null;
  }
  const label = until ? new Date(until).toLocaleString() : null;
  return (
    <span
      className="badge snooze-active"
      title={label ? `Snoozed until ${label}` : 'Snoozed'}
    >
      SNOOZED
    </span>
  );
}

export function UploadStatusBadge({ status }) {
  const value = status ?? 'UNKNOWN';
  return <span className={`badge upload-${value.toLowerCase()}`}>{value}</span>;
}
