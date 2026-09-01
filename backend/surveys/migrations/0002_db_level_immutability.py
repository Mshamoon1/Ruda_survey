"""
Database-level immutability for RUDA Survey (PostgreSQL).

Strategy (documented in doc 03 §3 and PHASE_2_IMPLEMENTATION_REPORT.md):

* survey_master / audit_logs : BEFORE UPDATE OR DELETE → unconditional exception.
* parcels                    : BEFORE DELETE → exception (children FK PROTECT
                                already blocks deletes; this also covers a
                                childless parcel so history can never dangle).
* survey_changes             : append-only with a narrow lifecycle channel —
                                UPDATE is allowed ONLY when every changed
                                column is status/accepted_at; DELETE blocked.

Escape hatch (deliberate, auditable, never used by application code):
    ALTER TABLE <table> DISABLE TRIGGER <trigger_name>;   -- superuser only

The SQL is applied only when the connection vendor is postgresql. On other
backends (e.g. SQLite test runs) the ORM guards in surveys.models.immutable
remain the enforcement layer; this limitation is documented.
"""
from django.db import migrations

BLOCK_ALL_FN = """
CREATE OR REPLACE FUNCTION surveys_block_all_mutation() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
    RAISE EXCEPTION 'RUDA-SURVEY: % on %.% is forbidden (immutable record)',
        TG_OP, TG_TABLE_SCHEMA, TG_TABLE_NAME;
END;
$fn$;
"""

CHANGES_GUARD_FN = """
CREATE OR REPLACE FUNCTION surveys_changes_lifecycle_guard() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'RUDA-SURVEY: DELETE on %.% is forbidden (append-only revisions)',
            TG_TABLE_SCHEMA, TG_TABLE_NAME;
    END IF;
    IF NEW.client_uuid       IS DISTINCT FROM OLD.client_uuid
        OR NEW.parcel_id     IS DISTINCT FROM OLD.parcel_id
        OR NEW.revision_no   IS DISTINCT FROM OLD.revision_no
        OR NEW.parent_revision_id IS DISTINCT FROM OLD.parent_revision_id
        OR NEW.base_master_row_ids IS DISTINCT FROM OLD.base_master_row_ids
        OR NEW.changes       IS DISTINCT FROM OLD.changes
        OR NEW.full_payload  IS DISTINCT FROM OLD.full_payload
        OR NEW.change_reason IS DISTINCT FROM OLD.change_reason
        OR NEW.changed_by_id IS DISTINCT FROM OLD.changed_by_id
        OR NEW.changed_at    IS DISTINCT FROM OLD.changed_at
        OR NEW.created_at    IS DISTINCT FROM OLD.created_at
        OR NEW.device_info   IS DISTINCT FROM OLD.device_info
    THEN
        RAISE EXCEPTION
            'RUDA-SURVEY: only status/accepted_at may change on % (append-only revisions)',
            TG_TABLE_NAME;
    END IF;
    RETURN NEW;
END;
$fn$;
"""

TRIGGERS = [
    # (table, trigger name, event, function, FOR EACH)
    ("parcels", "trg_parcels_no_delete", "DELETE", "surveys_block_all_mutation", "STATEMENT"),
    ("survey_master", "trg_survey_master_immutable", "UPDATE OR DELETE", "surveys_block_all_mutation", "STATEMENT"),
    ("audit_logs", "trg_audit_logs_immutable", "UPDATE OR DELETE", "surveys_block_all_mutation", "STATEMENT"),
    ("survey_changes", "trg_survey_changes_appendonly", "UPDATE OR DELETE", "surveys_changes_lifecycle_guard", "ROW"),
]


def _is_postgresql(schema_editor):
    return schema_editor.connection.vendor == "postgresql"


def forwards(apps, schema_editor):
    if not _is_postgresql(schema_editor):
        return
    with schema_editor.connection.cursor() as cursor:
        cursor.execute(BLOCK_ALL_FN)
        cursor.execute(CHANGES_GUARD_FN)
        for table, trg, events, fn, level in TRIGGERS:
            cursor.execute(
                f"CREATE TRIGGER {trg} BEFORE {events} ON {table} "
                f"FOR EACH {level} EXECUTE FUNCTION {fn}();"
            )


def backwards(apps, schema_editor):
    if not _is_postgresql(schema_editor):
        return
    with schema_editor.connection.cursor() as cursor:
        for table, trg, *_ in TRIGGERS:
            cursor.execute(f"DROP TRIGGER IF EXISTS {trg} ON {table};")
        cursor.execute("DROP FUNCTION IF EXISTS surveys_changes_lifecycle_guard();")
        cursor.execute("DROP FUNCTION IF EXISTS surveys_block_all_mutation();")


class Migration(migrations.Migration):

    dependencies = [
        ("surveys", "0001_initial"),
    ]

    operations = [
        migrations.RunPython(forwards, backwards),
    ]
