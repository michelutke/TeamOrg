import * as Sentry from '@sentry/sveltekit';

const dsn = process.env.SENTRY_DSN;
const environment = process.env.SENTRY_ENVIRONMENT || 'production';

Sentry.init({
	dsn: dsn || undefined,
	enabled: Boolean(dsn),
	environment,
	release: process.env.SENTRY_RELEASE || undefined,
	tracesSampleRate: environment === 'development' ? 1.0 : 0.2,
	dataCollection: {
		userInfo: false,
		cookies: false,
		httpBodies: []
	}
});
