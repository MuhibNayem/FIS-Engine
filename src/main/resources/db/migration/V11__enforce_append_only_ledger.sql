CREATE OR REPLACE FUNCTION fis_reject_ledger_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% on % is not allowed; ledger tables are append-only', TG_OP, TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS fis_reject_je_update ON fis_journal_entry;
DROP TRIGGER IF EXISTS fis_reject_je_delete ON fis_journal_entry;
DROP TRIGGER IF EXISTS fis_reject_jl_update ON fis_journal_line;
DROP TRIGGER IF EXISTS fis_reject_jl_delete ON fis_journal_line;

CREATE TRIGGER fis_reject_je_update
BEFORE UPDATE ON fis_journal_entry
FOR EACH ROW EXECUTE FUNCTION fis_reject_ledger_mutation();

CREATE TRIGGER fis_reject_je_delete
BEFORE DELETE ON fis_journal_entry
FOR EACH ROW EXECUTE FUNCTION fis_reject_ledger_mutation();

CREATE TRIGGER fis_reject_jl_update
BEFORE UPDATE ON fis_journal_line
FOR EACH ROW EXECUTE FUNCTION fis_reject_ledger_mutation();

CREATE TRIGGER fis_reject_jl_delete
BEFORE DELETE ON fis_journal_line
FOR EACH ROW EXECUTE FUNCTION fis_reject_ledger_mutation();
