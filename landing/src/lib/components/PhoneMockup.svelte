<script lang="ts">
	let {
		src,
		alt,
		width = 280,
		float = false,
		class: klass = ''
	}: { src: string; alt: string; width?: number; float?: boolean; class?: string } = $props();
</script>

<div
	class="relative shrink-0 rounded-[1.9rem] bg-[#0c0c0e] p-[1.8%] shadow-[0_30px_70px_-12px_rgba(40,25,90,0.32)] ring-1 ring-inset ring-white/10 {float
		? 'animate-float'
		: ''} {klass}"
	style="width: {width}px"
>
	<img {src} {alt} loading="lazy" class="block w-full rounded-[1.55rem]" width="412" height="917" />
	<!-- Dynamic Island -->
	<div
		class="absolute left-1/2 top-[2.6%] h-[3%] w-[30%] -translate-x-1/2 rounded-full bg-black"
	></div>
</div>
