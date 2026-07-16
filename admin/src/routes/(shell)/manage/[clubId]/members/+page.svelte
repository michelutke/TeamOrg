<script lang="ts">
	import { enhance } from '$app/forms';
	import { page } from '$app/state';
	import type { PageData } from './$types';
	import type { ClubUser } from './+page.server';

	interface Props { data: PageData }
	let { data }: Props = $props();

	// page.params is reactive in rune context — no $derived wrapper needed
	const clubId = page.params.clubId;

	let users = $state<ClubUser[]>(data.users);
	let offset = $state(data.users.length);
	let done = $state(data.users.length < data.pageSize);
	let loading = $state(false);
	let filter = $state('');

	const shown = $derived(
		filter.trim()
			? users.filter((u) =>
					`${u.displayName} ${u.email}`.toLowerCase().includes(filter.toLowerCase())
				)
			: users
	);

	async function loadMore() {
		if (loading || done) return;
		loading = true;
		try {
			const res = await fetch(`/manage/${clubId}/users?limit=${data.pageSize}&offset=${offset}`);
			const next = (await res.json()) as ClubUser[];
			users = [...users, ...next];
			offset += next.length;
			if (next.length < data.pageSize) done = true;
		} finally {
			loading = false;
		}
	}

	// Refetch page 1 after any mutation so the list reflects the change
	async function refreshList() {
		const res = await fetch(`/manage/${clubId}/users?limit=${data.pageSize}&offset=0`);
		const fresh = (await res.json()) as ClubUser[];
		users = fresh;
		offset = fresh.length;
		done = fresh.length < data.pageSize;
	}

	const mutationEnhance = () =>
		({ result }: { result: { type: string } }) => {
			if (result.type === 'success' || result.type === 'redirect') {
				return refreshList();
			}
		};

	const inputClasses =
		'w-full rounded-2xl border-none bg-surface-container-high px-[18px] py-3 text-[14px] text-on-surface outline-none placeholder:text-on-surface-variant focus:ring-2 focus:ring-primary';
	const labelClasses = 'mb-1 block text-[12px] font-medium text-on-surface-variant';
	const filledBtn =
		'cursor-pointer rounded-full border-none bg-primary px-6 py-3 text-[14px] font-bold text-on-primary hover:opacity-90';
	const outlinedBtn =
		'cursor-pointer rounded-full border border-outline-variant bg-transparent px-6 py-3 text-[14px] font-medium text-on-surface-variant hover:bg-surface-container-high';

	const ROLES = ['player', 'coach'];
	const hasTeams = $derived(data.teams.length > 0);
</script>

<svelte:head>
	<title>{data.m.manage.membersPageTitle} — TeamOrg</title>
</svelte:head>

<div class="flex flex-col gap-6">
	<!-- Page header -->
	<div class="flex items-center justify-between">
		<div class="flex flex-col gap-1">
			<h1 class="font-display text-[30px] font-extrabold text-on-surface">{data.m.manage.manageMembersTitle}</h1>
			<p class="text-[13px] text-on-surface-variant">{shown.length} {data.m.manage.shownCountMiddle} {users.length} {data.m.manage.shownCountSuffix}</p>
		</div>
	</div>

	<!-- Invite by email card -->
	<div class="rounded-3xl bg-surface-container-low p-6">
		<h2 class="mb-3 font-display text-[18px] font-bold text-on-surface">{data.m.manage.inviteByEmailTitle}</h2>
		{#if hasTeams}
			<form method="POST" action="?/inviteByEmail" use:enhance={mutationEnhance} class="flex flex-wrap items-end gap-3">
				<div class="min-w-[180px] flex-1">
					<label for="invite-email" class={labelClasses}>{data.m.login.email}</label>
					<input
						id="invite-email"
						name="email"
						type="email"
						required
						placeholder={data.m.manage.emailPlaceholderPerson}
						class={inputClasses}
					/>
				</div>
				<div class="min-w-[140px]">
					<label for="invite-team" class={labelClasses}>{data.m.manage.teamLabel}</label>
					<select id="invite-team" name="teamId" required class={inputClasses}>
						{#each data.teams as t}
							<option value={t.id}>{t.name}</option>
						{/each}
					</select>
				</div>
				<div class="min-w-[120px]">
					<label for="invite-role" class={labelClasses}>{data.m.invite.roleLabel}</label>
					<select id="invite-role" name="role" class={inputClasses}>
						{#each ROLES as r}
							<option value={r}>{r}</option>
						{/each}
					</select>
				</div>
				<button type="submit" class={filledBtn}>{data.m.manage.inviteButton}</button>
			</form>
		{:else}
			<p class="text-[14px] text-on-surface-variant">
				{data.m.manage.noTeamsForInvite}
			</p>
		{/if}
	</div>

	<!-- Filter -->
	<div>
		<input
			bind:value={filter}
			placeholder={data.m.manage.filterPlaceholder}
			class="w-full max-w-sm rounded-2xl border-none bg-surface-container-high px-[18px] py-3 text-[14px] text-on-surface outline-none placeholder:text-on-surface-variant focus:ring-2 focus:ring-primary"
		/>
	</div>

	<!-- User list -->
	{#if shown.length === 0}
		<div class="rounded-3xl bg-surface-container-low px-6 py-12 text-center">
			<p class="text-[14px] text-on-surface-variant">
				{filter.trim() ? data.m.manage.noMatch : data.m.manage.noMembersFound}
			</p>
		</div>
	{:else}
		<div class="flex flex-col gap-4">
			{#each shown as u (u.userId)}
				<div class="rounded-3xl bg-surface-container-low p-5">
					<!-- User info + team chips -->
					<div class="mb-4 flex flex-wrap items-center gap-3">
						<div class="flex-1">
							<p class="text-[15px] font-bold text-on-surface">{u.displayName}</p>
							<p class="text-[13px] text-on-surface-variant">{u.email}</p>
						</div>
						<div class="flex flex-wrap gap-2">
							{#each u.teamRoles as tr}
								<span
									class="rounded-full bg-secondary-container px-3 py-1 text-[12px] font-medium text-on-secondary-container"
								>
									{tr.teamName} · {tr.role}
								</span>
							{/each}
						</div>
					</div>

					<!-- Per-team: change role / remove -->
					{#each u.teamRoles as tr}
						<div class="mb-2 flex flex-wrap items-center gap-2 rounded-2xl bg-surface-container-high px-4 py-2">
							<span class="min-w-[100px] text-[13px] font-medium text-on-surface-variant"
								>{tr.teamName}</span
							>
							<!-- Change role -->
							<form method="POST" action="?/changeRole" use:enhance={mutationEnhance} class="flex items-center gap-2">
								<input type="hidden" name="teamId" value={tr.teamId} />
								<input type="hidden" name="userId" value={u.userId} />
								<select name="role" class="rounded-xl border-none bg-surface-container px-3 py-2 text-[13px] text-on-surface focus:ring-2 focus:ring-primary">
									{#each ROLES as r}
										<option value={r} selected={r === tr.role}>{r}</option>
									{/each}
								</select>
								<button
									type="submit"
									class="cursor-pointer rounded-full border border-outline-variant bg-transparent px-4 py-2 text-[12px] font-medium text-on-surface-variant hover:bg-surface-container"
								>{data.m.common.save}</button>
							</form>
							<!-- Remove -->
							<form method="POST" action="?/removeMember" use:enhance={mutationEnhance} class="ml-auto">
								<input type="hidden" name="teamId" value={tr.teamId} />
								<input type="hidden" name="userId" value={u.userId} />
								<button
									type="submit"
									class="cursor-pointer rounded-full border border-error bg-transparent px-4 py-2 text-[12px] font-medium text-error hover:bg-error-container"
								>{data.m.manage.remove}</button>
							</form>
						</div>
					{/each}

					<!-- Add to team -->
					{#if hasTeams}
						<form method="POST" action="?/addMember" use:enhance={mutationEnhance} class="mt-3 flex flex-wrap items-end gap-2">
							<input type="hidden" name="userId" value={u.userId} />
							<div class="min-w-[140px]">
								<select name="teamId" aria-label={data.m.manage.teamLabel} class="w-full rounded-2xl border-none bg-surface-container-high px-4 py-2 text-[13px] text-on-surface focus:ring-2 focus:ring-primary">
									{#each data.teams as t}
										<option value={t.id}>{t.name}</option>
									{/each}
								</select>
							</div>
							<div class="min-w-[110px]">
								<select name="role" aria-label={data.m.invite.roleLabel} class="w-full rounded-2xl border-none bg-surface-container-high px-4 py-2 text-[13px] text-on-surface focus:ring-2 focus:ring-primary">
									{#each ROLES as r}
										<option value={r}>{r}</option>
									{/each}
								</select>
							</div>
							<button type="submit" class="cursor-pointer rounded-full border-none bg-primary px-4 py-2 text-[13px] font-bold text-on-primary hover:opacity-90">
								{data.m.manage.addTeamButton}
							</button>
						</form>
					{/if}
				</div>
			{/each}
		</div>
	{/if}

	<!-- Lazy load -->
	{#if !done}
		<div class="flex justify-center">
			<button
				type="button"
				onclick={loadMore}
				disabled={loading}
				class={outlinedBtn}
			>
				{loading ? data.m.manage.loadingShort : data.m.manage.loadMore}
			</button>
		</div>
	{/if}
</div>
