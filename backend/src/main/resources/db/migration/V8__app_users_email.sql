-- =====================================================================
-- V8: optional email on app users.
-- Additive: one nullable column, so existing users just have no email.
-- Stored trimmed and lower case; unique when set.
-- =====================================================================

ALTER TABLE app_users
    ADD COLUMN email VARCHAR(254),
    ADD CONSTRAINT ck_app_users_email CHECK (
        email IS NULL OR (email = lower(btrim(email)) AND email LIKE '_%@_%._%'));

CREATE UNIQUE INDEX uq_app_users_email ON app_users (email) WHERE email IS NOT NULL;
