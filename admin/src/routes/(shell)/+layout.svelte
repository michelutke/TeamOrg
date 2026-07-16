<script lang="ts">
	import type { Snippet } from 'svelte';
	import { enhance } from '$app/forms';
	import Sidebar from '$lib/components/Sidebar.svelte';
	import type { LayoutData } from './$types';

	interface Props {
		data: LayoutData;
		children: Snippet;
	}

	let { data, children }: Props = $props();

	const isImpersonating = $derived(!!data.impersonation?.active);
</script>

{#if isImpersonating}
	<div
		class="fixed inset-x-0 top-0 z-50 flex items-center justify-between gap-4 bg-amber-500 px-4 py-2 text-sm text-amber-950"
	>
		<div>
			<span class="font-semibold">
				Impersonating {data.impersonation?.targetName}
				{#if data.impersonation?.clubName}@ {data.impersonation.clubName}{/if}
			</span>
			<span class="ml-2 opacity-80">All actions are audit-logged with impersonation context</span>
		</div>
		<form method="POST" action="/admin/impersonate/end" use:enhance>
			<button
				type="submit"
				class="rounded bg-amber-950/10 px-3 py-1 font-medium hover:bg-amber-950/20"
			>
				End Impersonation
			</button>
		</form>
	</div>
{/if}

<div
	class="flex min-h-screen bg-surface-container-low text-on-surface {isImpersonating
		? 'pt-[44px]'
		: ''}"
>
	<Sidebar user={data.user} managedClubs={data.managedClubs} lang={data.lang} m={data.m} />
	<main class="flex-1 px-8 py-8">
		{@render children()}
	</main>
</div>
