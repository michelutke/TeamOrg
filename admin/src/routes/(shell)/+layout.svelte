<script lang="ts">
	import type { Snippet } from 'svelte';
	import Sidebar from '$lib/components/Sidebar.svelte';
	import type { LayoutData } from './$types';

	interface Props {
		data: LayoutData;
		children: Snippet;
	}

	let { data, children }: Props = $props();
</script>

<div class="flex min-h-screen bg-surface-container-low text-on-surface">
	<Sidebar user={data.user} managedClubs={data.managedClubs} lang={data.lang} m={data.m} />
	<main class="flex-1 px-8 py-8">
		{@render children()}
	</main>
</div>
