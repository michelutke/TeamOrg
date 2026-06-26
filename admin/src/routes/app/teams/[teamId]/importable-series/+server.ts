import { apiGet } from '$lib/server/api';
import { ApiError, requireTeamManage } from '$lib/server/guards';
import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';

interface ImportableSeries {
	seriesId: string;
	patternType: string;
	weekdays: number[] | null;
	intervalDays: number | null;
	templateStartTime: string;
	templateEndTime: string;
	templateMeetupTime: string | null;
	templateTitle: string;
	templateType: string;
	templateLocation: string | null;
	templateMinAttendees: number | null;
	seriesStartDate: string;
	seriesEndDate: string | null;
	label: string;
}

interface ImportableSeriesResult {
	hasOwnSeries: boolean;
	predecessorTeamId: string | null;
	series: ImportableSeries[];
}

export const GET: RequestHandler = async ({ params, locals }) => {
	requireTeamManage(locals, params.teamId);
	try {
		const result = await apiGet<ImportableSeriesResult>(
			`/teams/${params.teamId}/importable-series`,
			locals.token!
		);
		return json(result);
	} catch (err) {
		if (err instanceof ApiError) return json({ error: true }, { status: err.status });
		return json({ error: true }, { status: 502 });
	}
};
