import type { Handle } from '@sveltejs/kit';

// Android App Links verification file. Served here (not as a route) because
// SvelteKit ignores dot-directories like src/routes/.well-known/.
const ASSETLINKS = JSON.stringify([
	{
		relation: ['delegate_permission/common.handle_all_urls'],
		target: {
			namespace: 'android_app',
			package_name: 'ch.teamorg',
			sha256_cert_fingerprints: [
				'F2:39:C1:56:12:78:F0:6E:A9:2A:CC:25:8F:6F:1C:62:54:55:A2:B1:78:E3:2F:32:73:34:72:19:1D:E8:CA:CD'
			]
		}
	}
]);

export const handle: Handle = async ({ event, resolve }) => {
	if (event.request.method === 'GET' && event.url.pathname === '/.well-known/assetlinks.json') {
		return new Response(ASSETLINKS, {
			headers: { 'content-type': 'application/json' }
		});
	}
	return resolve(event);
};
