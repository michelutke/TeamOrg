import {
	test,
	expect,
	request as playwrightRequest,
	type Browser,
	type BrowserContext,
	type Page
} from '@playwright/test';

/**
 * Public self-serve onboarding: /start, /join (short-code → invite), and the /create
 * wizard through the billing step (spec: docs/superpowers/specs/2026-07-24-self-serve-onboarding-billing-design.md).
 *
 * Card entry itself cannot run headless against real Stripe — E2E coverage stops once
 * the Payment Element mounts (or the "not configured" fallback shows in envs without a
 * publishable key). See the MANUAL QA CHECKLIST below for the card step onward.
 *
 * Non-mutating tests (always run):
 *   - anonymous /start renders both CTAs + the login link
 *   - /join rejects a garbage 8-char code and a malformed short code with the same error
 *
 * Mutating tests (gated behind E2E_ALLOW_MUTATION=1 — creates a real invite / account / club):
 *   - full join flow: create a reusable invite directly via the backend API (the admin's
 *     "Invite a member" UI has no control for `reusable: true`, so a short code cannot be
 *     produced through the UI today — this seeds it via the same POST /teams/{id}/invites
 *     endpoint the UI calls), then redeem the short code anonymously through /join.
 *   - /create wizard: register a throwaway account, fill club/team details, and land on
 *     /create/billing.
 *
 * Extra env (besides E2E_BASE_URL/E2E_EMAIL/E2E_PASSWORD):
 *   E2E_TEAM_ID   team (owned/managed by E2E_EMAIL) to create the reusable invite on
 *   E2E_API_URL   base URL of the backend API (default http://localhost:8080) — the
 *                 reusable-invite seed talks to the backend directly, not the admin app
 *
 * MANUAL QA CHECKLIST (card step onward — not automatable headless against real Stripe):
 *   [ ] /create/billing — test-card happy path (4242 4242 4242 4242, any future expiry/CVC)
 *       confirms the SetupIntent and lands the new owner on /manage/{clubId}.
 *   [ ] /create/billing — decline card (4000 0000 0000 0002) surfaces a card error and
 *       stays on the billing step.
 *   [ ] /create/billing — 3DS card (4000 0027 6000 3184) triggers the Stripe challenge
 *       modal, and completing it returns via ?setup_intent=... and confirms successfully.
 *   [ ] /manage/{clubId}/billing — "update card" flow replaces the stored card and shows
 *       the new brand/last4.
 *   [ ] /manage/{clubId}/billing — convert club → team and team → club (both directions)
 *       and confirm the billing page reflects the new kind.
 *   [ ] Frozen club: FrozenBanner shows on the manage shell with the 402 message, and the
 *       "frozenBannerCta" link routes to /manage/{clubId}/billing.
 *   [ ] Past-due club: FrozenBanner (past_due variant) shows without blocking access.
 */

const RUN = process.env.E2E_ALLOW_MUTATION === '1';
const EMAIL = process.env.E2E_EMAIL ?? '';
const PASSWORD = process.env.E2E_PASSWORD ?? '';
const TEAM_ID = process.env.E2E_TEAM_ID ?? '';
const API_URL = process.env.E2E_API_URL ?? 'http://localhost:8080';

const JOIN_CODE_INVALID = 'Dieser Code ist ungültig oder abgelaufen.';

async function anonymousPage(browser: Browser): Promise<{ ctx: BrowserContext; page: Page }> {
	const ctx = await browser.newContext({ storageState: { cookies: [], origins: [] } });
	return { ctx, page: await ctx.newPage() };
}

test('anonymous /start renders both CTAs and the login link', async ({ browser }) => {
	const { ctx, page } = await anonymousPage(browser);
	await page.goto('/start');
	await expect(page.locator('a[href="/join"]')).toBeVisible();
	await expect(page.locator('a[href="/create"]')).toBeVisible();
	await expect(page.locator('a[href="/login"]')).toBeVisible();
	await ctx.close();
});

test.describe('/join invalid codes', () => {
	test('a garbage 8-char code shows the invalid-code error', async ({ browser }) => {
		const { ctx, page } = await anonymousPage(browser);
		await page.goto('/join');
		await page.locator('#code').fill('ZZZZZZZZ');
		await page.getByRole('button', { name: 'Beitreten' }).click();
		await expect(page.getByText(JOIN_CODE_INVALID)).toBeVisible();
		await ctx.close();
	});

	test('a malformed (too-short) short code shows the same error', async ({ browser }) => {
		const { ctx, page } = await anonymousPage(browser);
		await page.goto('/join');
		await page.locator('#code').fill('AB1');
		await page.getByRole('button', { name: 'Beitreten' }).click();
		await expect(page.getByText(JOIN_CODE_INVALID)).toBeVisible();
		await ctx.close();
	});
});

test.describe('full join flow with a freshly created reusable invite', () => {
	test.skip(!RUN, 'Mutating — creates a real invite. Set E2E_ALLOW_MUTATION=1.');
	test.skip(!TEAM_ID, 'E2E_TEAM_ID is required to create the invite against a real team.');

	test('short code resolves and lands on the invite page', async ({ browser }) => {
		const api = await playwrightRequest.newContext({ baseURL: API_URL });

		const loginRes = await api.post('/auth/login', { data: { email: EMAIL, password: PASSWORD } });
		expect(loginRes.ok()).toBeTruthy();
		const { token } = await loginRes.json();

		const inviteRes = await api.post(`/teams/${TEAM_ID}/invites`, {
			headers: { Authorization: `Bearer ${token}` },
			data: { role: 'player', reusable: true }
		});
		expect(inviteRes.ok()).toBeTruthy();
		const invite = await inviteRes.json();
		expect(invite.shortCode).toBeTruthy();
		await api.dispose();

		const { ctx, page } = await anonymousPage(browser);
		await page.goto('/join');
		await page.locator('#code').fill(invite.shortCode);
		await page.getByRole('button', { name: 'Beitreten' }).click();
		await page.waitForURL(new RegExp(`/i/${invite.token}$`), { timeout: 15_000 });
		await ctx.close();
	});
});

test.describe('/create wizard', () => {
	test.skip(!RUN, 'Mutating — registers a throwaway account (and with Stripe, a club/team). Set E2E_ALLOW_MUTATION=1.');

	async function registerToDetails(browser: import('@playwright/test').Browser, stamp: number) {
		const email = `e2e.onboarding.${stamp}@example.com`;
		const password = `E2e!${stamp}pw`;
		const name = `E2E Onboarding ${stamp}`;

		const { ctx, page } = await anonymousPage(browser);
		await page.goto('/create');
		await page.locator('input[name="name"]').fill(name);
		await page.locator('input[name="email"]').fill(email);
		await page.locator('input[name="password"]').fill(password);
		await page.getByRole('button', { name: 'Konto erstellen' }).click();
		await page.waitForURL(/\/create$/, { timeout: 15_000 });
		return { ctx, page, name };
	}

	test('register lands on the details step with kind cards', async ({ browser }) => {
		const { ctx, page } = await registerToDetails(browser, Date.now());
		// Step 2 (details) — kind defaults to "team"; billing email is pre-filled.
		await expect(page.getByRole('radio', { checked: true })).toBeVisible();
		await expect(page.locator('input[name="billingEmail"]')).not.toHaveValue('');
		await expect(page.getByRole('button', { name: 'Weiter zur Zahlung' })).toBeVisible();
		await ctx.close();
	});

	// Submitting the details step calls the backend's POST /clubs/self-serve, which
	// requires STRIPE_SECRET_KEY on the *backend*. Gate behind E2E_STRIPE=1 so the
	// suite stays green against a Stripe-less dev stack.
	test.describe('through the billing step (needs Stripe-configured backend)', () => {
		test.skip(process.env.E2E_STRIPE !== '1', 'Backend needs STRIPE_* env. Set E2E_STRIPE=1.');

		test('fill details and reach the billing step', async ({ browser }) => {
			const { ctx, page, name } = await registerToDetails(browser, Date.now());
			await page.locator('input[name="name"]').fill(name);
			await page.getByRole('button', { name: 'Weiter zur Zahlung' }).click();
			await page.waitForURL(/\/create\/billing$/, { timeout: 15_000 });

			// Env-dependent: the Payment Element mounts an iframe when a publishable key
			// is configured on the admin app, otherwise the fallback shows.
			const paymentIframe = page.locator('iframe').first();
			const notConfigured = page.getByText('Billing is not configured in this environment.');
			await expect(paymentIframe.or(notConfigured)).toBeVisible({ timeout: 15_000 });
			await ctx.close();
		});
	});
});
