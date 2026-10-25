-- =====================================================================
-- V6: commission arrangements can be voided (with a reason), confirmed ones are frozen, and the
-- same recipient can't be entered twice in the same role on one project.
--
-- Additive: new nullable columns, a widened status check, an index and a trigger. No existing row
-- changes. It stops (applying nothing) if a project already has two live arrangements for the same
-- recipient and role.
-- =====================================================================

ALTER TABLE commission_arrangements DROP CONSTRAINT ck_commission_arrangements_status;

ALTER TABLE commission_arrangements
    ADD COLUMN voided_at   TIMESTAMPTZ,
    ADD COLUMN void_reason TEXT;

ALTER TABLE commission_arrangements
    ADD CONSTRAINT ck_commission_arrangements_status CHECK (status IN ('PENDING', 'CONFIRMED', 'VOIDED')),
    -- voided exactly when the void columns are set, and both are set together
    ADD CONSTRAINT ck_commission_arrangements_void CHECK (
        (status = 'VOIDED') = (voided_at IS NOT NULL) AND (voided_at IS NULL) = (void_reason IS NULL)),
    -- only a confirmed arrangement can be voided, so a voided one keeps its confirmed amount
    ADD CONSTRAINT ck_commission_arrangements_voided_confirmed CHECK (COALESCE(
        status <> 'VOIDED' OR (confirmed_on IS NOT NULL AND commission_amount >= 0), FALSE));

-- One live arrangement per recipient and role on a project. A voided one doesn't count, so a
-- correction is "void, then add again".
CREATE UNIQUE INDEX uq_commission_arrangements_recipient_role
    ON commission_arrangements (project_id, lower(recipient_name), lower(role))
    WHERE status <> 'VOIDED';

CREATE INDEX ix_commission_arrangements_status ON commission_arrangements (status);

-- Confirmed commission history is kept: a CONFIRMED row may only become VOIDED (nothing else about
-- it changes), a VOIDED row never changes, and only PENDING drafts can be deleted.
CREATE FUNCTION sk_guard_commission_arrangement() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'PENDING' THEN
            RAISE EXCEPTION 'confirmed commission arrangements cannot be deleted; void them instead'
                USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.status = 'VOIDED'
       OR (OLD.status = 'CONFIRMED' AND (
            NEW.status NOT IN ('CONFIRMED', 'VOIDED')
            OR (to_jsonb(NEW) - ARRAY['status', 'voided_at', 'void_reason', 'updated_at', 'version'])
               <> (to_jsonb(OLD) - ARRAY['status', 'voided_at', 'void_reason', 'updated_at', 'version']))) THEN
        RAISE EXCEPTION 'confirmed commission arrangements cannot be changed except to void them'
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_commission_arrangements_guard BEFORE UPDATE OR DELETE ON commission_arrangements
    FOR EACH ROW EXECUTE FUNCTION sk_guard_commission_arrangement();
