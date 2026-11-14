-- =====================================================================
-- V13: a new user sets their own password on their first sign-in.
-- The Super Admin gives them a starting password; until they change it, the API only lets them
-- see who they are, change the password or sign out. Existing users are not affected.
-- =====================================================================

ALTER TABLE app_users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
