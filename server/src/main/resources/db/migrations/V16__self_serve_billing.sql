ALTER TABLE clubs ADD COLUMN kind TEXT NOT NULL DEFAULT 'club';           -- club | team
ALTER TABLE clubs ADD COLUMN owner_user_id UUID NULL REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE clubs ADD COLUMN billing_mode TEXT NOT NULL DEFAULT 'manual'; -- stripe | manual | free
ALTER TABLE clubs ADD COLUMN billing_status TEXT NOT NULL DEFAULT 'active'; -- active | past_due | frozen
-- clubs.status additionally allows 'pending' (self-serve club before card setup succeeds)
ALTER TABLE clubs DROP CONSTRAINT clubs_status_check;
ALTER TABLE clubs ADD CONSTRAINT clubs_status_check CHECK (status IN ('active', 'deactivated', 'deleted', 'pending'));

CREATE TABLE club_billing (
    club_id UUID PRIMARY KEY REFERENCES clubs(id) ON DELETE CASCADE,
    stripe_customer_id TEXT NOT NULL UNIQUE,
    stripe_subscription_id TEXT NULL,
    setup_intent_id TEXT NULL,
    billing_email TEXT NOT NULL,
    card_brand TEXT NULL,
    card_last4 TEXT NULL,
    card_exp_month INT NULL,
    card_exp_year INT NULL,
    next_sample_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE member_count_samples (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id UUID NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    sampled_at TIMESTAMP NOT NULL,
    member_count INT NOT NULL
);
CREATE INDEX idx_member_count_samples_club_time ON member_count_samples(club_id, sampled_at);

CREATE TABLE billing_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stripe_event_id TEXT NOT NULL UNIQUE,
    club_id UUID NULL REFERENCES clubs(id) ON DELETE SET NULL,
    type TEXT NOT NULL,
    payload TEXT NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT now()
);
