-- =====================================================================
-- V12: page permissions belong to each user, not to a shared role.
-- Owner decision 2026-11-03: the role is only a name ("Supervisor") shown under the user's name;
-- the Super Admin ticks each user's pages one by one. Admins get pages too (all of them to start);
-- Super Admins always open everything and have none.
-- V11's custom roles are folded in: each user keeps their role's name and pages, then the
-- shared tables go. Nothing is lost.
-- =====================================================================

CREATE TABLE app_user_pages (
    user_id BIGINT      NOT NULL REFERENCES app_users (id),
    page    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, page),
    -- same names as the AppPage enum
    CONSTRAINT ck_app_user_pages_page CHECK (page IN (
        'DASHBOARD', 'PROJECTS', 'CLIENTS', 'REVENUE', 'EXPENSES', 'COMMISSIONS', 'REPORTS',
        'SUPPLIERS', 'INVENTORY', 'LABOUR', 'SETTINGS'))
);

-- the "Other" role's name; only for role CUSTOM
ALTER TABLE app_users ADD COLUMN role_label VARCHAR(50);

UPDATE app_users u SET role_label = r.name FROM custom_roles r WHERE u.custom_role_id = r.id;

INSERT INTO app_user_pages (user_id, page)
SELECT u.id, p.page FROM app_users u JOIN custom_role_pages p ON p.role_id = u.custom_role_id;

-- Admins keep opening every page
INSERT INTO app_user_pages (user_id, page)
SELECT u.id, p.page
FROM app_users u
CROSS JOIN (VALUES ('DASHBOARD'), ('PROJECTS'), ('CLIENTS'), ('REVENUE'), ('EXPENSES'), ('COMMISSIONS'), ('REPORTS'),
                   ('SUPPLIERS'), ('INVENTORY'), ('LABOUR'), ('SETTINGS')) AS p (page)
WHERE u.role = 'ADMIN';

ALTER TABLE app_users DROP CONSTRAINT ck_app_users_custom_role;
-- drops ix_app_users_custom_role and the FK with it
ALTER TABLE app_users DROP COLUMN custom_role_id;
DROP TABLE custom_role_pages;
DROP TABLE custom_roles;

ALTER TABLE app_users ADD CONSTRAINT ck_app_users_role_label CHECK (
    (role = 'CUSTOM') = (role_label IS NOT NULL)
    AND (role_label IS NULL OR (role_label = btrim(role_label) AND role_label <> '')));

-- ck_security_events_type keeps the CUSTOM_ROLE_* values: V11-era events stay in the trail.
