// API client with automatic Bearer token injection.
// Token lives in Zustand authStore (memory only). Each request pulls the
// current value at send-time rather than binding to a stale closure.

import { useAuthStore } from '../stores/authStore';

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code?: string,
    public readonly details?: unknown
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  opts: { skipAuth?: boolean } = {}
): Promise<T> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
  };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  if (!opts.skipAuth) {
    const token = useAuthStore.getState().accessToken;
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
  }

  const res = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  // 204 No Content
  if (res.status === 204) {
    return undefined as T;
  }

  let parsed: unknown = null;
  const text = await res.text();
  if (text) {
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = text;
    }
  }

  if (!res.ok) {
    // Auto-clear auth on 401 so the UI drops back to login.
    if (res.status === 401 && !opts.skipAuth) {
      useAuthStore.getState().clear();
    }

    const envelope = (parsed && typeof parsed === 'object' ? parsed : {}) as Record<string, unknown>;
    throw new ApiError(
      (envelope.error as string) || res.statusText || 'Request failed',
      res.status,
      envelope.code as string | undefined,
      envelope.details
    );
  }

  return parsed as T;
}

export const api = {
  get: <T>(path: string, opts?: { skipAuth?: boolean }) => request<T>('GET', path, undefined, opts),
  post: <T>(path: string, body?: unknown, opts?: { skipAuth?: boolean }) => request<T>('POST', path, body, opts),
  put: <T>(path: string, body?: unknown, opts?: { skipAuth?: boolean }) => request<T>('PUT', path, body, opts),
  patch: <T>(path: string, body?: unknown, opts?: { skipAuth?: boolean }) => request<T>('PATCH', path, body, opts),
  delete: <T>(path: string, opts?: { skipAuth?: boolean }) => request<T>('DELETE', path, undefined, opts),
};
