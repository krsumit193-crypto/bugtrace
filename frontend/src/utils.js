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
