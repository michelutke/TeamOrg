<script lang="ts">
	import { enhance } from '$app/forms';
	import type { ActionData } from './$types';

	interface Props {
		form: ActionData;
	}

	let { form }: Props = $props();

	function onFocus(e: Event) {
		const el = e.currentTarget as HTMLInputElement;
		el.style.borderColor = '#4F8EF7';
		el.style.boxShadow = '0 0 0 2px rgba(79,142,247,0.3)';
	}

	function onBlur(e: Event) {
		const el = e.currentTarget as HTMLInputElement;
		el.style.borderColor = '#2A2A40';
		el.style.boxShadow = 'none';
	}
</script>

<svelte:head>
	<title>Admin Login — TeamOrg</title>
</svelte:head>

<div class="min-h-screen flex items-center justify-center" style="background-color: #090912;">
	<div
		class="w-full max-w-sm"
		style="background-color: #1C1C2E; padding: 24px; border-radius: 8px; border: 1px solid #2A2A40; max-width: 384px; width: 100%;"
	>
		<h1 class="text-center font-semibold mb-6" style="font-size: 20px; color: #F0F0FF;">
			TeamOrg Admin
		</h1>

		<form method="POST" use:enhance>
			<div class="mb-4">
				<label for="email" class="block mb-1 font-semibold" style="font-size: 12px; color: #F0F0FF;">
					Email
				</label>
				<input
					id="email"
					name="email"
					type="email"
					autocomplete="email"
					required
					value={form?.email ?? ''}
					onfocus={onFocus}
					onblur={onBlur}
					style="
						background-color: #1C1C2E;
						border: 1px solid #2A2A40;
						color: #F0F0FF;
						font-size: 14px;
						height: 40px;
						padding: 0 16px;
						border-radius: 6px;
						outline: none;
						width: 100%;
						box-sizing: border-box;
					"
				/>
			</div>

			<div class="mb-6">
				<label for="password" class="block mb-1 font-semibold" style="font-size: 12px; color: #F0F0FF;">
					Password
				</label>
				<input
					id="password"
					name="password"
					type="password"
					autocomplete="current-password"
					required
					onfocus={onFocus}
					onblur={onBlur}
					style="
						background-color: #1C1C2E;
						border: 1px solid #2A2A40;
						color: #F0F0FF;
						font-size: 14px;
						height: 40px;
						padding: 0 16px;
						border-radius: 6px;
						outline: none;
						width: 100%;
						box-sizing: border-box;
					"
				/>
			</div>

			{#if form?.error}
				<p class="mb-4 text-center" style="font-size: 12px; color: #EF4444;">
					{form.error}
				</p>
			{/if}

			<button
				type="submit"
				class="w-full font-semibold"
				style="
					background-color: #4F8EF7;
					color: #FFFFFF;
					font-size: 14px;
					height: 40px;
					border: none;
					border-radius: 6px;
					cursor: pointer;
					padding: 0 16px;
					width: 100%;
				"
			>
				Sign In
			</button>
		</form>
	</div>
</div>
