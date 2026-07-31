-- The Angebot number is unique per club, not globally: two clubs can independently import the
-- same NDS Angebot (each linking it to their own team). The V14 index was global, so a second
-- club's import 500'd and left its team orphaned (never linked).
DROP INDEX uq_teams_nds_angebot;
CREATE UNIQUE INDEX uq_teams_club_nds_angebot ON teams(club_id, nds_angebot_id) WHERE nds_angebot_id IS NOT NULL;
