<script lang="ts">
	import { enhance } from '$app/forms';
	import { ArrowLeft } from 'lucide-svelte';
	import type { PageData, ActionData } from './$types';

	interface Props {
		data: PageData;
		form: ActionData;
	}

	let { data, form }: Props = $props();

	const roleLabel = (role: string) => data.m.roles[role as keyof typeof data.m.roles] ?? role;

	const initials = $derived(
		data.member.displayName
			.split(/\s+/)
			.slice(0, 2)
			.map((w) => w[0]?.toUpperCase() ?? '')
			.join('')
	);
	const inputCls =
		'w-full rounded-2xl bg-surface-container-high px-4 py-3 text-[14px] text-on-surface outline-none';
</script>

<svelte:head>
	<title>{data.member.displayName} — TeamOrg</title>
</svelte:head>

<a
	href="/app/teams/{data.team.id}"
	class="mb-4 inline-flex items-center gap-1 text-[13px] font-medium text-on-surface-variant hover:text-on-surface"
>
	<ArrowLeft size={16} /> {data.team.name}
</a>

<div class="mx-auto flex max-w-[560px] flex-col gap-6">
	<header class="flex items-center gap-4">
		{#if data.member.avatarUrl}
			<img src={data.member.avatarUrl} alt="" class="size-16 rounded-full object-cover" />
		{:else}
			<span
				class="flex size-16 items-center justify-center rounded-full bg-secondary-container text-[20px] font-bold text-on-secondary-container"
			>
				{initials}
			</span>
		{/if}
		<div>
			<h1 class="font-display text-[24px] font-extrabold text-on-surface">
				{data.member.displayName}
			</h1>
			<p class="text-[13px] text-on-surface-variant">{roleLabel(data.member.role)}</p>
		</div>
	</header>

	{#if data.editable}
		<form method="POST" action="?/save" use:enhance class="flex flex-col gap-4 rounded-[28px] bg-surface p-6">
			<label class="flex flex-col gap-1">
				<span class="text-[12px] font-medium text-primary">{data.m.member.jersey}</span>
				<input
					type="number"
					name="jerseyNumber"
					min="0"
					value={data.member.jerseyNumber ?? ''}
					class={inputCls}
				/>
			</label>
			<label class="flex flex-col gap-1">
				<span class="text-[12px] font-medium text-primary">{data.m.member.position}</span>
				<input name="position" value={data.member.position ?? ''} class={inputCls} />
			</label>
			{#if form?.error}
				<p class="text-[12px] font-medium text-error">{form.error}</p>
			{:else if form?.saved}
				<p class="text-[12px] font-medium text-success">{data.m.member.saved}</p>
			{/if}
			<button
				type="submit"
				class="rounded-full border-none bg-primary py-3 text-[15px] font-bold text-on-primary hover:opacity-90"
			>
				{data.m.common.save}
			</button>
		</form>
	{:else}
		<dl class="flex flex-col gap-3 rounded-[28px] bg-surface p-6 text-[14px]">
			<div class="flex justify-between">
				<dt class="text-on-surface-variant">{data.m.member.jersey}</dt>
				<dd class="font-semibold text-on-surface">{data.member.jerseyNumber ?? '–'}</dd>
			</div>
			<div class="flex justify-between">
				<dt class="text-on-surface-variant">{data.m.member.position}</dt>
				<dd class="font-semibold text-on-surface">{data.member.position ?? '–'}</dd>
			</div>
		</dl>
	{/if}
</div>
