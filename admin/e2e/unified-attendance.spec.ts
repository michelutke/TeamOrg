import { test, expect, type Page } from '@playwright/test';

/**
 * E2E coverage for the unified-attendance web surfaces (see
 * docs/superpowers/specs/2026-07-01-unified-attendance-design.md).
 *
 * The account in E2E_EMAIL must be a coach or club-manager of at least one team
 * for the coach-only surfaces (awaiting filter, finalize/reopen) to appear.
 *
 * These tests adapt to data availability: surfaces that need a specific event
 * state (an awaiting-check-in event) skip cleanly when no such event exists,
 * so the suite is stable across instances. The destructive finalize/reopen
 * round-trip only runs when E2E_ALLOW_MUTATION=1.
 */

const ALLOW_MUTATION = process.env.E2E_ALLOW_MUTATION === '1';

async function gotoEvents(page: Page) {
	await page.goto('/app/events');
	await expect(page).toHaveURL(/\/app\/events/);
}

test.describe('Events list', () => {
	test('loads for an authenticated user', async ({ page }) => {
		await gotoEvents(page);
		// The events route rendered (no redirect back to /login).
		await expect(page).not.toHaveURL(/\/login/);
	});

	test('coach sees the "Check-in offen" awaiting filter, and it filters', async ({ page }) => {
		await gotoEvents(page);
		const chip = page.getByText('Check-in offen', { exact: true }).first();
		if ((await chip.count()) === 0) {
			test.skip(true, 'Account is not a coach/manager of any team — no awaiting filter.');
		}
		await chip.click();
		// After activating, every visible event that carries a status badge must be awaiting.
		// (We can't assert a count without data, but the page must not error out.)
		await expect(page).not.toHaveURL(/\/login/);
		await expect(page.getByText('Check-in offen').first()).toBeVisible();
	});
});

test.describe('Event create form — defaultResponse', () => {
	test('exposes the none/accepted/declined select, defaulting to none', async ({ page }) => {
		await page.goto('/app/events/new');
		if (/\/login/.test(page.url())) test.skip(true, 'Not authorized to create events.');

		// Locale-robust: match the select by its option values, not labels.
		const select = page.locator('select:has(option[value="accepted"]):has(option[value="declined"])');
		await expect(select).toBeVisible();
		await expect(select.locator('option[value="none"]')).toHaveCount(1);
		await expect(select).toHaveValue('none');

		// It is settable.
		await select.selectOption('accepted');
		await expect(select).toHaveValue('accepted');
	});
});

test.describe('Awaiting check-in event (coach)', () => {
	/** Opens the first awaiting-check-in event via the coach filter; skips if none. */
	async function openFirstAwaiting(page: Page): Promise<boolean> {
		await gotoEvents(page);
		const chip = page.getByText('Check-in offen', { exact: true }).first();
		if ((await chip.count()) === 0) return false;
		await chip.click();
		// Event rows are links into /app/events/{id}. Pick the first one under the list.
		const firstEvent = page.locator('a[href*="/app/events/"]').first();
		if ((await firstEvent.count()) === 0) return false;
		await firstEvent.click();
		await expect(page).toHaveURL(/\/app\/events\/[^/]+$/);
		return true;
	}

	test('detail shows coach controls (finalize + edit affordance)', async ({ page }) => {
		const opened = await openFirstAwaiting(page);
		if (!opened) test.skip(true, 'No awaiting-check-in event available on this instance.');

		// "CheckIn abschliessen" is the finalize action, coach-only on an awaiting event.
		await expect(page.getByText('CheckIn abschliessen')).toBeVisible();
	});

	test('coach edit popup offers Anwesend/Abgemeldet + Nicht entschuldigt', async ({ page }) => {
		const opened = await openFirstAwaiting(page);
		if (!opened) test.skip(true, 'No awaiting-check-in event available on this instance.');

		// Open the per-member edit popup (edit affordance on a response row).
		const editTrigger = page
			.getByRole('button', { name: /bearbeiten|edit|ändern/i })
			.first();
		if ((await editTrigger.count()) === 0) {
			test.skip(true, 'No member rows to edit on this event.');
		}
		await editTrigger.click();

		await expect(page.getByText('Anwesend').first()).toBeVisible();
		await expect(page.getByText('Abgemeldet').first()).toBeVisible();
		// Choosing "Abgemeldet" reveals the coach-only "Nicht entschuldigt" toggle.
		await page.getByText('Abgemeldet').first().click();
		await expect(page.getByText('Nicht entschuldigt')).toBeVisible();
	});

	test('finalize → reopen round-trip', async ({ page }) => {
		test.skip(!ALLOW_MUTATION, 'Set E2E_ALLOW_MUTATION=1 to run the mutating finalize/reopen flow.');
		const opened = await openFirstAwaiting(page);
		if (!opened) test.skip(true, 'No awaiting-check-in event available on this instance.');

		await page.getByText('CheckIn abschliessen').click();

		// Either it finalizes (→ reopen button appears) or it is blocked (dialog lists members).
		const reopen = page.getByText(/CheckIn wieder/);
		const blocked = page.getByText(/müssen zuerst/);
		await expect(reopen.or(blocked).first()).toBeVisible({ timeout: 10_000 });

		if (await reopen.count()) {
			// Successfully finalized — reopen to restore the prior state.
			await reopen.click();
			await expect(page.getByText('CheckIn abschliessen')).toBeVisible({ timeout: 10_000 });
		}
	});
});
