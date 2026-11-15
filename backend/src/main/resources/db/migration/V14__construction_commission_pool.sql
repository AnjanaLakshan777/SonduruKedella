-- =====================================================================
-- V14: construction commission works like design (owner decision 2026-11-15).
-- Both have a commission pool (20% by default; the company keeps the rest). Going over the pool
-- is a warning that can be confirmed as an exception, exactly as before for design.
-- The optional construction "warning threshold" becomes a required pool; an unset one becomes 20.
-- =====================================================================

UPDATE commission_policy SET construction_warning_pct = 20 WHERE construction_warning_pct IS NULL;

ALTER TABLE commission_policy RENAME COLUMN construction_warning_pct TO construction_commission_pool_pct;
ALTER TABLE commission_policy ALTER COLUMN construction_commission_pool_pct SET DEFAULT 20;
ALTER TABLE commission_policy ALTER COLUMN construction_commission_pool_pct SET NOT NULL;

ALTER TABLE commission_policy DROP CONSTRAINT ck_commission_policy_construction;
ALTER TABLE commission_policy ADD CONSTRAINT ck_commission_policy_construction_pool
    CHECK (construction_commission_pool_pct > 0 AND construction_commission_pool_pct < 100);
