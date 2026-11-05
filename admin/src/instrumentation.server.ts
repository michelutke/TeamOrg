import * as Sentry from '@sentry/sveltekit';

// Same PUBLIC_ vars as the browser side: the DSN is not a secret, so one setting covers both.
const dsn = process.env.PUBLIC_SENTRY_DSN;
const environment = process.env.PUBLIC_SENTRY_ENVIRONMENT || 'production';

Sentry.init({
	dsn: dsn || undefined,
	enabled: Boolean(dsn),
	environment,
	tracesSampleRate: environment === 'development' ? 1.0 : 0.2,
	dataCollection: {
		userInfo: false,
		cookies: false,
		httpBodies: []
	}
});
