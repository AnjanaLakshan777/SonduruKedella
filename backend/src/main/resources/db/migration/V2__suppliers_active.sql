-- Suppliers can be deactivated instead of deleted: expenses (and later supplier invoices) keep
-- pointing at them, so the row has to stay. An inactive supplier just can't be picked for new records.
-- Additive only: every existing supplier becomes active, nothing else changes.
ALTER TABLE suppliers ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

-- No new indexes. Searches are "contains" matches (lower(name) LIKE '%...%'), which a b-tree index
-- can't serve, and a two-value flag on a small directory table isn't worth indexing.
