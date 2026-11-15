-- =====================================================================
-- V15: the weekly payday can be any day (owner decision 2026-11-15), not only Friday or Saturday.
-- The payroll week still runs Monday to Sunday. Payday is the first chosen weekday from that
-- week's Friday on: Friday, Saturday or Sunday fall in the same week, Monday to Thursday in the
-- next one (after the week's work is done). Friday and Saturday keep the dates they had.
-- =====================================================================

ALTER TABLE wage_settings DROP CONSTRAINT ck_wage_settings_payday;
ALTER TABLE wage_settings ADD CONSTRAINT ck_wage_settings_payday
    CHECK (payday IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'));
