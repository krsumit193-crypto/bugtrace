/**
 * Display formatters shared by every page.
 *
 * All `formatX` helpers return '—' for missing or invalid values instead of
 * printing "Invalid Date" / "NaN", because the dashboard must stay honest when
 * a field is genuinely absent in the database.
 */

const dateTimeFormatter = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
});

const dateFormatter = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
});

const numberFormatter = new Intl.NumberFormat();

/** @param {string|number|Date|null|undefined} value - ISO timestamp or epoch ms */
export function formatDateTime(value) {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : dateTimeFormatter.format(date);
}

/** @param {string|number|Date|null|undefined} value - date to format (no time) */
export function formatDate(value) {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : dateFormatter.format(date);
}

/** @param {number|null|undefined} value - count to format with locale grouping */
export function formatNumber(value) {
  return numberFormatter.format(value ?? 0);
}

/**
 * Render a duration in minutes as a compact human value.
 * @param {number|null|undefined} minutes - duration in minutes
 * @returns {string} e.g. `42 min`, `3h 15m`, `2d 4h`, or `—` when unknown
 */
export function formatDuration(minutes) {
  if (minutes === null || minutes === undefined || Number.isNaN(Number(minutes))) {
    return '—';
  }
  const total = Math.max(0, Math.round(Number(minutes)));
  if (total < 60) {
    return `${total} min`;
  }
  const hours = Math.floor(total / 60);
  if (hours < 24) {
    const rest = total % 60;
    return rest ? `${hours}h ${rest}m` : `${hours}h`;
  }
  const days = Math.floor(hours / 24);
  const restHours = hours % 24;
  return restHours ? `${days}d ${restHours}h` : `${days}d`;
}

/** @param {number|null|undefined} bytes - file size; rendered in the largest fitting unit */
export function formatBytes(bytes) {
  if (!bytes) {
    return '0 B';
  }
  const units = ['B', 'KB', 'MB', 'GB'];
  const exponent = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const size = bytes / 1024 ** exponent;
  return `${size.toFixed(exponent === 0 ? 0 : 1)} ${units[exponent]}`;
}

export function shortId(value) {
  return typeof value === 'string' && value.length > 12
    ? `${value.slice(0, 8)}…`
    : value;
}

/**
 * Trigger a browser download for an in-memory blob.
 *
 * Creates a temporary object URL, clicks a hidden anchor and revokes the URL
 * on the next tick so Safari has time to start the download.
 *
 * @param {Blob} blob - file contents
 * @param {string} filename - suggested name shown in the save dialog
 */
export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.rel = 'noopener';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}
