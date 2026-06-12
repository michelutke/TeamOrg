const API_BASE = process.env.API_URL || 'http://localhost:8080';

export async function apiGet<T>(path: string, token: string): Promise<T> {
	const res = await fetch(`${API_BASE}${path}`, {
		headers: { Authorization: `Bearer ${token}` }
	});
	if (!res.ok) throw new Error(`API error: ${res.status} ${res.statusText}`);
	return res.json();
}

export async function apiPost<T>(path: string, token: string, body?: unknown): Promise<T> {
	const res = await fetch(`${API_BASE}${path}`, {
		method: 'POST',
		headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
		body: body ? JSON.stringify(body) : undefined
	});
	if (!res.ok) throw new Error(`API error: ${res.status} ${res.statusText}`);
	return res.json();
}

export async function apiPatch<T>(path: string, token: string, body: unknown): Promise<T> {
	const res = await fetch(`${API_BASE}${path}`, {
		method: 'PATCH',
		headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
		body: JSON.stringify(body)
	});
	if (!res.ok) throw new Error(`API error: ${res.status} ${res.statusText}`);
	return res.json();
}

export async function apiDelete(path: string, token: string): Promise<void> {
	const res = await fetch(`${API_BASE}${path}`, {
		method: 'DELETE',
		headers: { Authorization: `Bearer ${token}` }
	});
	if (!res.ok) throw new Error(`API error: ${res.status} ${res.statusText}`);
}
