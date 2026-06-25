<script lang="ts">
	import { enhance } from '$app/forms';
	import { ArrowLeft } from 'lucide-svelte';
	import StatusChip from '$lib/components/StatusChip.svelte';
	import type { PageData } from './$types';

	interface Props {
		data: PageData;
	}

	let { data }: Props = $props();

	const statuses = [
		{ value: 'present', label: data.m.checkin.present, on: 'bg-success-container text-success' },
		{ value: 'excused', label: data.m.checkin.excused, on: 'bg-[#FFF3CD] text-[#7A5B00]' },
		{ value: 'absent', label: data.m.checkin.absent, on: 'bg-error-container text-error' }
	];

	function fmtDate(iso: string): string {
		return new Date(iso).toLocaleDateString(data.lang, { day: 'numeric', month: 'long' });
	}
</script>

<svelte:head>
	<title>{data.m.checkin.title} — {data.event.title}</title>
</svelte:head>

<a
	href="/app/teams/{data.teamId}"
	class="mb-4 inline-flex items-center gap-1 text-[13px] font-medium text-on-surface-variant hover:text-on-surface"
>
	<ArrowLeft size={16} /> {data.m.common.back}
</a>

<header class="mb-6">
	<h1 class="font-display text-[26px] font-extrabold text-on-surface">{data.m.checkin.title}</h1>
	<p class="text-[13px] text-on-surface-variant">{data.event.title} · {fmtDate(data.event.startAt)}</p>
</header>

{#if data.entries.length === 0}
	<p class="text-[14px] text-on-surface-variant">{data.m.checkin.empty}</p>
{:else}
	<div class="flex flex-col gap-2">
		{#each data.entries as entry (entry.userId)}
			<div class="flex flex-wrap items-center gap-3 rounded-2xl bg-surface px-4 py-3">
				<div class="min-w-0 flex-1">
					<p class="truncate text-[14px] font-semibold text-on-surface">{entry.userName}</p>
					{#if entry.response}
						<StatusChip status={entry.response.status as never} m={data.m.rsvp} size="sm" />
					{/if}
				</div>
				<div class="flex gap-1.5">
					{#each statuses as s (s.value)}
						<form method="POST" action="?/setStatus" use:enhance>
							<input type="hidden" name="userId" value={entry.userId} />
							<input type="hidden" name="status" value={s.value} />
							<button
								type="submit"
								class="rounded-full px-3 py-1.5 text-[12px] font-semibold transition-colors {entry
									.record?.status === s.value
									? s.on
									: 'bg-surface-container-high text-on-surface-variant hover:bg-surface-container-low'}"
							>
								{s.label}
							</button>
						</form>
					{/each}
				</div>
			</div>
		{/each}
	</div>
{/if}
