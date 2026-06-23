import type { Config } from 'tailwindcss';

// TeamOrg landing palette — amber/gold primary, green/red/yellow pastel highlights,
// warm neutrals. Mirrors the Figma landing design (file iKcGJfgxUxMi2AnE9o4BAL).
export default {
	content: ['./src/**/*.{html,js,svelte,ts}'],
	theme: {
		extend: {
			colors: {
				// Brand (amber/gold)
				primary: '#C9760E',
				'on-primary': '#FFFFFF',
				'primary-container': '#FBE6C0',
				'on-primary-container': '#6E3D03',
				// Warm neutrals
				surface: '#FBFAF7',
				'surface-low': '#F4F1EA',
				'surface-container': '#EEEAE1',
				'surface-high': '#E8E3D9',
				'on-surface': '#1F1B16',
				'on-surface-variant': '#4B463E',
				outline: '#7C766B',
				'outline-variant': '#D2CCC0',
				'inverse-surface': '#322D26',
				// Highlight accents (the app's going / unsure / declined colors, pastel)
				'accent-green': '#C8EBD5',
				'accent-green-on': '#145638',
				'accent-yellow': '#FBEFBF',
				'accent-yellow-on': '#6B5212',
				'accent-red': '#F9D7D3',
				'accent-red-on': '#76271F'
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
