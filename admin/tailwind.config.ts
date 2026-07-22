import type { Config } from 'tailwindcss';

// Graphite Cyan brand palette (light scheme) — mirrors composeApp ui/theme/Color.kt
export default {
	content: ['./src/**/*.{html,js,svelte,ts}'],
	theme: {
		extend: {
			colors: {
				primary: '#0E6577',
				'on-primary': '#FFFFFF',
				'primary-container': '#BFEAF4',
				'on-primary-container': '#001F26',
				'secondary-container': '#CDE7EC',
				'on-secondary-container': '#051F24',
				tertiary: '#545D92',
				'on-tertiary': '#FFFFFF',
				'tertiary-container': '#DCE1FF',
				'on-tertiary-container': '#101A4B',
				surface: '#F7F9FA',
				'surface-container-low': '#F1F4F6',
				'surface-container-high': '#E5E9EB',
				'on-surface': '#181C1F',
				'on-surface-variant': '#40484C',
				'outline-variant': '#C3CBD1',
				error: '#BA1A1A',
				'on-error': '#FFFFFF',
				'error-container': '#FFDAD6',
				success: '#1F6B37',
				'success-container': '#D7F0DC'
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
			}
		}
	}
} satisfies Config;
