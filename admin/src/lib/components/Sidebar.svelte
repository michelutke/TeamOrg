<script lang="ts">
	import { page } from '$app/stores';
	import { goto } from '$app/navigation';
	import {
		House,
		CalendarDays,
		Users,
		Inbox,
		User,
		LogOut,
		Building2,
		Trophy,
		Shield
	} from 'lucide-svelte';

	interface NavUser {
		displayName: string;
		email: string;
		isSuperAdmin: boolean;
		managedClubIds: string[];
		teamRoles: unknown[];
	}

	interface Props {
		user: NavUser;
		managedClubs: { id: string; name: string }[];
		lang: string;
		m: { nav: Record<string, string> };
	}

	let { user, managedClubs, lang, m }: Props = $props();

	const isManager = $derived(user.managedClubIds.length > 0);
	const isMember = $derived(user.teamRoles.length > 0);
	const showHeaders = $derived(isManager && isMember);

	const pathname = $derived($page.url.pathname);
	// Active club: the one in the URL on /manage/*, else the first managed club.
	const activeClubId = $derived($page.params.clubId ?? managedClubs[0]?.id ?? null);

	const managerNav = $derived(
		activeClubId
			? [
					{ href: `/manage/${activeClubId}`, label: m.nav.club, icon: Building2, exact: true },
					{ href: `/manage/${activeClubId}/teams`, label: m.nav.teams, icon: Trophy, exact: false }
				]
			: []
	);

	const memberNav = $derived([
		{ href: '/app', label: m.nav.start, icon: House, exact: true },
		{ href: '/app/events', label: m.nav.termine, icon: CalendarDays, exact: false },
		{ href: '/app/teams', label: m.nav.teams, icon: Users, exact: false },
		{ href: '/app/inbox', label: m.nav.inbox, icon: Inbox, exact: false }
	]);

	function isActive(href: string, exact: boolean): boolean {
		return exact ? pathname === href : pathname.startsWith(href);
	}

	const linkBase =
		'flex items-center gap-3 rounded-full px-4 py-3 text-[14px] no-underline transition-colors';
	const linkActive = 'bg-secondary-container font-bold text-on-secondary-container';
	const linkIdle = 'font-medium text-on-surface-variant hover:bg-surface-container-high';

	function onClubChange(e: Event) {
		const id = (e.currentTarget as HTMLSelectElement).value;
		goto(`/manage/${id}`);
	}
</script>

<aside
	class="sticky top-0 flex h-screen w-[240px] flex-col gap-2 border-r border-outline-variant bg-surface px-4 py-6"
>
	<a href={isManager && !isMember ? `/manage/${activeClubId}` : '/app'} class="mb-4 flex items-center gap-3 px-2">
		<span
			class="flex size-10 shrink-0 items-center justify-center rounded-full bg-primary-container text-[15px] font-bold text-on-primary-container"
		>
			TO
		</span>
		<span class="font-display text-[18px] font-extrabold">TeamOrg</span>
	</a>

	<nav class="flex flex-1 flex-col gap-1">
		<!-- Club management section -->
		{#if isManager && activeClubId}
			{#if managedClubs.length > 1}
				<select
					value={activeClubId}
					onchange={onClubChange}
					class="mb-1 w-full cursor-pointer rounded-2xl border-none bg-surface-container-high px-3 py-2 text-[13px] font-semibold text-on-surface outline-none"
				>
					{#each managedClubs as c (c.id)}
						<option value={c.id}>{c.name}</option>
					{/each}
				</select>
			{/if}
			{#if showHeaders}
				<p class="px-4 pb-1 pt-2 text-[11px] font-bold uppercase tracking-wide text-on-surface-variant">
					{m.nav.manageArea}
				</p>
			{/if}
			{#each managerNav as item (item.href)}
				<a href={item.href} class="{linkBase} {isActive(item.href, item.exact) ? linkActive : linkIdle}">
					<item.icon size={20} />
					{item.label}
				</a>
			{/each}
		{/if}

		<!-- Member participation section -->
		{#if isMember}
			{#if showHeaders}
				<p class="px-4 pb-1 pt-4 text-[11px] font-bold uppercase tracking-wide text-on-surface-variant">
					{m.nav.memberSection}
				</p>
			{/if}
			{#each memberNav as item (item.href)}
				<a href={item.href} class="{linkBase} {isActive(item.href, item.exact) ? linkActive : linkIdle}">
					<item.icon size={20} />
					{item.label}
				</a>
			{/each}
		{/if}
	</nav>

	<div class="flex flex-col gap-2 border-t border-outline-variant pt-4">
		<a href="/app/profile" class="{linkBase} {isActive('/app/profile', false) ? linkActive : linkIdle}">
			<User size={20} />
			{m.nav.profil}
		</a>

		<div class="flex items-center gap-1 px-2 text-[12px] font-medium">
			<a
				href="?lang=de"
				data-sveltekit-reload
				class="rounded-full px-3 py-1 transition-colors {lang === 'de'
					? 'bg-secondary-container text-on-secondary-container'
					: 'text-on-surface-variant hover:bg-surface-container-high'}">DE</a
			>
			<a
				href="?lang=en"
				data-sveltekit-reload
				class="rounded-full px-3 py-1 transition-colors {lang === 'en'
					? 'bg-secondary-container text-on-secondary-container'
					: 'text-on-surface-variant hover:bg-surface-container-high'}">EN</a
			>
		</div>

		<div class="px-4">
			<p class="truncate text-[13px] font-semibold text-on-surface">{user.displayName}</p>
			<p class="truncate text-[11px] text-on-surface-variant">{user.email}</p>
		</div>

		{#if user.isSuperAdmin}
			<a
				href="/admin/dashboard"
				class="flex items-center gap-3 rounded-full px-4 py-2 text-[13px] font-medium text-on-surface-variant hover:bg-surface-container-high"
			>
				<Shield size={18} />
				{m.nav.adminArea}
			</a>
		{/if}

		<form method="POST" action="/logout">
			<button
				type="submit"
				class="flex w-full items-center gap-3 rounded-full border-none bg-transparent px-4 py-3 text-[14px] font-medium text-on-surface-variant hover:bg-surface-container-high"
			>
				<LogOut size={20} />
				{m.nav.logout}
			</button>
		</form>
	</div>
</aside>
