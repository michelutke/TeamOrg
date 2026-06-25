-- Per-team visual identity (M3 Expressive shape + color), set by coaches/managers.
-- Nullable: teams without an explicit appearance fall back to a deterministic
-- default derived client-side from the team id.
ALTER TABLE teams ADD COLUMN appearance_shape TEXT;
ALTER TABLE teams ADD COLUMN appearance_color TEXT;
