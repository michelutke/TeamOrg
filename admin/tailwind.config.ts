import type { Config } from 'tailwindcss';

// M3 Expressive palette (Figma redesign iKcGJfgxUxMi2AnE9o4BAL)
export default {
	content: ['./src/**/*.{html,js,svelte,ts}'],
	theme: {
		extend: {
			colors: {
				primary: '#6750A4',
				'on-primary': '#FFFFFF',
				'primary-container': '#EADDFF',
				'on-primary-container': '#4F378B',
				'secondary-container': '#E8DEF8',
				'on-secondary-container': '#1D192B',
				tertiary: '#7D5260',
				'on-tertiary': '#FFFFFF',
				'tertiary-container': '#FFD8E4',
				'on-tertiary-container': '#31111D',
				surface: '#FEF7FF',
				'surface-container-low': '#F7F2FA',
				'surface-container-high': '#ECE6F0',
				'on-surface': '#1D1B20',
				'on-surface-variant': '#49454F',
				'outline-variant': '#E6E0E9',
				error: '#B3261E',
				'on-error': '#FFFFFF',
				'error-container': '#F9DEDC',
				success: '#2E7D32',
				'success-container': '#C8E6C9'
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
