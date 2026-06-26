import { ApiError } from '$lib/server/guards';
import { error } from '@sveltejs/kit';
import type { RequestHandler } from './$types';

const API_BASE = process.env.API_URL || 'http://localhost:8080';

// Streams the NDS export ZIP (Aktivitäten + AWK CSV) from the backend to the browser as a
// download. 409 means pre-flight failed → surface as an error the page already explains.
export const GET: RequestHandler = async ({ params, locals }) => {
	if (!locals.token) throw error(401, 'Nicht angemeldet');
	const res = await fetch(`${API_BASE}/teams/${params.teamId}/nds/export`, {
		headers: { Authorization: `Bearer ${locals.token}` }
	});
	if (res.status === 409) throw error(409, 'Export blockiert: bitte zuerst die Pre-Flight-Fehler beheben.');
	if (!res.ok) throw new ApiError(res.status, 'Export fehlgeschlagen');

	const body = await res.arrayBuffer();
	return new Response(body, {
		headers: {
			'Content-Type': 'application/zip',
			'Content-Disposition': 'attachment; filename="nds-export.zip"'
		}
	});
};
