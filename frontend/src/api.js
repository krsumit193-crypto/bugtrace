/**
 * Thin REST client for the BugTrace backend.
 *
 * Every function here returns parsed JSON (or `null` for a 204) and throws an
 * `ApiError` for both HTTP failures and network failures. Pages therefore only
 * ever deal with one error type, which is what `useAsync` and the `ErrorState`
 * component expect.
 *
 * `VITE_API_BASE_URL` is empty in development because Vite proxies `/api` to
 * port 8080. In production it is set to the deployed backend origin.
 */
const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');

/**
 * Uniform error object used across the UI.
 * `status === 0` means the request never reached the server (network/CORS).
 */
export class ApiError extends Error {
  constructor(message, status, fieldErrors = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

/**
 * Perform one API request and normalise all failure modes into `ApiError`.
 *
 * @param {string} path - path beginning with `/api/...`
 * @param {RequestInit} [options] - standard fetch options (method, body, signal…)
 * @returns {Promise<any|null>} parsed JSON, or `null` for an empty/204 response
 * @throws {ApiError} when the server responds with a non-2xx status or is unreachable
 */
async function request(path, options = {}) {
  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      headers: options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' },
      ...options,
    });
  } catch (error) {
    throw new ApiError(
      'Could not reach the BugTrace API. Please try again in a moment.',
      0,
    );
  }

  if (response.status === 204) {
    return null;
  }

  const text = await response.text();
  let payload = null;
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch (error) {
      payload = null;
    }
  }

  if (!response.ok) {
    const message =
      payload?.message || `Request failed with status ${response.status}.`;
    throw new ApiError(message, response.status, payload?.fieldErrors ?? {});
  }

  return payload;
}

/**
 * Build a query string while dropping empty filters.
 * Empty strings are omitted so the backend never receives `?severity=&q=`.
 *
 * @param {Record<string, string|number|undefined|null>} params
 * @returns {string} e.g. `?severity=ERROR&page=0`, or `''` when nothing is set
 */
function query(params) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, value);
    }
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}

/**
 * Extract the download name from a Content-Disposition header.
 * Prefers the RFC 5987 `filename*` form when the server sends both.
 *
 * @param {string|null} value - raw header value
 * @returns {string|null} decoded filename, or `null` when absent
 */
function filenameFromDisposition(value) {
  if (!value) {
    return null;
  }
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(value);
  if (utf8) {
    return decodeURIComponent(utf8[1]);
  }
  const plain = /filename="?([^";]+)"?/i.exec(value);
  return plain ? plain[1] : null;
}

export const api = {
  dashboard: () => request('/api/dashboard/stats'),

  listIncidents: (params) => request(`/api/incidents${query(params)}`),
  getIncident: (id) => request(`/api/incidents/${id}`),
  getIncidentEntries: (id, params) =>
    request(`/api/incidents/${id}/entries${query(params)}`),
  updateIncidentStatus: (id, status) =>
    request(`/api/incidents/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    }),
  addIncidentNote: (id, noteText) =>
    request(`/api/incidents/${id}/notes`, {
      method: 'POST',
      body: JSON.stringify({ noteText }),
    }),

  listUploads: (params) => request(`/api/uploads${query(params)}`),
  getUpload: (id) => request(`/api/uploads/${id}`),
  uploadLog: (file) => {
    const form = new FormData();
    form.append('file', file);
    return request('/api/uploads', { method: 'POST', body: form });
  },
};

/**
 * Download the incidents matching the current filters as a CSV file.
 *
 * Kept outside `request()` because the response body is a binary attachment,
 * not JSON. Failures are still normalised to `ApiError`.
 *
 * @param {Record<string, string|number|undefined|null>} params - same filters as `listIncidents`
 * @returns {Promise<{blob: Blob, filename: string, rowCount: number, truncated: boolean}>}
 * @throws {ApiError} when the export fails or the API is unreachable
 */
export async function exportIncidents(params = {}) {
  let response;
  try {
    response = await fetch(`${API_BASE}/api/incidents/export${query(params)}`);
  } catch (error) {
    throw new ApiError(
      'Could not reach the BugTrace API. Please try again in a moment.',
      0,
    );
  }

  if (!response.ok) {
    const text = await response.text();
    let payload = null;
    if (text) {
      try {
        payload = JSON.parse(text);
      } catch (error) {
        payload = null;
      }
    }
    throw new ApiError(
      payload?.message || `Export failed with status ${response.status}.`,
      response.status,
      payload?.fieldErrors ?? {},
    );
  }

  const blob = await response.blob();
  return {
    blob,
    filename:
      filenameFromDisposition(response.headers.get('Content-Disposition')) ??
      'bugtrace-incidents.csv',
    rowCount: Number(response.headers.get('X-Exported-Rows') ?? 0),
    truncated: response.headers.get('X-Export-Truncated') === 'true',
  };
}

