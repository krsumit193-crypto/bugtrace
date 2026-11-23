const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');

export class ApiError extends Error {
  constructor(message, status, fieldErrors = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

async function request(path, options = {}) {
  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      headers: options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' },
      ...options,
    });
  } catch (error) {
    throw new ApiError(
      'Could not reach the BugTrace API. Is the Spring Boot backend running on port 8080?',
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
