import { test as setup, expect } from '@playwright/test';

const AUTH_FILE = 'e2e/.auth/user.json';

/**
 * Logs in once via /login and persists the session cookie so the actual
 * specs start already authenticated. Credentials come from env only.
 */
setup('authenticate', async ({ page }) => {
	const email = process.env.E2E_EMAIL;
	const password = process.env.E2E_PASSWORD;
	if (!email || !password) {
		throw new Error('E2E_EMAIL and E2E_PASSWORD are required to authenticate.');
	}

	await page.goto('/login');
	await page.locator('#email').fill(email);
	await page.locator('#password').fill(password);
	await page.getByRole('button', { name: /.+/ }).click();

	// A successful login leaves /login (redirect to the app shell).
	await expect(page).not.toHaveURL(/\/login(\?|$)/, { timeout: 15_000 });

	await page.context().storageState({ path: AUTH_FILE });
});
