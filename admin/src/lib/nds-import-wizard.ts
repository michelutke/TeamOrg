// Pure derivation/gating/payload-assembly logic for the NDS import wizard
// (admin/src/lib/components/NdsImportDialog.svelte + step components). Kept dependency-free
// from Svelte so it can be unit-tested without mounting components.

export interface ParsedActivity {
	date: string;
	weekday: string | null;
	symbol: string;
	durationMin: number | null;
}
export interface ParsedMember {
	funktion: string;
	lastName: string;
	firstName: string;
	birthDate: string | null;
}
export interface ParsedAnwesenheitsliste {
	angebotId: string;
	kursName: string | null;
	hauptsportart: string | null;
	gruppengroesse: string | null;
	activities: ParsedActivity[];
	members: ParsedMember[];
	linkedTeamId?: string | null;
	linkedTeamName?: string | null;
}

export interface NdsMemberInput {
	lastName: string;
	firstName: string;
	birthDate: string | null;
	personNumber: string | null;
	funktion: string;
}

export interface CandidateDto {
	userId: string;
	displayName: string;
	score: string; // HIGH | MEDIUM
	birthdateMatch: boolean;
}
export interface MemberSuggestionDto {
	rowKey: string;
	candidates: CandidateDto[];
	preselectedUserId: string | null;
	alreadyLinkedUserId: string | null;
}

export interface NdsSeries {
	seriesKey: string;
	weekday: number | null;
	symbol: string;
	durationMin: number | null;
	dates: string[];
	count: number;
}

export interface NdsConflictDate {
	date: string;
	existingEventId: string;
	existingEventTitle: string;
	existingEventStart: string;
}
export interface NdsConflictGroup {
	seriesKey: string;
	dates: NdsConflictDate[];
}

export interface NdsParseResponse {
	anwesenheitsliste: ParsedAnwesenheitsliste | null;
	persons: NdsMemberInput[];
	memberSuggestions: MemberSuggestionDto[];
	series: NdsSeries[];
	conflicts: NdsConflictGroup[];
	linkedTeamId: string | null;
	linkedTeamName: string | null;
}

// ── rowKey — mirrors server NdsMemberMatcher.rowKey/normalize exactly, so the mapping table can
// join `persons` rows to `memberSuggestions` entries by key. ──

export function normalizeName(s: string): string {
	return s
		.trim()
		.toLowerCase()
		.replace(/ä/g, 'ae')
		.replace(/ö/g, 'oe')
		.replace(/ü/g, 'ue')
		.replace(/[éèê]/g, 'e')
		.replace(/[àâ]/g, 'a')
		.replace(/ß/g, 'ss')
		.replace(/\s+/g, ' ');
}

export function rowKey(funktion: string, lastName: string, firstName: string): string {
	const prefix = /leiter/i.test(funktion) ? 'L:' : 'T:';
	return `${prefix}${normalizeName(lastName)}|${normalizeName(firstName)}`;
}

/**
 * Union of the Anwesenheitsliste roster and the dedicated person exports, deduped by rowKey —
 * mirrors the server's `mergeMemberRows` (NdsRoutes.kt) exactly, since the parse response's
 * `persons` field only ever carries the Teilnehmende/Leiter file rows, never the AWL roster.
 * The AWL row wins on birthdate (freshest export); the person export's PERSONENNUMMER is kept
 * when the AWL row lacks one.
 */
export function mergeRows(
	awlMembers: ParsedMember[] | undefined,
	persons: NdsMemberInput[]
): NdsMemberInput[] {
	const merged = new Map<string, NdsMemberInput>();
	for (const p of persons) {
		merged.set(rowKey(p.funktion, p.lastName, p.firstName), p);
	}
	for (const m of awlMembers ?? []) {
		const input: NdsMemberInput = {
			lastName: m.lastName,
			firstName: m.firstName,
			birthDate: m.birthDate,
			personNumber: null,
			funktion: m.funktion
		};
		const key = rowKey(input.funktion, input.lastName, input.firstName);
		const existing = merged.get(key);
		merged.set(
			key,
			existing
				? {
						...input,
						personNumber: existing.personNumber ?? input.personNumber,
						birthDate: input.birthDate ?? existing.birthDate
					}
				: input
		);
	}
	return Array.from(merged.values());
}

// ── Step 2: Mitglieder-Zuordnung ──

export type MappingAction = 'map' | 'create' | 'skip';
export interface MappingChoice {
	action: MappingAction;
	userId?: string;
}

/**
 * Default per-row mapping decision from the parse response's suggestions: a unique high-confidence
 * candidate preselects `map`, otherwise `create`. Rows already linked (`alreadyLinkedUserId`) are
 * locked in the UI and excluded here — they never appear in the import payload's `mappings`.
 */
export function defaultMappings(suggestions: MemberSuggestionDto[]): Map<string, MappingChoice> {
	const result = new Map<string, MappingChoice>();
	for (const s of suggestions) {
		if (s.alreadyLinkedUserId) continue;
		result.set(
			s.rowKey,
			s.preselectedUserId ? { action: 'map', userId: s.preselectedUserId } : { action: 'create' }
		);
	}
	return result;
}

// ── Step 3: Events & Konflikte ──

export interface SeriesTimeInput {
	startTime: string; // HH:mm
	endTime: string; // HH:mm
	location?: string;
}

export type ConflictKeep = 'teamorg' | 'nds';
export interface ConflictResolutionInput {
	keep: ConflictKeep;
	overrides: Map<string, ConflictKeep>; // date -> keep
}

const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/;

export function isValidTimeRange(startTime: string, endTime: string): boolean {
	if (!TIME_PATTERN.test(startTime) || !TIME_PATTERN.test(endTime)) return false;
	return endTime > startTime; // HH:mm strings compare lexicographically like the times they encode
}

export function effectiveKeep(
	conflictGroup: NdsConflictGroup | undefined,
	resolution: ConflictResolutionInput | undefined,
	date: string
): ConflictKeep {
	if (!conflictGroup) return 'nds'; // no conflict on this date → nothing blocks the import
	const groupKeep = resolution?.keep ?? 'teamorg';
	return resolution?.overrides.get(date) ?? groupKeep;
}

/** Bestätigen-step summary counts: how many event dates import as new NDS events vs. keep TeamOrg/NDS. */
export function conflictCounts(
	series: NdsSeries[],
	conflicts: NdsConflictGroup[],
	resolutions: Map<string, ConflictResolutionInput>
): { eventsNew: number; keepTeamorg: number; keepNds: number } {
	let eventsNew = 0;
	let keepTeamorg = 0;
	let keepNds = 0;
	for (const s of series) {
		const conflictGroup = conflicts.find((c) => c.seriesKey === s.seriesKey);
		const resolution = resolutions.get(s.seriesKey);
		for (const d of s.dates) {
			const isConflict = conflictGroup?.dates.some((cd) => cd.date === d) ?? false;
			const keep = effectiveKeep(conflictGroup, resolution, d);
			if (keep === 'teamorg') keepTeamorg++;
			else {
				eventsNew++;
				if (isConflict) keepNds++;
			}
		}
	}
	return { eventsNew, keepTeamorg, keepNds };
}

/** Whether a series imports at least one event date (i.e. isn't fully resolved keep-TeamOrg). */
export function seriesImportsAnyEvent(
	series: NdsSeries,
	conflicts: NdsConflictGroup[],
	resolutions: Map<string, ConflictResolutionInput>
): boolean {
	const conflictGroup = conflicts.find((c) => c.seriesKey === series.seriesKey);
	const resolution = resolutions.get(series.seriesKey);
	return series.dates.some((d) => effectiveKeep(conflictGroup, resolution, d) !== 'teamorg');
}

/**
 * Gates the "Weiter" button on the Events & Konflikte step: every series that imports at least one
 * event must have a valid seriesTime (both HH:mm, end after start). A series fully resolved
 * keep-TeamOrg needs no time at all.
 */
export function step3Gate(
	series: NdsSeries[],
	conflicts: NdsConflictGroup[],
	times: Map<string, SeriesTimeInput>,
	resolutions: Map<string, ConflictResolutionInput>
): boolean {
	for (const s of series) {
		if (!seriesImportsAnyEvent(s, conflicts, resolutions)) continue;
		const t = times.get(s.seriesKey);
		if (!t || !isValidTimeRange(t.startTime, t.endTime)) return false;
	}
	return true;
}

// ── Step 4: Bestätigen — payload assembly ──

export interface AssemblePayloadInput {
	teamId?: string | null;
	createTeamName?: string | null;
	nutzergruppe?: string | null;
	parsed: ParsedAnwesenheitsliste | null;
	persons: NdsMemberInput[];
	importEvents: boolean;
	attendanceMode: 'keep' | 'discard';
	mappings: Map<string, MappingChoice>;
	seriesTimes: Map<string, SeriesTimeInput>;
	resolutions: Map<string, ConflictResolutionInput>;
}

export function assemblePayload(input: AssemblePayloadInput) {
	const mappings = Array.from(input.mappings.entries()).map(([key, choice]) => ({
		rowKey: key,
		action: choice.action,
		userId: choice.userId ?? null
	}));
	const seriesTimes = Array.from(input.seriesTimes.entries()).map(([seriesKey, t]) => ({
		seriesKey,
		startTime: t.startTime,
		endTime: t.endTime,
		location: t.location?.trim() ? t.location.trim() : null
	}));
	const conflictResolutions = Array.from(input.resolutions.entries()).map(([seriesKey, r]) => ({
		seriesKey,
		keep: r.keep,
		overrides: Array.from(r.overrides.entries()).map(([date, keep]) => ({ date, keep }))
	}));

	return {
		...(input.teamId
			? { teamId: input.teamId }
			: { createTeamName: (input.createTeamName ?? '').trim() }),
		nutzergruppe: input.nutzergruppe || null,
		parsed: input.parsed,
		persons: input.persons,
		importEvents: input.importEvents,
		attendanceMode: input.attendanceMode,
		mappings,
		seriesTimes,
		conflictResolutions
	};
}
