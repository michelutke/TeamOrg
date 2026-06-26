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
	<title>{data.m.eventForm.newTitle} — TeamOrg</title>
</svelte:head>

<EventForm
	teams={data.teams}
	m={data.m}
	heading={data.m.eventForm.newTitle}
	submitLabel={data.m.common.create}
	error={form?.error}
	initial={data.initial ?? {}}
/>
