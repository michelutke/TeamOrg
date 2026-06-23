import type { Locale } from '$lib/i18n';

declare global {
	namespace App {
		interface Locals {
			lang: Locale;
		}
		// interface Error {}
		// interface PageData {}
		// interface PageState {}
		// interface Platform {}
	}

	interface Window {
		turnstile?: { reset: (widgetId?: string) => void };
	}
}

export {};
