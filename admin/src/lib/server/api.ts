import { ApiError } from './guards';

const API_BASE = process.env.API_URL || 'http://localhost:8080';

export async function apiGet<T>(path: string, token: string): Promise<T> {
	const res = await fetch(`${API_BASE}${path}`, {
		headers: { Authorization: `Bearer ${token}` }
	});
	if (!res.ok) throw new ApiError(res.status, `API error: ${res.status} ${res.statusText}`);
	return res.json();
}

export async function apiPost<T>(path: string, token: string, body?: unknown): Promise<T> {
	const res = await fetch(`${API_BASE}${path}`, {
		method: 'POST',
		headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
		body: body ? JSON.stringify(body) : undefined
	});
	if (!res.ok) throw new ApiError(res.status, `API error: ${res.status} ${res.statusText}`);
	return res.json();
}

export async function apiPatch<T>(path: string, token: string, body: unknown): Promise<T> {
	const res = await fetch(`${API_BASE}${path}`, {
		method: 'PATCH',
		headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
		body: JSON.stringify(body)
	});
	if (!res.ok) throw new ApiError(res.status, `API error: ${res.status} ${res.statusText}`);
	return res.json();
}

export async function apiDelete(path: string, token: string): Promise<void> {
	const res = await fetch(`${API_BASE}${path}`, {
		method: 'DELETE',
		headers: { Authorization: `Bearer ${token}` }
	});
	if (!res.ok) throw new ApiError(res.status, `API error: ${res.status} ${res.statusText}`);
}

/** Forwards a multipart FormData body (e.g. file upload). Do NOT set Content-Type —
 * fetch derives the multipart boundary from the FormData automatically. */
export async function apiPostForm<T>(path: string, token: string, form: FormData): Promise<T> {
	const res = await fetch(`${API_BASE}${path}`, {
		method: 'POST',
		headers: { Authorization: `Bearer ${token}` },
		body: form
	});
	if (!res.ok) throw new ApiError(res.status, `API error: ${res.status} ${res.statusText}`);
	return res.json();
}

export async function apiPut<T>(path: string, token: string, body: unknown): Promise<T> {
	const res = await fetch(`${API_BASE}${path}`, {
		method: 'PUT',
		headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
		body: JSON.stringify(body)
	});
	if (!res.ok) throw new ApiError(res.status, `API error: ${res.status} ${res.statusText}`);
	return res.json();
}
