import { describe, expect, it } from 'vitest';
import {
	assemblePayload,
	defaultMappings,
	mergeRows,
	rowKey,
	step3Gate,
	type ConflictResolutionInput,
	type MemberSuggestionDto,
	type NdsConflictGroup,
	type NdsSeries,
	type SeriesTimeInput
} from './nds-import-wizard';

describe('rowKey', () => {
	it('prefixes leaders with L: and participants with T:', () => {
		expect(rowKey('Leiter/in', 'Müller', 'Anna')).toBe('L:mueller|anna');
		expect(rowKey('Teilnehmer/in', 'Meier', 'Tom')).toBe('T:meier|tom');
	});
});

describe('mergeRows', () => {
	it('returns AWL rows when there are no person files (AWL-only import)', () => {
		const rows = mergeRows(
			[{ funktion: 'Teilnehmer/in', lastName: 'Meier', firstName: 'Tom', birthDate: '2010-01-01' }],
			[]
		);
		expect(rows).toEqual([
			{
				lastName: 'Meier',
				firstName: 'Tom',
				birthDate: '2010-01-01',
				personNumber: null,
				funktion: 'Teilnehmer/in'
			}
		]);
	});

	it('dedupes by rowKey, AWL birthdate wins, person-file personNumber is preserved', () => {
		const rows = mergeRows(
			[{ funktion: 'Teilnehmer/in', lastName: 'Meier', firstName: 'Tom', birthDate: '2010-01-01' }],
			[
				{
					funktion: 'Teilnehmer/in',
					lastName: 'Meier',
					firstName: 'Tom',
					birthDate: null,
					personNumber: '123456'
				}
			]
		);
		expect(rows).toEqual([
			{
				lastName: 'Meier',
				firstName: 'Tom',
				birthDate: '2010-01-01',
				personNumber: '123456',
				funktion: 'Teilnehmer/in'
			}
		]);
	});

	it('keeps person-file-only rows not present in the AWL', () => {
		const rows = mergeRows(undefined, [
			{ funktion: 'Leiter/in', lastName: 'Huber', firstName: 'Sara', birthDate: null, personNumber: '999' }
		]);
		expect(rows).toEqual([
			{ funktion: 'Leiter/in', lastName: 'Huber', firstName: 'Sara', birthDate: null, personNumber: '999' }
		]);
	});

	it('produces rowKeys that match defaultMappings keys', () => {
		const rows = mergeRows(
			[{ funktion: 'Teilnehmer/in', lastName: 'Meier', firstName: 'Tom', birthDate: '2010-01-01' }],
			[]
		);
		const key = rowKey(rows[0].funktion, rows[0].lastName, rows[0].firstName);
		const suggestions: MemberSuggestionDto[] = [
			{ rowKey: key, candidates: [], preselectedUserId: null, alreadyLinkedUserId: null }
		];
		const mappings = defaultMappings(suggestions);
		expect(mappings.has(key)).toBe(true);
	});
});

describe('defaultMappings', () => {
	it('maps a row with a unique preselected candidate', () => {
		const suggestions: MemberSuggestionDto[] = [
			{
				rowKey: 'T:meier|tom',
				candidates: [{ userId: 'u1', displayName: 'Tom Meier', score: 'HIGH', birthdateMatch: true }],
				preselectedUserId: 'u1',
				alreadyLinkedUserId: null
			}
		];
		const result = defaultMappings(suggestions);
		expect(result.get('T:meier|tom')).toEqual({ action: 'map', userId: 'u1' });
	});

	it('defaults to create when there is no preselection', () => {
		const suggestions: MemberSuggestionDto[] = [
			{
				rowKey: 'T:unbekannt|person',
				candidates: [],
				preselectedUserId: null,
				alreadyLinkedUserId: null
			}
		];
		const result = defaultMappings(suggestions);
		expect(result.get('T:unbekannt|person')).toEqual({ action: 'create' });
	});

	it('excludes already-linked rows entirely (locked in the UI)', () => {
		const suggestions: MemberSuggestionDto[] = [
			{
				rowKey: 'T:verknuepft|person',
				candidates: [],
				preselectedUserId: null,
				alreadyLinkedUserId: 'u9'
			}
		];
		const result = defaultMappings(suggestions);
		expect(result.has('T:verknuepft|person')).toBe(false);
	});
});

describe('step3Gate', () => {
	const series: NdsSeries[] = [
		{ seriesKey: 'mo-T-90', weekday: 0, symbol: 'T', durationMin: 90, dates: ['2026-08-03'], count: 18 }
	];

	it('blocks continue when a series importing events has no time set', () => {
		expect(step3Gate(series, [], new Map(), new Map())).toBe(false);
	});

	it('allows continue once a valid time is set', () => {
		const times = new Map<string, SeriesTimeInput>([
			['mo-T-90', { startTime: '18:00', endTime: '19:30' }]
		]);
		expect(step3Gate(series, [], times, new Map())).toBe(true);
	});

	it('rejects an end time that is not after the start time', () => {
		const times = new Map<string, SeriesTimeInput>([
			['mo-T-90', { startTime: '18:00', endTime: '18:00' }]
		]);
		expect(step3Gate(series, [], times, new Map())).toBe(false);
	});

	it('needs no time for a series fully resolved keep-TeamOrg', () => {
		const conflicts: NdsConflictGroup[] = [
			{
				seriesKey: 'mo-T-90',
				dates: [
					{
						date: '2026-08-03',
						existingEventId: 'e1',
						existingEventTitle: 'Training',
						existingEventStart: '2026-08-03T18:00:00Z'
					}
				]
			}
		];
		const resolutions = new Map<string, ConflictResolutionInput>([
			['mo-T-90', { keep: 'teamorg', overrides: new Map() }]
		]);
		expect(step3Gate(series, conflicts, new Map(), resolutions)).toBe(true);
	});

	it('still needs a time when a per-date override switches a keep-teamorg group back to NDS', () => {
		const conflicts: NdsConflictGroup[] = [
			{
				seriesKey: 'mo-T-90',
				dates: [
					{
						date: '2026-08-03',
						existingEventId: 'e1',
						existingEventTitle: 'Training',
						existingEventStart: '2026-08-03T18:00:00Z'
					}
				]
			}
		];
		const resolutions = new Map<string, ConflictResolutionInput>([
			['mo-T-90', { keep: 'teamorg', overrides: new Map([['2026-08-03', 'nds']]) }]
		]);
		expect(step3Gate(series, conflicts, new Map(), resolutions)).toBe(false);
		const times = new Map<string, SeriesTimeInput>([
			['mo-T-90', { startTime: '18:00', endTime: '19:30' }]
		]);
		expect(step3Gate(series, conflicts, times, resolutions)).toBe(true);
	});
});

describe('assemblePayload', () => {
	it('assembles mappings, seriesTimes and conflictResolutions (incl. overrides) for an existing team', () => {
		const mappings = new Map([
			['T:meier|tom', { action: 'map' as const, userId: 'u1' }],
			['T:neu|person', { action: 'create' as const }],
			['T:uebersprungen|person', { action: 'skip' as const }]
		]);
		const seriesTimes = new Map<string, SeriesTimeInput>([
			['mo-T-90', { startTime: '18:00', endTime: '19:30', location: 'Halle 1' }]
		]);
		const resolutions = new Map<string, ConflictResolutionInput>([
			['mo-T-90', { keep: 'teamorg', overrides: new Map([['2026-08-10', 'nds']]) }]
		]);

		const payload = assemblePayload({
			teamId: 'team-1',
			parsed: null,
			persons: [],
			importEvents: true,
			attendanceMode: 'keep',
			mappings,
			seriesTimes,
			resolutions
		});

		expect(payload).toMatchObject({
			teamId: 'team-1',
			importEvents: true,
			attendanceMode: 'keep',
			mappings: [
				{ rowKey: 'T:meier|tom', action: 'map', userId: 'u1' },
				{ rowKey: 'T:neu|person', action: 'create', userId: null },
				{ rowKey: 'T:uebersprungen|person', action: 'skip', userId: null }
			],
			seriesTimes: [{ seriesKey: 'mo-T-90', startTime: '18:00', endTime: '19:30', location: 'Halle 1' }],
			conflictResolutions: [
				{ seriesKey: 'mo-T-90', keep: 'teamorg', overrides: [{ date: '2026-08-10', keep: 'nds' }] }
			]
		});
		expect(payload).not.toHaveProperty('createTeamName');
	});

	it('uses createTeamName when no teamId is set', () => {
		const payload = assemblePayload({
			createTeamName: '  U14 ',
			parsed: null,
			persons: [],
			importEvents: false,
			attendanceMode: 'discard',
			mappings: new Map(),
			seriesTimes: new Map(),
			resolutions: new Map()
		});
		expect(payload).toMatchObject({ createTeamName: 'U14' });
		expect(payload).not.toHaveProperty('teamId');
	});
});
