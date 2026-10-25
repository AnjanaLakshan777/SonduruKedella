-- =====================================================================
-- V7: write-off reversal, idempotent and voidable payroll corrections, supplier invoice cancellation.
-- Additive: new nullable columns (version defaults to 0), checks, triggers and one key swapped for
-- an equivalent partial index. No existing row changes.
-- =====================================================================

-- Write-offs can be reversed (voided with a reason); a reversed write-off stops counting and stays.
ALTER TABLE receivable_write_offs
    ADD COLUMN voided_at   TIMESTAMPTZ,
    ADD COLUMN void_reason TEXT,
    ADD COLUMN version     BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_receivable_write_offs_void CHECK ((voided_at IS NULL) = (void_reason IS NULL));

-- Same rule as payments (V1 sk_guard_payment_change): never deleted, and once recorded only the
-- void columns may change, once.
CREATE TRIGGER trg_receivable_write_offs_guard BEFORE UPDATE OR DELETE ON receivable_write_offs
    FOR EACH ROW EXECUTE FUNCTION sk_guard_payment_change();

-- Payroll corrections: a client request id so a resent correction is recorded once, and a void with a
-- reason instead of editing (a wrong correction is voided and a new one recorded: the audit trail).
ALTER TABLE payroll_corrections
    ADD COLUMN request_id  UUID,
    ADD COLUMN voided_at   TIMESTAMPTZ,
    ADD COLUMN void_reason TEXT,
    ADD COLUMN version     BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT uq_payroll_corrections_request UNIQUE (request_id),
    ADD CONSTRAINT ck_payroll_corrections_void CHECK ((voided_at IS NULL) = (void_reason IS NULL));

CREATE TRIGGER trg_payroll_corrections_guard BEFORE UPDATE OR DELETE ON payroll_corrections
    FOR EACH ROW EXECUTE FUNCTION sk_guard_payment_change();

-- Supplier invoices can be cancelled with a reason (the service allows it only for PROJECT invoices
-- with no active payments). A cancelled invoice no longer blocks its number, so it can be re-entered.
ALTER TABLE supplier_invoices
    ADD COLUMN cancelled_at  TIMESTAMPTZ,
    ADD COLUMN cancel_reason TEXT,
    ADD CONSTRAINT ck_supplier_invoices_cancel CHECK ((cancelled_at IS NULL) = (cancel_reason IS NULL));

ALTER TABLE supplier_invoices DROP CONSTRAINT uq_supplier_invoices_supplier_invoice_no;
CREATE UNIQUE INDEX uq_supplier_invoices_supplier_invoice_no ON supplier_invoices (supplier_id, invoice_no)
    WHERE cancelled_at IS NULL;
