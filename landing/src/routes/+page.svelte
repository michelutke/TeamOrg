<script lang="ts">
	import Hero from '$lib/components/Hero.svelte';
	import Features from '$lib/components/Features.svelte';
	import HowItWorks from '$lib/components/HowItWorks.svelte';
	import Pricing from '$lib/components/Pricing.svelte';
	import Contact from '$lib/components/Contact.svelte';
	import type { PageData, ActionData } from './$types';

	let { data, form }: { data: PageData; form: ActionData } = $props();
</script>

<Hero m={data.m.hero} />
<Features m={data.m.features} />
<HowItWorks m={data.m.how} />
<Pricing m={data.m.pricing} />
<Contact m={data.m.contact} siteKey={data.turnstileSiteKey} {form} />
