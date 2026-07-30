import type { Config } from 'tailwindcss';

// TeamOrg landing palette — Graphite Cyan brand (light scheme), cool neutrals.
// Mirrors composeApp ui/theme/Color.kt.
export default {
	content: ['./src/**/*.{html,js,svelte,ts}'],
	theme: {
		extend: {
			colors: {
				primary: 'var(--color-accent)', // legacy alias, keeps old classes working
				'on-primary': 'var(--color-on-accent)',
				'primary-container': '#BFEAF4', // pastel chips identical in both schemes
				'on-primary-container': '#001F26',
				accent: 'var(--color-accent)',
				'on-accent': 'var(--color-on-accent)',
				surface: 'var(--color-surface)',
				'surface-low': 'var(--color-surface-low)',
				panel: 'var(--color-panel)',
				'on-surface': 'var(--color-on-surface)',
				'on-surface-variant': 'var(--color-body)',
				body: 'var(--color-body)',
				placeholder: 'var(--color-placeholder)',
				outline: 'var(--color-outline)',
				'outline-variant': 'var(--color-outline-variant)',
				'accent-green': '#C8EBD5',
				'accent-green-on': '#0D4020',
				'accent-yellow': '#F7E7B7',
				'accent-yellow-on': '#7A5C00',
				'accent-red': '#F9D7D3',
				'accent-red-on': '#701C1A',
				'accent-blue': '#BFEAF4',
				'accent-blue-on': '#00363F',
				'accent-lavender': '#C7D2FE',
				'accent-lavender-on': '#243178',
				'accent-purple': '#D6C4F4',
				'accent-purple-on': '#4A2C6E'
			},
			fontFamily: {
				sans: ['Roboto Flex', 'Roboto', 'ui-sans-serif', 'system-ui', 'sans-serif'],
				display: [
					'Google Sans Flex',
					'Roboto Flex',
					'Roboto',
					'ui-sans-serif',
					'system-ui',
					'sans-serif'
				]
			},
			maxWidth: {
				content: '1280px'
			}
		}
	}
} satisfies Config;
