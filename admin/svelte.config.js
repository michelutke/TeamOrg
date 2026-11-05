import adapter from '@sveltejs/adapter-node';
import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';

/** @type {import('@sveltejs/kit').Config} */
const config = {
	preprocess: vitePreprocess(),
	kit: {
		adapter: adapter(),
		experimental: {
			instrumentation: { server: true },
			tracing: { server: true }
		},
		// SvelteKit inlines hydration scripts, so it has to author the script-src hashes
		// itself — a hand-written CSP header would break hydration. Stripe's Payment
		// Element needs js.stripe.com (script + iframe) and api.stripe.com (XHR); the
		// API origin for uploads/fetches is appended at runtime in hooks.server.ts. Sentry's EU
		// ingest host is allowed so browser error/trace events are not blocked.
		csp: {
			mode: 'auto',
			directives: {
				'default-src': ['self'],
				'script-src': ['self', 'https://js.stripe.com'],
				'style-src': ['self', 'unsafe-inline'],
				'img-src': ['self', 'data:'],
				'font-src': ['self'],
				'connect-src': ['self', 'https://api.stripe.com', 'https://*.ingest.de.sentry.io'],
				'frame-src': ['https://js.stripe.com', 'https://hooks.stripe.com'],
				'object-src': ['none'],
				'base-uri': ['none'],
				'form-action': ['self'],
				'frame-ancestors': ['none']
			}
		}
	}
};

export default config;
