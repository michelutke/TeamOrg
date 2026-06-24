<script lang="ts">
	import type { PageData } from './$types';

	interface Props {
		data: PageData;
	}

	let { data }: Props = $props();

	function initials(name: string): string {
		return name
			.split(' ')
			.map((p) => p[0])
			.slice(0, 2)
			.join('')
			.toUpperCase();
	}
</script>

<svelte:head>
	<title>Select Club — TeamOrg</title>
</svelte:head>

<div class="flex min-h-screen items-center justify-center bg-surface p-6">
	<div class="w-full max-w-[480px]">
		<div class="mb-8 text-center">
			<div class="mx-auto mb-4 flex size-[56px] items-center justify-center rounded-2xl bg-primary-container">
				<span class="text-[20px] font-bold text-on-primary-container">TO</span>
			</div>
			<h1 class="font-display text-[26px] font-extrabold text-on-surface">Your Clubs</h1>
			<p class="mt-1 text-[14px] text-on-surface-variant">Select a club to manage</p>
		</div>

		<div class="flex flex-col gap-3">
			{#each data.clubs as club}
				<a
					href="/manage/{club.id}"
					class="flex items-center gap-4 rounded-[24px] bg-surface-container-low px-5 py-4 no-underline transition-colors hover:bg-surface-container"
				>
					<div class="flex size-[44px] shrink-0 items-center justify-center rounded-2xl bg-primary-container">
						<span class="text-[15px] font-bold text-on-primary-container">{initials(club.name)}</span>
					</div>
					<div class="flex flex-col">
						<span class="text-[16px] font-bold text-on-surface">{club.name}</span>
						<span class="text-[12px] text-on-surface-variant">
							{club.sportType}{club.location ? ` · ${club.location}` : ''}
						</span>
					</div>
					<span class="ml-auto text-[18px] text-on-surface-variant">›</span>
				</a>
			{/each}
		</div>

		<form method="POST" action="/admin/logout" class="mt-8 text-center">
			<button
				type="submit"
				class="cursor-pointer rounded-full border-none bg-transparent px-6 py-2 text-[14px] font-medium text-on-surface-variant hover:bg-surface-container-high"
			>
				Log out
			</button>
		</form>
	</div>
</div>
