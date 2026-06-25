<script lang="ts">
	import type { Snippet } from 'svelte';

	export interface Member {
		userId: string;
		displayName: string;
		avatarUrl: string | null;
		role: string;
		jerseyNumber: number | null;
		position: string | null;
	}

	interface Props {
		member: Member;
		roleLabel: (role: string) => string;
		actions?: Snippet<[Member]>;
	}

	let { member, roleLabel, actions }: Props = $props();

	const initials = $derived(
		member.displayName
			.split(/\s+/)
			.slice(0, 2)
			.map((w) => w[0]?.toUpperCase() ?? '')
			.join('')
	);
</script>

<div class="flex items-center gap-3 rounded-2xl bg-surface px-4 py-3">
	{#if member.avatarUrl}
		<img src={member.avatarUrl} alt="" class="size-10 shrink-0 rounded-full object-cover" />
	{:else}
		<span
			class="flex size-10 shrink-0 items-center justify-center rounded-full bg-secondary-container text-[14px] font-bold text-on-secondary-container"
		>
			{initials}
		</span>
	{/if}

	<div class="min-w-0 flex-1">
		<p class="truncate text-[14px] font-semibold text-on-surface">{member.displayName}</p>
		<p class="truncate text-[12px] text-on-surface-variant">
			{roleLabel(member.role)}{#if member.position}
				· {member.position}{/if}
		</p>
	</div>

	{#if member.jerseyNumber != null}
		<span
			class="flex size-8 shrink-0 items-center justify-center rounded-full bg-primary-container text-[13px] font-bold text-on-primary-container"
		>
			{member.jerseyNumber}
		</span>
	{/if}

	{#if actions}
		{@render actions(member)}
	{/if}
</div>
