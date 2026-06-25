<script lang="ts">
	import { enhance } from '$app/forms';
	import type { ActionData } from './$types';

	interface Props {
		form: ActionData;
	}

	let { form }: Props = $props();
</script>

<svelte:head>
	<title>Anmelden — TeamOrg</title>
</svelte:head>

<div class="flex min-h-screen items-center justify-center bg-surface-container-low">
	<div
		class="flex w-full max-w-[440px] flex-col items-center gap-4 rounded-[32px] bg-surface px-10 py-12 shadow-[0px_8px_32px_0px_rgba(0,0,0,0.08)]"
	>
		<div class="flex size-16 items-center justify-center rounded-full bg-primary-container">
			<span class="text-[22px] font-bold text-on-primary-container">TO</span>
		</div>
		<h1 class="font-display text-[26px] font-extrabold text-on-surface">TeamOrg</h1>
		<p class="text-[13px] text-on-surface-variant">Melde dich mit deinem Konto an</p>

		<form method="POST" use:enhance class="flex w-full flex-col gap-4">
			<label class="flex w-full flex-col gap-1 rounded-2xl bg-surface-container-high px-[18px] py-[10px]">
				<span class="text-[11px] font-medium text-primary">E-Mail</span>
				<input
					id="email"
					name="email"
					type="email"
					autocomplete="email"
					required
					value={form?.email ?? ''}
					class="w-full border-none bg-transparent text-[16px] text-on-surface outline-none"
				/>
			</label>

			<label class="flex w-full flex-col gap-1 rounded-2xl bg-surface-container-high px-[18px] py-[10px]">
				<span class="text-[11px] font-medium text-primary">Passwort</span>
				<input
					id="password"
					name="password"
					type="password"
					autocomplete="current-password"
					required
					class="w-full border-none bg-transparent text-[16px] text-on-surface outline-none"
				/>
			</label>

			{#if form?.error}
				<p class="text-center text-[12px] font-medium text-error">{form.error}</p>
			{/if}

			<button
				type="submit"
				class="w-full cursor-pointer rounded-full border-none bg-primary py-4 text-[15px] font-bold text-on-primary hover:opacity-90"
			>
				Anmelden
			</button>
		</form>
	</div>
</div>
