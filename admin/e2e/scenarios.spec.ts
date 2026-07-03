import { test, expect, type BrowserContext, type Page } from '@playwright/test';

/**
 * Real-world persona scenarios against a live instance, exercising the full
 * unified-attendance lifecycle with TWO users:
 *
 *   Manager/Coach (E2E_EMAIL)  — club manager of the club, manages the test team
 *   Player (created here)      — joins via the real invite → register flow
 *
 * Story:
 *   1.  Manager creates a shareable player invite for the test team.
 *   2.  A new player redeems it: registers, lands authenticated, sees the team.
 *   3.  Coach creates a future training (defaultResponse=none).
 *   4.  Player RSVPs "Zusagen" (confirm) on that training.
 *   5.  Coach sees the player's response on the event detail.
 *   6.  Coach creates a PAST training (defaultResponse=accepted) → born awaiting check-in.
 *   7.  Player sees the lock ("Zeit zum An-/Abmelden abgelaufen", disabled buttons).
 *   8.  Coach marks the player "Abgemeldet" + "Nicht entschuldigt" via the edit popup.
 *   9.  Coach finalizes ("CheckIn abschliessen") — defaultResponse=accepted resolves
 *       the remaining no-response members → done; unexcused badge survives.
 *   10. Coach reopens ("CheckIn wieder öffnen") → back to awaiting.
 *   Cleanup: both E2E events are cancelled; the throwaway player account remains
 *   (reported for manual removal — there is no self-service account deletion).
 *
 * MUTATING by design → gated behind E2E_ALLOW_MUTATION=1. All writes are confined
 * to entities this suite creates (events titled "E2E …", one throwaway player).
 *
 * Extra env (besides E2E_BASE_URL/E2E_EMAIL/E2E_PASSWORD):
 *   E2E_CLUB_ID   club the manager account manages
 *   E2E_TEAM_ID   team (within that club) to run the scenario on — use a TEST team
 *   E2E_TEAM_NAME visible name of that team (for the create-form team chip)
 */

const RUN = process.env.E2E_ALLOW_MUTATION === '1';
const CLUB_ID = process.env.E2E_CLUB_ID ?? '';
const TEAM_ID = process.env.E2E_TEAM_ID ?? '';
const TEAM_NAME = process.env.E2E_TEAM_NAME ?? '';

const STAMP = Date.now();
const PLAYER_NAME = `E2E Testspieler ${STAMP}`;
const PLAYER_EMAIL = `e2e.player.${STAMP}@example.com`;
const PLAYER_PASSWORD = `E2e!${STAMP}pw`;
const FUTURE_TITLE = `E2E Training offen ${STAMP}`;
const PAST_TITLE = `E2E Training Checkin ${STAMP}`;

/** yyyy-MM-ddTHH:mm for datetime-local inputs, local time. */
function local(dt: Date): string {
	const p = (n: number) => String(n).padStart(2, '0');
	return `${dt.getFullYear()}-${p(dt.getMonth() + 1)}-${p(dt.getDate())}T${p(dt.getHours())}:${p(dt.getMinutes())}`;
}

async function createEvent(
	coach: Page,
	opts: { title: string; start: Date; end: Date; defaultResponse: 'none' | 'accepted' | 'declined' }
): Promise<string> {
	await coach.goto('/app/events/new');
	await coach.getByRole('button', { name: TEAM_NAME }).click();
	await coach.locator('input[name="title"]').fill(opts.title);
	const dts = coach.locator('input[type="datetime-local"]');
	await dts.nth(0).fill(local(opts.start));
	await dts.nth(1).fill(local(opts.end));
	await coach.locator('select[name="defaultResponse"]').selectOption(opts.defaultResponse);
	await coach.getByRole('button', { name: /Erstellen|Speichern|Create/i }).last().click();
	await coach.waitForURL(/\/app\/events\/[0-9a-f-]{36}$/i, { timeout: 15_000 });
	return new URL(coach.url()).pathname;
}

test.describe.serial('Real-world attendance scenarios (manager + invited player)', () => {
	test.skip(!RUN, 'Mutating scenario suite — set E2E_ALLOW_MUTATION=1 (never against real data you care about).');
	test.skip(!CLUB_ID || !TEAM_ID || !TEAM_NAME, 'E2E_CLUB_ID, E2E_TEAM_ID and E2E_TEAM_NAME are required.');

	let managerCtx: BrowserContext;
	let playerCtx: BrowserContext;
	let manager: Page;
	let player: Page;
	let inviteUrl = '';
	let futureEventPath = '';
	let pastEventPath = '';

	test.beforeAll(async ({ browser }) => {
		managerCtx = await browser.newContext({ storageState: 'e2e/.auth/user.json' });
		// NOTE: newContext() inherits the project's `use` options — including the
		// manager storageState. The player must start truly anonymous, so override
		// with an explicit empty storage state.
		playerCtx = await browser.newContext({ storageState: { cookies: [], origins: [] } });
		manager = await managerCtx.newPage();
		player = await playerCtx.newPage();
	});

	test.afterAll(async () => {
		// Cleanup: cancel the E2E events (best effort — they are clearly labelled).
		for (const path of [futureEventPath, pastEventPath]) {
			if (!path) continue;
			try {
				await manager.goto(path);
				const cancel = manager.locator('form[action="?/cancel"] button');
				if (await cancel.count()) await cancel.click();
				await manager.waitForTimeout(500);
			} catch {
				/* best effort */
			}
		}
		await managerCtx?.close();
		await playerCtx?.close();
	});

	test('1. manager creates a shareable player invite', async () => {
		await manager.goto(`/manage/${CLUB_ID}/teams/${TEAM_ID}`);
		await manager.getByRole('button', { name: 'Invite a member' }).click();
		// role select defaults to "player"; leave email empty → shareable link.
		await manager.getByRole('button', { name: 'Create invite' }).click();
		// The shareable link lands in a readonly input; take the one holding an /i/ URL.
		// (Note: the generated link uses the teamorg.ch base — we navigate by pathname.)
		await expect(manager.locator('input[readonly]').first()).toBeVisible({ timeout: 10_000 });
		const values = await manager
			.locator('input[readonly]')
			.evaluateAll((els) => els.map((e) => (e as HTMLInputElement).value));
		inviteUrl = values.find((v) => /\/i\/[A-Za-z0-9_-]+/.test(v)) ?? '';
		expect(inviteUrl).toMatch(/\/i\/[A-Za-z0-9_-]+/);
	});

	test('2. new player registers through the invite', async () => {
		const path = new URL(inviteUrl, 'https://placeholder.invalid').pathname;
		await player.goto(path);
		await player.locator('input[name="name"]').fill(PLAYER_NAME);
		await player.locator('input[name="email"]').fill(PLAYER_EMAIL);
		await player.locator('input[name="password"]').fill(PLAYER_PASSWORD);
		await player.getByRole('button', { name: 'Konto erstellen & beitreten' }).click();
		await player.waitForURL((u) => u.pathname.startsWith('/app'), { timeout: 15_000 });
		// The player is authenticated and a member of the team.
		await player.goto('/app/teams');
		await expect(player.getByText(TEAM_NAME).first()).toBeVisible();
	});

	test('3. coach creates a future training with defaultResponse=none', async () => {
		const start = new Date(Date.now() + 24 * 3600 * 1000);
		start.setHours(19, 0, 0, 0);
		const end = new Date(start.getTime() + 90 * 60 * 1000);
		futureEventPath = await createEvent(manager, {
			title: FUTURE_TITLE,
			start,
			end,
			defaultResponse: 'none'
		});
		// Open state: player RSVP buttons would be enabled; no finalize button yet.
		await expect(manager.getByText('CheckIn abschliessen')).toHaveCount(0);
	});

	test('4. player RSVPs "Zusagen" on the future training', async () => {
		await player.goto(futureEventPath);
		await expect(player.getByText(FUTURE_TITLE)).toBeVisible();
		const zusagen = player.getByRole('button', { name: 'Zusagen', exact: true }).first();
		await expect(zusagen).toBeEnabled();
		await zusagen.click();
		await expect(player.getByText('Gespeichert')).toBeVisible({ timeout: 10_000 });
	});

	test('5. coach sees the player response on the detail', async () => {
		await manager.goto(futureEventPath);
		await expect(manager.getByText(PLAYER_NAME).first()).toBeVisible();
	});

	test('6. coach creates a PAST training → born awaiting check-in', async () => {
		const start = new Date(Date.now() - 24 * 3600 * 1000);
		start.setHours(19, 0, 0, 0);
		const end = new Date(start.getTime() + 90 * 60 * 1000);
		pastEventPath = await createEvent(manager, {
			title: PAST_TITLE,
			start,
			end,
			defaultResponse: 'accepted'
		});
		await expect(manager.getByText('CheckIn abschliessen')).toBeVisible();
	});

	test('7. player sees the lock on the past training', async () => {
		await player.goto(pastEventPath);
		await expect(player.getByText('Zeit zum An-/Abmelden abgelaufen')).toBeVisible();
		await expect(
			player.getByRole('button', { name: 'Zusagen', exact: true }).first()
		).toBeDisabled();
	});

	test('8. coach marks the player Abgemeldet + Nicht entschuldigt', async () => {
		await manager.goto(pastEventPath);
		// Row-scoped edit trigger (works with and without the aria-label).
		const row = manager
			.locator('div.justify-between')
			.filter({ hasText: PLAYER_NAME })
			.first();
		await row.getByRole('button').click();

		const dialog = manager.locator('[aria-label="Status bearbeiten"]');
		await expect(dialog).toBeVisible();
		await dialog.getByRole('button', { name: 'Abgemeldet' }).click();
		await dialog.locator('input[type="checkbox"]').check();
		await dialog.getByRole('button', { name: 'Speichern' }).click();

		// The unexcused badge appears on the player's row (coach view only).
		await expect(
			manager
				.locator('div')
				.filter({ hasText: PLAYER_NAME })
				.getByText('Nicht entschuldigt')
				.first()
		).toBeVisible({ timeout: 10_000 });
	});

	test('9. coach finalizes — defaultResponse=accepted resolves the rest', async () => {
		await manager.goto(pastEventPath);
		await manager.getByText('CheckIn abschliessen').click();
		// Success → done state with a reopen affordance.
		await expect(manager.getByText(/CheckIn wieder/)).toBeVisible({ timeout: 15_000 });
		// The player's manual declined+unexcused survived the finalize.
		await expect(manager.getByText('Nicht entschuldigt').first()).toBeVisible();
	});

	test('10. coach reopens the check-in', async () => {
		await manager.getByText(/CheckIn wieder/).click();
		await expect(manager.getByText('CheckIn abschliessen')).toBeVisible({ timeout: 15_000 });
	});
});
