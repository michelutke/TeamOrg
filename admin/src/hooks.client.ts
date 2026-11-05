import { env } from '$env/dynamic/public';
import * as Sentry from '@sentry/sveltekit';

const dsn = env.PUBLIC_SENTRY_DSN;
const environment = env.PUBLIC_SENTRY_ENVIRONMENT || 'production';

Sentry.init({
	dsn: dsn || undefined,
	enabled: Boolean(dsn),
	environment,
	tracesSampleRate: environment === 'development' ? 1.0 : 0.2,
	// The Ktor API has no CORS config, so trace headers must only go to same-origin requests.
	tracePropagationTargets: [window.location.origin],
	dataCollection: {
		userInfo: false,
		cookies: false,
		httpBodies: []
	}
});

export const handleError = Sentry.handleErrorWithSentry();
