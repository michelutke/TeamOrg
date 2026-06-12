import type { Config } from 'tailwindcss';

export default {
	content: ['./src/**/*.{html,js,svelte,ts}'],
	theme: {
		extend: {
			colors: {
				background: '#090912',
				surface: '#13131F',
				card: '#1C1C2E',
				primary: '#4F8EF7',
				accent: '#F97316',
				destructive: '#EF4444',
				success: '#22C55E',
				warning: '#FACC15',
				foreground: '#F0F0FF',
				'muted-foreground': '#9090B0',
				border: '#2A2A40'
			},
			fontFamily: {
				sans: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif']
			}
		}
	}
} satisfies Config;
