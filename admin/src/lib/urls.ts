import { env } from '$env/dynamic/public';

// Uploaded files (club logos, avatars) are served by the API under /uploads.
// The browser must hit the API's PUBLIC origin, not the web app origin, so
// relative paths are prefixed. Override via PUBLIC_SERVER_URL in deployment.
const SERVER_BASE = (env.PUBLIC_SERVER_URL || 'https://server.teamorg.ch').replace(/\/$/, '');

/** Resolve a stored file path to a browser-loadable absolute URL. */
export function fileUrl(path: string | null | undefined): string | null {
	if (!path) return null;
	if (/^https?:\/\//.test(path)) return path;
	return `${SERVER_BASE}${path.startsWith('/') ? '' : '/'}${path}`;
}
