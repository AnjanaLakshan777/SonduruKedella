-- Inventory additions. All additive: existing materials become active, and the new movement columns
-- are nullable, so every row (and the append-only trigger) is unaffected.

-- Materials are referenced by the movement ledger, so they can't be deleted; they are deactivated.
-- An inactive material takes no new receipts, issues or adjustments, but its history stays readable,
-- and stock already issued from it can still be returned.
ALTER TABLE materials ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

-- V1 can only reach a supplier through supplier_invoice_lines, which don't exist yet. A manual
-- "Record stock received" (cash purchase) can now name its supplier. Receipts only.
ALTER TABLE stock_movements ADD COLUMN supplier_id BIGINT REFERENCES suppliers (id);
ALTER TABLE stock_movements ADD CONSTRAINT ck_stock_movements_supplier
    CHECK (supplier_id IS NULL OR movement_type = 'RECEIVED');
CREATE INDEX ix_stock_movements_supplier ON stock_movements (supplier_id);

-- Client-generated key per submission, as on payments. Rows can never be updated or deleted, so a
-- double-submitted receipt or issue could only be undone by an adjustment; this stops it instead.
-- Nullable: movements posted by the system (supplier invoices, later) don't need one. A UNIQUE
-- constraint allows any number of NULLs.
ALTER TABLE stock_movements ADD COLUMN request_id UUID;
ALTER TABLE stock_movements ADD CONSTRAINT uq_stock_movements_request UNIQUE (request_id);
