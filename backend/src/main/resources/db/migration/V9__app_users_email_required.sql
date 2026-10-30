-- =====================================================================
-- V9: every user must have an email (it's now a sign-in name next to the username).
-- Stops with a NOT NULL error, changing nothing, if any user still has no email -
-- give those users one first.
-- =====================================================================

ALTER TABLE app_users ALTER COLUMN email SET NOT NULL;

-- the V8 index only covered rows with an email; now that's all of them
DROP INDEX uq_app_users_email;
CREATE UNIQUE INDEX uq_app_users_email ON app_users (email);
