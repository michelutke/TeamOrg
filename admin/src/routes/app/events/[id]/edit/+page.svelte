<script lang="ts">
	import EventForm from '$lib/components/EventForm.svelte';
	import type { PageData, ActionData } from './$types';

	interface Props {
		data: PageData;
		form: ActionData;
	}

	let { data, form }: Props = $props();
</script>

<svelte:head>
	<title>{data.m.eventForm.editTitle} — TeamOrg</title>
</svelte:head>

<EventForm
	teams={data.teams}
	m={data.m}
	heading={data.m.eventForm.editTitle}
	submitLabel={data.m.common.save}
	error={form?.error}
	initial={{
		title: data.event.title,
		type: data.event.type,
		startAt: data.event.startAt,
		endAt: data.event.endAt,
		meetupAt: data.event.meetupAt,
		location: data.event.location,
		description: data.event.description,
		minAttendees: data.event.minAttendees,
		teamIds: data.event.teamIds
	}}
/>
