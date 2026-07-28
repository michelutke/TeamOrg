import { apiGet, apiPost, apiGetPublic } from './api';

export type SelfServeCreate = {
	kind: 'club' | 'team';
	name: string;
	sportType?: string;
	location?: string;
	billingEmail: string;
};
export type SelfServeCreated = { clubId: string; teamId?: string | null; setupIntentClientSecret: string };
export type BillingInfo = {
	billingEmail: string;
	cardBrand: string | null;
	cardLast4: string | null;
	cardExpMonth: number | null;
	cardExpYear: number | null;
	currentMemberCount: number;
	projectedBilledCount: number;
	billingStatus: 'active' | 'past_due' | 'frozen';
	billingMode: 'stripe' | 'manual' | 'free';
	kind: 'club' | 'team';
};

export const createSelfServe = (token: string, body: SelfServeCreate) =>
	apiPost<SelfServeCreated>('/clubs/self-serve', token, body);
export const confirmBilling = (token: string, clubId: string, setupIntentId: string) =>
	apiPost<{ status: string }>(`/clubs/${clubId}/billing/confirm`, token, { setupIntentId });
export const getBilling = (token: string, clubId: string) =>
	apiGet<BillingInfo>(`/clubs/${clubId}/billing`, token);
export const updateCard = (token: string, clubId: string) =>
	apiPost<{ setupIntentClientSecret: string }>(`/clubs/${clubId}/billing/update-card`, token);
export const convertClub = (token: string, clubId: string, targetKind: 'club' | 'team') =>
	apiPost<{ kind: string }>(`/clubs/${clubId}/convert`, token, { targetKind });
export const lookupInviteCode = (shortCode: string) =>
	apiGetPublic<{ token: string }>(`/invites/code/${encodeURIComponent(shortCode)}`);
