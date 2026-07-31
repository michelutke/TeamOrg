import path from 'node:path';
import os from 'node:os';
import { fileURLToPath } from 'node:url';
import AdmZip from 'adm-zip';
import { test, expect, type Browser, type Page } from '@playwright/test';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/**
 * E2E coverage for the NDS import wizard (admin/src/lib/components/NdsImportDialog.svelte),
 * entered from the team page ("NDS-Import" button, fixed-team flow).
 *
 * Fixtures (admin/e2e/fixtures/; AWL Angebot rewritten per run, see uniqueAnwesenheitsliste) are dumped byte-for-byte from the server's own
 * ch.teamorg.nds.NdsTestFixtures (the same builder NdsRoutesTest.kt uses), so the parser
 * accepts them exactly as it would the real POI-built test fixtures. Regenerate by
 * temporarily re-adding a JUnit test that calls NdsTestFixtures.*Bytes() and writes the
 * result to this directory, then `./gradlew :server:test --tests <thatTest>`.
 *
 * The Anwesenheitsliste fixture's 8 activity dates are fixed at Aug 2026 (2 weekly series:
 * Mondays + Wednesdays) — see NdsTestFixtures.MONDAYS/WEDNESDAYS — so they stay in the future
 * for any run before then.
 *
 * MUTATING by design (creates a team, an invited member, events, an NDS import) → gated behind
 * E2E_ALLOW_MUTATION=1, run only against a local/throwaway stack, never production.
 *
 * Extra env (besides E2E_BASE_URL/E2E_EMAIL/E2E_PASSWORD):
 *   E2E_CLUB_ID   club the E2E_EMAIL account manages (used to create throwaway teams in)
 */

const RUN = process.env.E2E_ALLOW_MUTATION === '1';
const CLUB_ID = process.env.E2E_CLUB_ID ?? '';

const FIXTURES = {
	anwesenheitsliste: path.join(__dirname, 'fixtures', 'anwesenheitsliste.xlsx'),
	teilnehmende: path.join(__dirname, 'fixtures', 'teilnehmende.csv'),
	leiter: path.join(__dirname, 'fixtures', 'leiter.xlsx')
};

/**
 * The fixture's Angebot number (753813) may only ever be linked to ONE team, so importing it
 * twice against the same stack collides (409). Each run therefore rewrites the Angebot inside
 * the xlsx (it lives in xl/sharedStrings.xml) to a unique per-run value, making the suite
 * repeatable without any cleanup access to the backend.
 */
function uniqueAnwesenheitsliste(angebotId: string): string {
	const zip = new AdmZip(FIXTURES.anwesenheitsliste);
	const entry = 'xl/sharedStrings.xml';
	zip.updateFile(entry, Buffer.from(zip.readAsText(entry).replaceAll('753813', angebotId), 'utf-8'));
	const out = path.join(os.tmpdir(), `nds-awl-${angebotId}.xlsx`);
	zip.writeZip(out);
	return out;
}

// Must match ch.teamorg.nds.NdsTestFixtures.MONDAYS / WEDNESDAYS exactly (all 4 of each — a
// partial conflict, where a series has both conflicting and non-conflicting dates, hides the
// Events & Konflikte step's time inputs client-side even though the non-conflicting dates still
// need one; seeding the whole series avoids that mismatch).
const CONFLICT_MONDAYS = ['2026-08-03', '2026-08-10', '2026-08-17', '2026-08-24'];
const CONFLICT_WEDNESDAYS = ['2026-08-05', '2026-08-12', '2026-08-19', '2026-08-26'];

const STAMP = Date.now();

/**
 * Backend API access through the vite dev proxy (`/api` → localhost:8080). Local/throwaway
 * stacks only — mirrors what the wizard's own proxies do, but lets setup seed data in seconds
 * instead of driving the UI (the previous UI-based seeding blew the beforeAll timeout).
 */
async function apiLogin(page: Page): Promise<string> {
	const res = await page.request.post('/api/auth/login', {
		data: { email: process.env.E2E_EMAIL, password: process.env.E2E_PASSWORD }
	});
	expect(res.ok()).toBeTruthy();
	return (await res.json()).token as string;
}

/** Creates a throwaway team in CLUB_ID via the backend API; returns its id. */
async function createTeam(page: Page, name: string): Promise<string> {
	const token = await apiLogin(page);
	const res = await page.request.post(`/api/clubs/${CLUB_ID}/teams`, {
		headers: { Authorization: `Bearer ${token}` },
		data: { name }
	});
	expect(res.ok()).toBeTruthy();
	return (await res.json()).id as string;
}

/** Creates a pre-existing TeamOrg training event on every given ISO date (19:00–20:00). */
async function createConflictingEvents(page: Page, teamId: string, title: string, isoDates: string[]): Promise<void> {
	const token = await apiLogin(page);
	for (const isoDate of isoDates) {
		const res = await page.request.post('/api/events', {
			headers: { Authorization: `Bearer ${token}` },
			data: {
				title,
				type: 'training',
				startAt: `${isoDate}T19:00:00Z`,
				endAt: `${isoDate}T20:00:00Z`,
				teamIds: [teamId]
			}
		});
		expect(res.ok()).toBeTruthy();
	}
}

/**
 * Opens the NDS import wizard from the team page. Retry-clicks until the dialog is visible:
 * on the vite dev server the button can be painted before Svelte hydration wires its onclick,
 * so a single early click silently does nothing.
 */
async function openWizard(page: Page, teamId: string): Promise<void> {
	await page.goto(`/app/teams/${teamId}`);
	await expect(async () => {
		await page.getByRole('button', { name: 'NDS-Import' }).click();
		await expect(page.getByRole('dialog', { name: 'NDS-Import' })).toBeVisible({ timeout: 2000 });
	}).toPass({ timeout: 30_000 });
}

/** Invites a shareable link on the team's manage page, redeems it anonymously, returns the new member's name. */
async function inviteAndRegisterMember(
	manager: Page,
	browser: Browser,
	teamId: string,
	displayName: string
): Promise<void> {
	await manager.goto(`/manage/${CLUB_ID}/teams/${teamId}`);
	await manager.getByRole('button', { name: /Invite a member|Mitglied einladen/i }).click();
	await manager.getByRole('button', { name: /Create invite|Einladung erstellen/i }).click();
	await expect(manager.locator('input[readonly]').first()).toBeVisible({ timeout: 10_000 });
	const values = await manager
		.locator('input[readonly]')
		.evaluateAll((els) => els.map((e) => (e as HTMLInputElement).value));
	const inviteUrl = values.find((v) => /\/i\/[A-Za-z0-9_-]+/.test(v)) ?? '';
	expect(inviteUrl).toMatch(/\/i\/[A-Za-z0-9_-]+/);

	const playerCtx = await browser.newContext({ storageState: { cookies: [], origins: [] } });
	const player = await playerCtx.newPage();
	try {
		const path_ = new URL(inviteUrl, 'https://placeholder.invalid').pathname;
		await player.goto(path_);
		await player.locator('input[name="name"]').fill(displayName);
		await player.locator('input[name="email"]').fill(`e2e.nds.${STAMP}.${Math.random().toString(36).slice(2)}@example.com`);
		await player.locator('input[name="password"]').fill(`E2e!${STAMP}pw`);
		await player.getByRole('button', { name: /Konto erstellen|Create account/i }).click();
		await player.waitForURL((u) => u.pathname.startsWith('/app'), { timeout: 15_000 });
	} finally {
		await playerCtx.close();
	}
}

const seriesContainer = (page: Page, weekdayPrefix: 'MO' | 'MI') =>
	page.locator('div.rounded-2xl.bg-surface-container-high.p-4').filter({ hasText: new RegExp(`^${weekdayPrefix} ·`) });

const mappingRow = (page: Page, lastName: string) => page.locator('tr').filter({ hasText: lastName });

test.describe.serial('NDS import wizard — existing team, full flow', () => {
	test.skip(!RUN, 'Mutating suite — set E2E_ALLOW_MUTATION=1 (local/throwaway stack only).');
	test.skip(!CLUB_ID, 'E2E_CLUB_ID is required.');

	const TEAM_NAME = `E2E NDS Team ${STAMP}`;
	const MEMBER_NAME = `E2E NDS Member ${STAMP}`;
	let teamId = '';

	test.beforeAll(async ({ browser }, testInfo) => {
		testInfo.setTimeout(180_000);
		const ctx = await browser.newContext({ storageState: 'e2e/.auth/user.json' });
		const page = await ctx.newPage();
		teamId = await createTeam(page, TEAM_NAME);
		await inviteAndRegisterMember(page, browser, teamId, MEMBER_NAME);
		// Pre-existing TeamOrg events covering the whole Monday series and the whole Wednesday
		// series, so each weekly series conflicts on every one of its dates.
		await createConflictingEvents(page, teamId, 'Bestehendes Training Mo', CONFLICT_MONDAYS);
		await createConflictingEvents(page, teamId, 'Bestehendes Training Mi', CONFLICT_WEDNESDAYS);
		await ctx.close();
	});

	test('wizard walks Dateien → Zuordnung → Events & Konflikte → Bestätigen and imports', async ({ page }) => {
		await openWizard(page, teamId);

		// Step 1 — Dateien. The AWL gets a per-run Angebot so re-runs never hit the
		// one-team-per-Angebot 409 from a previous run's link.
		await page.setInputFiles('input[type="file"][accept=".csv"]', FIXTURES.teilnehmende);
		const xlsxInputs = page.locator('input[type="file"][accept=".xlsx"]');
		await xlsxInputs.nth(0).setInputFiles(FIXTURES.leiter);
		await xlsxInputs.nth(1).setInputFiles(uniqueAnwesenheitsliste(`${STAMP}`.slice(-9)));
		await page.getByRole('button', { name: 'Weiter' }).click();

		// Step 2 — Mitglieder-Zuordnung: 3 rows (Trainer/Leiter, Müller, Meier).
		await expect(mappingRow(page, 'Trainer')).toBeVisible();
		await expect(mappingRow(page, 'Müller')).toBeVisible();
		await expect(mappingRow(page, 'Meier')).toBeVisible();

		await mappingRow(page, 'Trainer').locator('select').selectOption({ label: MEMBER_NAME });
		// Müller keeps the default ("Neuen Nutzer erstellen").
		await mappingRow(page, 'Meier').locator('select').selectOption('skip');

		await page.getByRole('button', { name: 'Weiter' }).click();

		// Step 3 — Events & Konflikte: two weekly series (MO, MI), each fully conflicting (all 4
		// dates) with the pre-existing TeamOrg events seeded above.
		const mo = seriesContainer(page, 'MO');
		const mi = seriesContainer(page, 'MI');
		await expect(mo).toBeVisible();
		await expect(mi).toBeVisible();
		await expect(mo.getByText('Terminkonflikt')).toBeVisible();
		await expect(mi.getByText('Terminkonflikt')).toBeVisible();

		// MO keeps the default "TeamOrg behalten" — every date stays on the existing event, so no
		// time input renders for this series at all.
		await mo.getByRole('radio', { name: 'TeamOrg behalten' }).check();
		await expect(mo.locator('input[type="time"]')).toHaveCount(0);

		// MI switches to "NDS übernehmen" — every date now imports a new NDS event, so its time
		// inputs appear once the radio is toggled.
		await mi.getByRole('radio', { name: 'NDS übernehmen' }).check();
		await mi.locator('input[type="time"]').nth(0).fill('18:00');
		await mi.locator('input[type="time"]').nth(1).fill('19:30');

		await expect(page.getByRole('button', { name: 'Weiter' })).toBeEnabled();
		await page.getByRole('button', { name: 'Weiter' }).click();

		// Step 4 — Bestätigen: badge counts reflect the decisions above.
		const dialog = page.getByRole('dialog', { name: 'NDS-Import' });
		await expect(dialog.getByText('1 zugeordnet', { exact: true })).toBeVisible();
		await expect(dialog.getByText('1 neu', { exact: true })).toBeVisible();
		await expect(dialog.getByText('1 übersprungen', { exact: true })).toBeVisible();
		await expect(dialog.getByText('4 Events neu', { exact: true })).toBeVisible();
		await expect(dialog.getByText('4 Konflikte TeamOrg / 4 Konflikte NDS', { exact: true })).toBeVisible();

		await page.getByRole('button', { name: 'Importieren' }).click();

		// Done step: real import result. MO fully kept (0 new), MI fully replaced (4 new) → 4 total.
		await expect(dialog.getByText('Import erfolgreich')).toBeVisible({ timeout: 15_000 });
		await expect(dialog.getByText('2 Mitglieder', { exact: true })).toBeVisible();
		await expect(dialog.getByText('4 Termine', { exact: true })).toBeVisible();
		await expect(dialog.getByText(/\d+ Anwesenheiten übernommen/)).toBeVisible();
		await page.getByRole('button', { name: 'Fertig' }).click();
	});

	test('roster reflects the mapping decisions', async ({ page }) => {
		await page.goto(`/app/teams/${teamId}`);
		// NDS rows always show the roster's own name (from the Anwesenheitsliste/Leiter file), not
		// the linked account's display name — the mapped row for "Trainer" just gains a "verknüpft"
		// badge (see admin/src/routes/(shell)/app/teams/[teamId]/+page.svelte).
		await expect(page.getByText('Anna Trainer').first()).toBeVisible();
		await expect(page.getByText('verknüpft')).toHaveCount(1); // only the mapped Trainer row
		await expect(page.getByText('Lara Müller').first()).toBeVisible();
		await expect(page.getByText('Tim Meier')).toHaveCount(0);
	});

	test('events reflect the conflict decisions (kept-TeamOrg survives, kept-NDS replaces)', async ({ page }) => {
		await page.goto(`/app/events?team=${teamId}`);
		// Monday series resolved "keep TeamOrg" → all 4 pre-existing events survive untouched.
		await expect(page.getByRole('link', { name: /Bestehendes Training Mo/ })).toHaveCount(4);
		// Wednesday series resolved "keep NDS" → 4 new NDS-sourced events (titled after the Kurs)
		// replace the pre-existing ones. The cancelled originals may still render in the list
		// (cancelled events aren't hidden there), so the reliable UI signal is the new events.
		await expect(page.getByRole('link', { name: /Test Kurs/ })).toHaveCount(4);
	});
});

test.describe('NDS import wizard — lighter scenarios', () => {
	test.skip(!RUN, 'Mutating suite — set E2E_ALLOW_MUTATION=1 (local/throwaway stack only).');
	test.skip(!CLUB_ID, 'E2E_CLUB_ID is required.');

	test('members-only import (Teilnehmende CSV alone): step 3 is skipped, zero events', async ({ page }) => {
		const teamId = await createTeam(page, `E2E NDS MembersOnly ${STAMP}`);
		await openWizard(page, teamId);

		await page.setInputFiles('input[type="file"][accept=".csv"]', FIXTURES.teilnehmende);
		await page.getByRole('button', { name: 'Weiter' }).click();

		// Only the two Teilnehmende rows — no Leiter/AWL rows.
		await expect(mappingRow(page, 'Müller')).toBeVisible();
		await expect(mappingRow(page, 'Meier')).toBeVisible();
		await expect(mappingRow(page, 'Trainer')).toHaveCount(0);

		// hasAwl is false → "Weiter" jumps straight to "Bestätigen", skipping "Events & Konflikte".
		await page.getByRole('button', { name: 'Weiter' }).click();
		const dialog = page.getByRole('dialog', { name: 'NDS-Import' });
		await expect(dialog.getByText('Zusammenfassung')).toBeVisible();
		// No Anwesenheitsliste → no series at all, so the events summary bullet shows all zeros.
		await expect(dialog.getByText('0 Konflikte TeamOrg / 0 Konflikte NDS', { exact: true })).toBeVisible();

		await page.getByRole('button', { name: 'Importieren' }).click();
		await expect(dialog.getByText('Import erfolgreich')).toBeVisible({ timeout: 15_000 });
		await expect(dialog.getByText('2 Mitglieder', { exact: true })).toBeVisible();
		await expect(dialog.getByText('0 Termine', { exact: true })).toBeVisible();
	});

	test('Anwesenheitsliste-only upload shows mapping rows (regression)', async ({ page }) => {
		const teamId = await createTeam(page, `E2E NDS AwlOnly ${STAMP}`);
		await openWizard(page, teamId);

		const xlsxInputs = page.locator('input[type="file"][accept=".xlsx"]');
		await xlsxInputs.nth(1).setInputFiles(FIXTURES.anwesenheitsliste);
		await page.getByRole('button', { name: 'Weiter' }).click();

		// AWL roster rows must show up even with no dedicated Teilnehmende/Leiter file uploaded.
		await expect(mappingRow(page, 'Trainer')).toBeVisible();
		await expect(mappingRow(page, 'Müller')).toBeVisible();
		await expect(mappingRow(page, 'Meier')).toBeVisible();
	});
});
