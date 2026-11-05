import { sveltekit } from '@sveltejs/kit/vite';
import { sentrySvelteKit } from '@sentry/sveltekit/vite';
import { defineConfig } from 'vite';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
	plugins: [
		// Must come before sveltekit(). Source maps are only uploaded when SENTRY_AUTH_TOKEN
		// is present at build time, so local and CI builds without it still succeed.
		sentrySvelteKit({
			org: 'teamorg-46',
			project: 'teamorg-admin',
			sentryUrl: 'https://de.sentry.io/',
			authToken: process.env.SENTRY_AUTH_TOKEN,
			autoUploadSourceMaps: Boolean(process.env.SENTRY_AUTH_TOKEN)
		}),
		tailwindcss(),
		sveltekit()
	],
	server: {
		proxy: {
			'/api': {
				target: 'http://localhost:8080',
				rewrite: (path) => path.replace(/^\/api/, '')
			}
		}
	}
});
