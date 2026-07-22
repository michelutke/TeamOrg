import type { Config } from 'tailwindcss';

// TeamOrg landing palette — Graphite Cyan brand (light scheme), cool neutrals.
// Mirrors composeApp ui/theme/Color.kt.
export default {
	content: ['./src/**/*.{html,js,svelte,ts}'],
	theme: {
		extend: {
			colors: {
				// Brand (teal/cyan on light)
				primary: '#0E6577',
				'on-primary': '#FFFFFF',
				'primary-container': '#BFEAF4',
				'on-primary-container': '#001F26',
				// Cool neutrals
				surface: '#F7F9FA',
				'surface-low': '#F1F4F6',
				'surface-container': '#EBEEF0',
				'surface-high': '#E5E9EB',
				'on-surface': '#181C1F',
				'on-surface-variant': '#40484C',
				outline: '#70787C',
				'outline-variant': '#C3CBD1',
				'inverse-surface': '#22262E',
				// Highlight accents (the app's going / unsure / declined colors, pastel)
				'accent-green': '#D7F0DC',
				'accent-green-on': '#1F6B37',
				'accent-yellow': '#F8ECC8',
				'accent-yellow-on': '#7A5C00',
				'accent-red': '#FADCD8',
				'accent-red-on': '#A83A30'
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
