<script lang="ts">
	import { enhance } from '$app/forms';
	import { ArrowLeft, MapPin, Clock, Pencil, ClipboardCheck, Copy, RotateCcw, Ban, Lock, CheckCircle, X } from 'lucide-svelte';
	import StatusChip from '$lib/components/StatusChip.svelte';
	import type { PageData, ActionData } from './$types';

	interface Props {
		data: PageData;
		form: ActionData;
	}

	let { data, form }: Props = $props();

	const typeLabel = (t: string) => data.m.eventTypes[t as keyof typeof data.m.eventTypes] ?? t;

	// Reason field is shown only when the user picks "unsure".
	let chosen = $state<string | null>(data.myResponse?.status ?? null);

	// Coach edit popup state.
	interface EditTarget {
		userId: string;
		displayName: string;
		currentStatus: string;
	}
	let editTarget = $state<EditTarget | null>(null);
	let editStatus = $state<'confirmed' | 'declined'>('confirmed');
	let editUnexcused = $state(false);

	// Finalize blocked dialog — driven by local state so OK dismisses without navigating.
	let dismissedFinalize = $state(false);
	const showFinalizeBlocked = $derived(!dismissedFinalize && !!form?.finalizeBlocked);

	function openEdit(r: { userId: string; displayName: string; status: string; unexcused: boolean }) {
		editTarget = { userId: r.userId, displayName: r.displayName, currentStatus: r.status };
		editStatus = r.status === 'declined' || r.status === 'declined-auto' ? 'declined' : 'confirmed';
		editUnexcused = r.unexcused;
	}

	function closeEdit() {
		editTarget = null;
	}

	// `datetime-local` wants `YYYY-MM-DDTHH:mm` in local time.
	function toLocalInput(iso: string | null): string {
		if (!iso) return '';
		const d = new Date(iso);
		const pad = (n: number) => String(n).padStart(2, '0');
		return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
	}

	function fmtDateTime(iso: string): string {
		return new Date(iso).toLocaleString(data.lang, {
			weekday: 'long',
			day: 'numeric',
			month: 'long',
			hour: '2-digit',
			minute: '2-digit'
		});
	}

	const counts = $derived({
		confirmed: data.responses.filter((r) => r.status === 'confirmed').length,
		unsure: data.responses.filter((r) => r.status === 'unsure').length,
		declined: data.responses.filter((r) => r.status === 'declined' || r.status === 'declined-auto')
			.length
	});

	const rsvpButtons = [
		{ value: 'confirmed', label: data.m.rsvp.confirmed, on: 'bg-success-container text-success' },
		{ value: 'unsure', label: data.m.rsvp.unsure, on: 'bg-[#FFF3CD] text-[#7A5B00]' },
		{ value: 'declined', label: data.m.rsvp.declined, on: 'bg-error-container text-error' }
	];

	const rsvpLocked = $derived(data.event.checkInStatus !== 'open');

	// Map blocked userIds to display names for the finalize dialog.
	const blockedNames = $derived(
		form?.finalizeBlocked?.userIds?.map(
			(uid: string) => data.responses.find((r) => r.userId === uid)?.displayName ?? uid
		) ?? []
	);
</script>

<svelte:head>
	<title>{data.event.title} — TeamOrg</title>
</svelte:head>

<a
	href="/app/events"
	class="mb-4 inline-flex items-center gap-1 text-[13px] font-medium text-on-surface-variant hover:text-on-surface"
>
	<ArrowLeft size={16} /> {data.m.common.back}
</a>

<div class="mx-auto flex max-w-[720px] flex-col gap-6">
	{#if data.canReconcile && data.event.needsReview}
		<!-- SwissVolley needs-review banner + reconcile form -->
		<section class="rounded-[28px] bg-[#FFF3CD] p-6 text-[#7A5B00]">
			<h2 class="font-display text-[18px] font-extrabold">{data.m.reconcile.needsReviewTitle}</h2>
			<p class="mt-1 text-[13px]">{data.m.reconcile.needsReviewBody}</p>

			<form method="POST" action="?/reconcile" use:enhance class="mt-4 flex flex-col gap-3">
				<label class="flex flex-col gap-1 text-[13px] font-semibold">
					{data.m.reconcile.meetupAt}
					<input
						type="datetime-local"
						name="meetupAt"
						value={toLocalInput(data.event.meetupAt)}
						class="rounded-2xl bg-surface px-4 py-3 text-[14px] font-normal text-on-surface outline-none"
					/>
				</label>

				<label class="flex flex-col gap-1 text-[13px] font-semibold">
					{data.m.reconcile.notes}
					<textarea
						name="notes"
						rows="2"
						value={data.event.description ?? ''}
						class="rounded-2xl bg-surface px-4 py-3 text-[14px] font-normal text-on-surface outline-none"
					></textarea>
				</label>

				<label class="flex flex-col gap-1 text-[13px] font-semibold">
					{data.m.reconcile.minAttendees}
					<input
						type="number"
						name="minAttendees"
						min="0"
						value={data.event.minAttendees ?? ''}
						class="rounded-2xl bg-surface px-4 py-3 text-[14px] font-normal text-on-surface outline-none"
					/>
				</label>

				<fieldset class="flex flex-col gap-2">
					<legend class="text-[13px] font-semibold">{data.m.reconcile.availabilityTitle}</legend>
					<label class="flex items-center gap-2 text-[14px] font-normal">
						<input type="checkbox" name="resetAvailability" class="size-4" />
						{data.m.reconcile.resetAvailability}
					</label>
					<p class="text-[12px] font-normal opacity-80">{data.m.reconcile.keepAvailability}</p>
				</fieldset>

				<button
					type="submit"
					class="self-start rounded-full bg-primary px-5 py-3 text-[14px] font-bold text-on-primary hover:opacity-90"
				>
					{data.m.reconcile.submit}
				</button>

				{#if form?.reconcileError}
					<p class="text-[12px] font-medium text-error">{form.reconcileError}</p>
				{:else if form?.reconciled}
					<p class="text-[12px] font-medium text-success">{data.m.reconcile.reconciled}</p>
				{/if}
			</form>
		</section>
	{/if}

	<!-- Locked / awaiting / done banner -->
	{#if data.event.checkInStatus === 'locked' || data.event.checkInStatus === 'awaiting_checkin'}
		<section class="flex items-center gap-3 rounded-[28px] bg-surface-container-high px-6 py-4 text-on-surface-variant">
			<Lock size={18} class="shrink-0" />
			<p class="text-[13px]">Anmeldeschluss ist vorbei. Trainer können den Status der Mitglieder noch bearbeiten.</p>
		</section>
	{:else if data.event.checkInStatus === 'done'}
		<section class="flex items-center gap-3 rounded-[28px] bg-success-container px-6 py-4 text-success">
			<CheckCircle size={18} class="shrink-0" />
			<p class="text-[13px]">CheckIn abgeschlossen{data.event.checkInCompletedAt ? ' — ' + fmtDateTime(data.event.checkInCompletedAt) : ''}.</p>
		</section>
	{/if}

	<!-- Header -->
	<header class="rounded-[28px] bg-surface p-6">
		<div class="flex items-start justify-between gap-4">
			<div>
				<div class="mb-2 flex flex-wrap items-center gap-2">
					<span
						class="inline-block rounded-full bg-primary-container px-3 py-1 text-[11px] font-semibold text-on-primary-container"
					>
						{typeLabel(data.event.type)}
					</span>
					{#if data.event.externalStatus === 'postponed'}
						<span
							class="inline-block rounded-full bg-[#FFF3CD] px-3 py-1 text-[11px] font-semibold text-[#7A5B00]"
						>
							{data.m.swissvolley.postponed}
						</span>
					{/if}
					{#if data.event.externalSource === 'swissvolley'}
						<span
							class="inline-block rounded-full bg-surface-container-high px-3 py-1 text-[11px] font-semibold text-on-surface-variant"
						>
							{data.m.swissvolley.sourceLabel}
						</span>
					{/if}
				</div>
				<div class="flex items-center gap-3">
					<h1 class="font-display text-[26px] font-extrabold text-on-surface">{data.event.title}</h1>
					{#if data.event.status === 'cancelled'}
						<span class="rounded-full bg-error-container px-3 py-1 text-[11px] font-bold text-error">
							{data.m.events.cancelledBadge}
						</span>
					{/if}
				</div>
			</div>
			{#if data.canManage}
				<div class="flex shrink-0 flex-wrap gap-2">
					<a
						href="/app/events/{data.event.id}/edit"
						class="flex items-center gap-1 rounded-full bg-surface-container-high px-4 py-2 text-[13px] font-medium text-on-surface hover:opacity-90"
					>
						<Pencil size={15} /> {data.m.common.edit}
					</a>
					<form method="POST" action="?/duplicate" use:enhance>
						<button
							type="submit"
							class="flex items-center gap-1 rounded-full bg-surface-container-high px-4 py-2 text-[13px] font-medium text-on-surface hover:opacity-90"
						>
							<Copy size={15} /> {data.m.events.duplicate}
						</button>
					</form>
					{#if data.event.status === 'cancelled'}
						<form method="POST" action="?/uncancel" use:enhance>
							<button
								type="submit"
								class="flex items-center gap-1 rounded-full bg-surface-container-high px-4 py-2 text-[13px] font-medium text-on-surface hover:opacity-90"
							>
								<RotateCcw size={15} /> {data.m.events.uncancelEvent}
							</button>
						</form>
					{:else}
						<form method="POST" action="?/cancel" use:enhance>
							<button
								type="submit"
								class="flex items-center gap-1 rounded-full bg-error-container px-4 py-2 text-[13px] font-medium text-error hover:opacity-90"
							>
								<Ban size={15} /> {data.m.events.cancelEvent}
							</button>
						</form>
					{/if}
					{#if data.event.checkInStatus === 'awaiting_checkin'}
						<form method="POST" action="?/finalize" use:enhance={() => { dismissedFinalize = false; return async ({ update }) => { await update(); }; }}>
							<button
								type="submit"
								class="flex items-center gap-1 rounded-full bg-primary px-4 py-2 text-[13px] font-medium text-on-primary hover:opacity-90"
							>
								<ClipboardCheck size={15} /> CheckIn abschliessen
							</button>
						</form>
					{/if}
					{#if data.event.checkInStatus === 'done'}
						<form method="POST" action="?/reopen" use:enhance>
							<button
								type="submit"
								class="flex items-center gap-1 rounded-full bg-surface-container-high px-4 py-2 text-[13px] font-medium text-on-surface hover:opacity-90"
							>
								<RotateCcw size={15} /> CheckIn wieder öffnen
							</button>
						</form>
					{/if}
				</div>
			{/if}
		</div>

		<div class="mt-4 flex flex-col gap-2 text-[14px] text-on-surface">
			<p class="flex items-center gap-2">
				<Clock size={16} class="text-on-surface-variant" />
				{fmtDateTime(data.event.startAt)}
			</p>
			{#if data.event.location}
				<p class="flex items-center gap-2">
					<MapPin size={16} class="text-on-surface-variant" />
					{data.event.location}
				</p>
			{/if}
			{#if data.event.externalSource === 'nds' && data.event.presentCount > 0}
				<p class="flex items-center gap-2 text-on-surface-variant">
					<ClipboardCheck size={16} /> {data.event.presentCount} dokumentierte Anwesenheiten
				</p>
			{/if}
		</div>

		{#if data.event.description}
			<p class="mt-4 whitespace-pre-line text-[14px] text-on-surface-variant">
				{data.event.description}
			</p>
		{/if}
	</header>

	<!-- RSVP -->
	<section class="rounded-[28px] bg-surface p-6">
		<h2 class="mb-3 text-[15px] font-bold text-on-surface">{data.m.rsvp.yourResponse}</h2>
		{#if rsvpLocked}
			<p class="mb-3 text-[13px] text-on-surface-variant">Zeit zum An-/Abmelden abgelaufen.</p>
			<div class="flex gap-2">
				{#each rsvpButtons as btn (btn.value)}
					<button
						type="button"
						disabled
						class="flex-1 cursor-not-allowed rounded-full px-4 py-3 text-[14px] font-semibold opacity-40 {chosen ===
						btn.value
							? btn.on
							: 'bg-surface-container-high text-on-surface-variant'}"
					>
						{btn.label}
					</button>
				{/each}
			</div>
			{#if form?.error}
				<p class="mt-2 text-[12px] font-medium text-error">{form.error}</p>
			{/if}
		{:else}
			<form method="POST" action="?/rsvp" use:enhance class="flex flex-col gap-3">
				<div class="flex gap-2">
					{#each rsvpButtons as btn (btn.value)}
						<button
							type="submit"
							name="status"
							value={btn.value}
							onclick={() => (chosen = btn.value)}
							class="flex-1 rounded-full px-4 py-3 text-[14px] font-semibold transition-colors {chosen ===
							btn.value
								? btn.on
								: 'bg-surface-container-high text-on-surface-variant hover:bg-surface-container-low'}"
						>
							{btn.label}
						</button>
					{/each}
				</div>
				{#if chosen === 'unsure'}
					<input
						name="reason"
						placeholder={data.m.rsvp.reason}
						value={data.myResponse?.reason ?? ''}
						class="rounded-2xl bg-surface-container-high px-4 py-3 text-[14px] text-on-surface outline-none"
					/>
				{/if}
				{#if form?.error}
					<p class="text-[12px] font-medium text-error">{form.error}</p>
				{:else if form?.saved}
					<p class="text-[12px] font-medium text-success">{data.m.rsvp.saved}</p>
				{/if}
			</form>
		{/if}
	</section>

	<!-- Responses -->
	<section class="rounded-[28px] bg-surface p-6">
		<div class="mb-4 flex items-center justify-between">
			<h2 class="text-[15px] font-bold text-on-surface">{data.m.rsvp.responses}</h2>
			<div class="flex gap-2">
				<span class="rounded-full bg-success-container px-3 py-1 text-[12px] font-semibold text-success">
					{counts.confirmed} {data.m.rsvp.confirmed}
				</span>
				<span class="rounded-full bg-[#FFF3CD] px-3 py-1 text-[12px] font-semibold text-[#7A5B00]">
					{counts.unsure} {data.m.rsvp.unsure}
				</span>
				<span class="rounded-full bg-error-container px-3 py-1 text-[12px] font-semibold text-error">
					{counts.declined} {data.m.rsvp.declined}
				</span>
			</div>
		</div>

		{#if data.responses.length === 0}
			<p class="text-[13px] text-on-surface-variant">—</p>
		{:else}
			<div class="flex flex-col gap-1">
				{#each data.responses as r (r.userId)}
					<div class="flex items-center justify-between py-1.5">
						<div class="flex items-center gap-2">
							<span class="text-[14px] text-on-surface">{r.displayName}</span>
							{#if data.canManage && r.unexcused && (r.status === 'declined' || r.status === 'declined-auto')}
								<span class="rounded-full bg-error-container px-2 py-0.5 text-[10px] font-semibold text-error">
									Nicht entschuldigt
								</span>
							{/if}
						</div>
						<div class="flex items-center gap-2">
							<StatusChip status={r.status as never} m={data.m.rsvp} size="sm" />
							{#if data.canManage && data.event.checkInStatus !== 'done'}
								<button
									type="button"
									onclick={() => openEdit(r)}
									class="rounded-full bg-surface-container-high px-3 py-1 text-[12px] font-medium text-on-surface hover:opacity-90"
								>
									<Pencil size={12} />
								</button>
							{/if}
						</div>
					</div>
				{/each}
			</div>
		{/if}

		{#if form?.memberSaved}
			<p class="mt-3 text-[12px] font-medium text-success">Gespeichert.</p>
		{/if}
		{#if form?.manageError}
			<p class="mt-3 text-[12px] font-medium text-error">{form.manageError}</p>
		{/if}
	</section>
</div>

<!-- Coach edit popup -->
{#if editTarget}
	<div
		class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
		role="presentation"
		onclick={(e) => {
			if (e.target === e.currentTarget) closeEdit();
		}}
	>
		<div
			class="flex w-full max-w-[400px] flex-col rounded-[28px] bg-surface-container-low p-6"
			role="dialog"
			aria-modal="true"
			aria-label="Status bearbeiten"
		>
			<div class="mb-4 flex items-start justify-between gap-4">
				<h2 class="text-[18px] font-bold text-on-surface">{editTarget.displayName}</h2>
				<button
					type="button"
					onclick={closeEdit}
					aria-label="Schliessen"
					class="cursor-pointer rounded-full border-none bg-transparent p-2 text-on-surface-variant hover:bg-surface-container-high"
				>
					<X size={18} />
				</button>
			</div>

			<form
				method="POST"
				action="?/setMemberStatus"
				use:enhance={() => {
					return async ({ result, update }) => {
						closeEdit();
						await update();
					};
				}}
				class="flex flex-col gap-4"
			>
				<input type="hidden" name="userId" value={editTarget.userId} />

				<div class="flex gap-2">
					<button
						type="button"
						onclick={() => { editStatus = 'confirmed'; editUnexcused = false; }}
						class="flex-1 rounded-full px-4 py-3 text-[14px] font-semibold transition-colors {editStatus === 'confirmed'
							? 'bg-success-container text-success'
							: 'bg-surface-container-high text-on-surface-variant hover:opacity-90'}"
					>
						Anwesend
					</button>
					<button
						type="button"
						onclick={() => (editStatus = 'declined')}
						class="flex-1 rounded-full px-4 py-3 text-[14px] font-semibold transition-colors {editStatus === 'declined'
							? 'bg-error-container text-error'
							: 'bg-surface-container-high text-on-surface-variant hover:opacity-90'}"
					>
						Abgemeldet
					</button>
				</div>

				{#if editStatus === 'declined'}
					<label class="flex cursor-pointer items-center gap-2 text-[14px] text-on-surface">
						<input
							type="checkbox"
							bind:checked={editUnexcused}
							class="size-4 accent-error"
						/>
						Nicht entschuldigt
					</label>
				{/if}

				<input type="hidden" name="status" value={editStatus} />
				<input type="hidden" name="unexcused" value={String(editUnexcused)} />

				<div class="flex justify-end gap-3">
					<button
						type="button"
						onclick={closeEdit}
						class="cursor-pointer rounded-full border border-outline-variant bg-transparent px-6 py-3 text-[14px] font-medium text-on-surface-variant hover:bg-surface-container-high"
					>
						Abbrechen
					</button>
					<button
						type="submit"
						class="cursor-pointer rounded-full border-none bg-primary px-6 py-3 text-[14px] font-bold text-on-primary hover:opacity-90"
					>
						Speichern
					</button>
				</div>
			</form>
		</div>
	</div>
{/if}

<!-- Finalize blocked dialog -->
{#if showFinalizeBlocked && form?.finalizeBlocked}
	<div
		class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
		role="presentation"
	>
		<div
			class="flex w-full max-w-[440px] flex-col rounded-[28px] bg-surface-container-low p-6"
			role="dialog"
			aria-modal="true"
			aria-label="CheckIn kann nicht abgeschlossen werden"
		>
			<div class="mb-4 flex items-start justify-between gap-4">
				<h2 class="text-[18px] font-bold text-on-surface">CheckIn nicht möglich</h2>
			</div>

			<p class="text-[14px] text-on-surface-variant">
				{#if form.finalizeBlocked.reason === 'unsure'}
					Unsichere Spieler müssen zuerst als anwesend oder abwesend markiert werden.
				{:else}
					Folgende Mitglieder haben keine Rückmeldung abgegeben und müssen zuerst manuell eingetragen werden.
				{/if}
			</p>

			{#if blockedNames.length > 0}
				<ul class="mt-3 flex flex-col gap-1">
					{#each blockedNames as name (name)}
						<li class="text-[13px] font-medium text-on-surface">{name}</li>
					{/each}
				</ul>
			{/if}

			<div class="mt-5 flex justify-end">
				<button
					type="button"
					onclick={() => (dismissedFinalize = true)}
					class="cursor-pointer rounded-full border-none bg-primary px-6 py-3 text-[14px] font-bold text-on-primary hover:opacity-90"
				>
					OK
				</button>
			</div>
		</div>
	</div>
{/if}
