<script lang="ts">
	import type { PageData } from './$types';

	interface Props {
		data: PageData;
	}

	let { data }: Props = $props();

	const fmtDate = (iso: string) =>
		new Intl.DateTimeFormat('en-GB', {
			weekday: 'short',
			day: '2-digit',
			month: '2-digit',
			hour: '2-digit',
			minute: '2-digit'
		}).format(new Date(iso));
</script>

<svelte:head>
	<title>Attendance — {data.team.name}</title>
</svelte:head>

<div class="flex flex-col gap-6">
	<nav class="text-[13px]">
		<a
			href="/manage/{data.clubId}/teams/{data.team.id}"
			class="text-on-surface-variant no-underline hover:text-primary">‹ Back to {data.team.name}</a
		>
	</nav>

	<div>
		<h1 class="font-display text-[24px] font-extrabold text-on-surface">Attendance</h1>
		<p class="text-[13px] text-on-surface-variant">
			{data.team.name} · {data.memberCount} member{data.memberCount === 1 ? '' : 's'}
		</p>
	</div>

	{#if data.summary.length === 0}
		<div class="rounded-3xl bg-surface-container-low p-8 text-center">
			<p class="text-[14px] text-on-surface-variant">No events for this team yet.</p>
		</div>
	{:else}
		<div class="overflow-hidden rounded-3xl bg-surface-container-low py-1">
			<div class="overflow-x-auto">
				<table class="w-full border-collapse">
					<thead>
						<tr>
							<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">Event</th>
							<th scope="col" class="px-4 py-3.5 text-center text-[12px] font-bold text-success">✓ In</th>
							<th scope="col" class="px-4 py-3.5 text-center text-[12px] font-bold text-on-surface-variant">? Unsure</th>
							<th scope="col" class="px-4 py-3.5 text-center text-[12px] font-bold text-error">✗ Out</th>
							<th scope="col" class="px-4 py-3.5 text-center text-[12px] font-bold text-on-surface-variant">No reply</th>
						</tr>
					</thead>
					<tbody>
						{#each data.summary as ev (ev.id)}
							<tr class="border-t border-outline-variant bg-white">
								<td class="px-6 py-[13px]">
									<a href="/app/events/{ev.id}" class="block hover:text-primary">
										<span class="text-[14px] font-medium text-on-surface">{ev.title}</span>
										{#if ev.status === 'cancelled'}
											<span class="ml-2 rounded-full bg-error-container px-2 py-0.5 text-[10px] font-bold text-error">Cancelled</span>
										{/if}
										<span class="block text-[12px] text-on-surface-variant">{fmtDate(ev.startAt)}</span>
									</a>
								</td>
								<td class="px-4 py-[13px] text-center text-[14px] font-semibold text-success">{ev.confirmed}</td>
								<td class="px-4 py-[13px] text-center text-[14px] text-on-surface-variant">{ev.unsure}</td>
								<td class="px-4 py-[13px] text-center text-[14px] font-semibold text-error">{ev.declined}</td>
								<td class="px-4 py-[13px] text-center text-[14px] text-on-surface-variant">{ev.noResponse}</td>
							</tr>
						{/each}
					</tbody>
				</table>
			</div>
		</div>
	{/if}
</div>
