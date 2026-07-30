<script lang="ts">
	import {
		rowKey,
		type MappingChoice,
		type MemberSuggestionDto,
		type NdsMemberInput
	} from '$lib/nds-import-wizard';

	interface TeamMemberOption {
		userId: string;
		displayName: string;
	}

	interface Props {
		persons: NdsMemberInput[];
		suggestions: MemberSuggestionDto[];
		teamMembers: TeamMemberOption[];
		mappings: Map<string, MappingChoice>;
	}

	let { persons, suggestions, teamMembers, mappings = $bindable() }: Props = $props();

	function suggestionFor(p: NdsMemberInput) {
		const key = rowKey(p.funktion, p.lastName, p.firstName);
		return suggestions.find((s) => s.rowKey === key);
	}

	function otherTeamMembers(suggestion: MemberSuggestionDto | undefined) {
		const suggested = new Set(suggestion?.candidates.map((c) => c.userId) ?? []);
		return teamMembers.filter((m) => !suggested.has(m.userId));
	}

	function selectValue(key: string): string {
		const choice = mappings.get(key);
		if (!choice) return 'create';
		if (choice.action === 'map') return `map:${choice.userId}`;
		return choice.action;
	}

	function setMapping(key: string, value: string) {
		const next = new Map(mappings);
		if (value === 'create' || value === 'skip') {
			next.set(key, { action: value });
		} else if (value.startsWith('map:')) {
			next.set(key, { action: 'map', userId: value.slice(4) });
		}
		mappings = next;
	}
</script>

<div class="min-h-0 flex-1 overflow-y-auto pr-1">
	<p class="mb-3 text-[13px] text-on-surface-variant">
		Ordne jede Person einem bestehenden Teammitglied zu, erstelle einen neuen Nutzer, oder
		überspringe die Zeile.
	</p>
	<div class="overflow-x-auto rounded-2xl bg-surface-container-high">
		<table class="w-full text-left text-[13px]">
			<thead>
				<tr class="text-[11px] uppercase tracking-wide text-on-surface-variant">
					<th class="px-3 py-2 font-medium">Funktion</th>
					<th class="px-3 py-2 font-medium">Name</th>
					<th class="px-3 py-2 font-medium">Geburtsdatum</th>
					<th class="px-3 py-2 font-medium">Personennummer</th>
					<th class="px-3 py-2 font-medium">Zuordnung</th>
				</tr>
			</thead>
			<tbody>
				{#each persons as p (rowKey(p.funktion, p.lastName, p.firstName))}
					{@const key = rowKey(p.funktion, p.lastName, p.firstName)}
					{@const suggestion = suggestionFor(p)}
					<tr class="border-t border-outline-variant/30">
						<td class="px-3 py-2 text-on-surface-variant">{p.funktion}</td>
						<td class="px-3 py-2 text-on-surface">{p.firstName} {p.lastName}</td>
						<td class="px-3 py-2 text-on-surface-variant">{p.birthDate ?? '–'}</td>
						<td class="px-3 py-2 text-on-surface-variant">{p.personNumber ?? '–'}</td>
						<td class="px-3 py-2">
							{#if suggestion?.alreadyLinkedUserId}
								<span class="text-[12px] font-medium text-on-surface-variant">bereits verknüpft</span>
							{:else}
								<select
									value={selectValue(key)}
									onchange={(e) => setMapping(key, (e.currentTarget as HTMLSelectElement).value)}
									class="w-full rounded-xl border-none bg-surface-container px-2 py-1.5 text-[13px] text-on-surface outline-none focus:ring-2 focus:ring-primary"
								>
									{#if suggestion && suggestion.candidates.length > 0}
										<optgroup label="Vorschlag">
											{#each suggestion.candidates as c (c.userId)}
												<option value={`map:${c.userId}`}>{c.displayName}</option>
											{/each}
										</optgroup>
									{/if}
									{#if otherTeamMembers(suggestion).length > 0}
										<optgroup label="Teammitglieder">
											{#each otherTeamMembers(suggestion) as m (m.userId)}
												<option value={`map:${m.userId}`}>{m.displayName}</option>
											{/each}
										</optgroup>
									{/if}
									<option value="create">Neuen Nutzer erstellen</option>
									<option value="skip">Überspringen</option>
								</select>
							{/if}
						</td>
					</tr>
				{/each}
			</tbody>
		</table>
	</div>
</div>
