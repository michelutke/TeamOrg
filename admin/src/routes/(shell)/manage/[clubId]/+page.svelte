<script lang="ts">
	import type { PageData, ActionData } from './$types';
	import { fileUrl } from '$lib/urls';

	interface Props {
		data: PageData;
		form: ActionData;
	}

	let { data, form }: Props = $props();
	const logoSrc = $derived(fileUrl(data.club.logoUrl));

	let showEditForm = $state(false);
	let showInviteForm = $state(false);

	const inputClasses =
		'w-full rounded-2xl border-none bg-surface-container-high px-[18px] py-3 text-[14px] text-on-surface outline-none placeholder:text-on-surface-variant focus:ring-2 focus:ring-primary';
	const labelClasses = 'mb-1 block text-[12px] font-medium text-on-surface-variant';
	const filledBtn =
		'cursor-pointer rounded-full border-none bg-primary px-6 py-3 text-[14px] font-bold text-on-primary hover:opacity-90';
	const outlinedBtn =
		'cursor-pointer rounded-full border border-outline-variant bg-transparent px-6 py-3 text-[14px] font-medium text-on-surface-variant hover:bg-surface-container-high';

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
	<title>{data.club.name} — TeamOrg</title>
</svelte:head>

<div class="flex flex-col gap-6">
	<!-- Hero -->
	<div class="flex items-center gap-5 rounded-3xl bg-primary-container px-8 py-7">
		<div class="flex size-16 shrink-0 items-center justify-center overflow-hidden rounded-3xl bg-primary">
			{#if logoSrc}
				<img src={logoSrc} alt="" class="size-full object-cover" />
			{:else}
				<span class="text-[20px] font-bold text-on-primary">{initials(data.club.name)}</span>
			{/if}
		</div>
		<div class="flex flex-col gap-1">
			<h1 class="font-display text-[28px] font-extrabold text-on-surface">{data.club.name}</h1>
			<p class="text-[14px] text-on-primary-container">
				{data.club.sportType}{data.club.location ? ` · ${data.club.location}` : ''}
			</p>
		</div>
		<div class="flex-1"></div>
		{#if !showEditForm}
			<button
				type="button"
				onclick={() => (showEditForm = true)}
				class="cursor-pointer rounded-full border border-on-primary-container bg-white px-6 py-3 text-[14px] font-bold text-on-primary-container hover:bg-surface"
			>Edit</button>
		{/if}
	</div>

	<!-- Edit form -->
	{#if showEditForm}
		<div class="rounded-3xl bg-surface-container-low p-6">
			<h2 class="mb-4 text-[17px] font-bold text-on-surface">Edit club</h2>
			<form method="POST" action="?/edit">
				<div class="mb-4 grid grid-cols-2 gap-4">
					<div>
						<label for="edit-name" class={labelClasses}>Name</label>
						<input id="edit-name" name="name" type="text" value={data.club.name} class={inputClasses} />
					</div>
					<div>
						<label for="edit-location" class={labelClasses}>Location</label>
						<input id="edit-location" name="location" type="text" value={data.club.location || ''} class={inputClasses} />
					</div>
				</div>
				<div class="flex gap-3">
					<button type="submit" class={filledBtn}>Save Changes</button>
					<button type="button" onclick={() => (showEditForm = false)} class={outlinedBtn}>Cancel</button>
				</div>
			</form>

			<div class="mt-6 border-t border-outline-variant pt-6">
				<h3 class="mb-1 text-[15px] font-bold text-on-surface">Club logo</h3>
				<p class="mb-3 text-[13px] text-on-surface-variant">JPG, PNG or WebP, up to 2MB.</p>
				{#if form?.action === 'logo_uploaded'}
					<p class="mb-3 text-[13px] font-medium text-success">Logo updated.</p>
				{:else if form?.logoError}
					<p class="mb-3 text-[13px] font-medium text-error">{form.logoError}</p>
				{/if}
				<form
					method="POST"
					action="?/uploadLogo"
					enctype="multipart/form-data"
					class="flex flex-wrap items-center gap-3"
				>
					<input
						type="file"
						name="logo"
						accept="image/jpeg,image/png,image/webp"
						required
						class="text-[13px] text-on-surface-variant"
					/>
					<button type="submit" class={filledBtn}>Upload logo</button>
				</form>
			</div>
		</div>
	{/if}

	<!-- Invite co-manager -->
	<div class="rounded-3xl bg-surface-container-low p-6">
		<h2 class="mb-4 font-display text-[20px] font-bold text-on-surface">Co-managers</h2>
		<p class="mb-4 text-[14px] text-on-surface-variant">
			Invite another person to co-manage this club.
		</p>

		{#if form?.action === 'invite_sent' && form.inviteUrl}
			<div class="mb-4 rounded-2xl bg-white p-4">
				<p class="mb-2 text-[12px] font-bold text-success">Invite link generated!</p>
				<input
					type="text"
					readonly
					value={form.inviteUrl}
					onclick={(e) => (e.currentTarget as HTMLInputElement).select()}
					class="{inputClasses} cursor-text"
				/>
				<p class="mt-2 text-[12px] text-on-surface-variant">
					Expires: {new Date(form.expiresAt).toLocaleString('en-GB')}
				</p>
			</div>
		{/if}

		{#if !showInviteForm}
			<button type="button" onclick={() => (showInviteForm = true)} class={filledBtn}>
				Invite co-manager
			</button>
		{:else}
			<form method="POST" action="?/inviteCoManager" class="flex items-end gap-3">
				<div class="flex-1">
					{#if form?.error}
						<p class="mb-1 text-[12px] font-medium text-error">{form.error}</p>
					{:else}
						<label for="co-manager-email" class={labelClasses}>Email</label>
					{/if}
					<input
						id="co-manager-email"
						name="email"
						type="email"
						placeholder="colleague@example.com"
						required
						class={inputClasses}
					/>
				</div>
				<button type="submit" class="{filledBtn} whitespace-nowrap">Send invite</button>
				<button
					type="button"
					onclick={() => (showInviteForm = false)}
					class="{outlinedBtn} whitespace-nowrap"
				>Cancel</button>
			</form>
		{/if}
	</div>

	<!-- Quick link to teams -->
	<div class="rounded-3xl bg-surface-container-low p-6">
		<h2 class="mb-3 font-display text-[20px] font-bold text-on-surface">Teams</h2>
		<p class="mb-4 text-[14px] text-on-surface-variant">Manage rosters, invite members, update team info.</p>
		<a
			href="/manage/{data.clubId}/teams"
			class="inline-flex cursor-pointer items-center gap-2 rounded-full border-none bg-primary px-6 py-3 text-[14px] font-bold text-on-primary no-underline hover:opacity-90"
		>
			View Teams ›
		</a>
	</div>

	<!-- Quick link to members -->
	<div class="rounded-3xl bg-surface-container-low p-6">
		<h2 class="mb-3 font-display text-[20px] font-bold text-on-surface">Mitglieder</h2>
		<p class="mb-4 text-[14px] text-on-surface-variant">Alle Club-Mitglieder anzeigen, zu Teams hinzufügen oder entfernen.</p>
		<a
			href="/manage/{data.clubId}/members"
			class="inline-flex cursor-pointer items-center gap-2 rounded-full border-none bg-primary px-6 py-3 text-[14px] font-bold text-on-primary no-underline hover:opacity-90"
		>
			Mitglieder verwalten ›
		</a>
	</div>
</div>
