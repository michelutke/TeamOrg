import { defineConfig, devices } from '@playwright/test';

/**
 * E2E config for the TeamOrg admin.
 *
 * Required env (no secrets are committed):
 *   E2E_BASE_URL   base URL of a RUNNING admin instance (e.g. http://localhost:5173)
 *   E2E_EMAIL      login email of a coach / club-manager test account
 *   E2E_PASSWORD   that account's password
 *
 * Optional:
 *   E2E_ALLOW_MUTATION=1  enable the destructive finalize/reopen round-trip.
 *                         Leave unset when pointing at a shared/production-like
 *                         instance — the suite then only asserts the controls
 *                         exist and does not mutate attendance state.
 *
 * Run:  E2E_BASE_URL=... E2E_EMAIL=... E2E_PASSWORD=... npm run test:e2e
 */
const baseURL = process.env.E2E_BASE_URL;

if (!baseURL) {
	// Fail loudly instead of silently defaulting to a production URL.
	throw new Error('E2E_BASE_URL is required (base URL of a running admin instance).');
}

export default defineConfig({
	testDir: './e2e',
	fullyParallel: false,
	forbidOnly: !!process.env.CI,
	retries: process.env.CI ? 1 : 0,
	workers: 1,
	reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
	use: {
		baseURL,
		trace: 'on-first-retry',
		screenshot: 'only-on-failure'
	},
	projects: [
		{ name: 'setup', testMatch: /auth\.setup\.ts/ },
		{
			name: 'chromium',
			use: { ...devices['Desktop Chrome'], storageState: 'e2e/.auth/user.json' },
			dependencies: ['setup']
		}
	]
});
