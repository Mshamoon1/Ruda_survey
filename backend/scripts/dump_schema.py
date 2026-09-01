"""Dump a full schema verification summary for the Ruda_Survey database.

Usage:
    python scripts/dump_schema.py > ..\\PHASE_2_SCHEMA_SUMMARY.md

Reads live PostgreSQL catalogs (works only on the postgresql backend).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings.dev")

import django  # noqa: E402

django.setup()

from django.db import connection  # noqa: E402

CORE_TABLES = [
    "parcels",
    "survey_master",
    "survey_changes",
    "survey_images",
    "audit_logs",
    "import_batches",
]

print("# PHASE 2 — SCHEMA VERIFICATION SUMMARY (live PostgreSQL)")
print()
print("Database: `Ruda_Survey` · host localhost:5433 · PostgreSQL 18.6 · role `ruda_app`")
print(f"Generated: {__import__('datetime').datetime.now():%Y-%m-%d %H:%M}")
print()


def rows(cursor, sql, params=None):
    cursor.execute(sql, params or [])
    return cursor.fetchall()


with connection.cursor() as cur:
    print("## 1. Tables present")
    print()
    print("| table | rows(schema check) |")
    print("|---|---|")
    for t in CORE_TABLES:
        cur.execute(
            "SELECT 1 FROM information_schema.tables "
            "WHERE table_schema='public' AND table_name=%s", [t]
        )
        found = cur.fetchone() is not None
        print(f"| {t} | {'EXISTS' if found else '**MISSING**'} |")
    print()

    print("## 2. Columns, types and nullability")
    for t in CORE_TABLES:
        print()
        print(f"### `{t}`")
        print()
        print("| column | type | nullable | default |")
        print("|---|---|---|---|")
        data = rows(cur, """
            SELECT column_name, data_type, is_nullable, column_default
            FROM information_schema.columns
            WHERE table_schema='public' AND table_name=%s
            ORDER BY ordinal_position
        """, [t])
        for name, dtype, nullable, dflt in data:
            dflt = (dflt or "")[:40].replace("|", "\\|")
            print(f"| {name} | {dtype} | {nullable} | {dflt} |")

    print()
    print("## 3. Constraints (PK / FK / UNIQUE / CHECK)")
    for t in CORE_TABLES:
        print()
        print(f"### `{t}`")
        print()
        print("| name | type | definition |")
        print("|---|---|---|")
        data = rows(cur, """
            SELECT conname, contype, pg_get_constraintdef(oid)
            FROM pg_constraint
            WHERE conrelid = %s::regclass
            ORDER BY contype, conname
        """, [t])
        label = {"p": "PRIMARY KEY", "f": "FOREIGN KEY", "u": "UNIQUE", "c": "CHECK"}
        for name, ctype, definition in data:
            definition = definition.replace("|", "\\|")
            print(f"| {name} | {label.get(ctype, ctype)} | {definition} |")

    print()
    print("## 4. Indexes")
    for t in CORE_TABLES:
        print()
        print(f"### `{t}`")
        print()
        print("| index name | columns | unique |")
        print("|---|---|---|")
        data = rows(cur, """
            SELECT i.relname AS indexname,
                   ix.indisunique,
                   array_agg(a.attname ORDER BY a.attnum) AS columns
            FROM pg_class t
            JOIN pg_index ix ON t.oid = ix.indrelid
            JOIN pg_class i ON i.oid = ix.indexrelid
            JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(ix.indkey)
            WHERE t.relname = %s AND t.relkind = 'r'
            GROUP BY i.relname, ix.indisunique
            ORDER BY i.relname
        """, [t])
        for idxname, uniq, cols in data:
            print(f"| {idxname} | {', '.join(cols)} | {'yes' if uniq else 'no'} |")

    print()
    print("## 5. Immutability triggers")
    print()
    print("| table | trigger | event | function | enabled |")
    print("|---|---|---|---|---|")
    data = rows(cur, """
        SELECT c.relname AS tablename, t.tgname,
               pg_get_triggerdef(t.oid),
               p.proname,
               CASE WHEN t.tgenabled IN ('O','A') THEN 'enabled' ELSE 'DISABLED' END
        FROM pg_trigger t
        JOIN pg_class c ON c.oid = t.tgrelid
        JOIN pg_proc p ON p.oid = t.tgfoid
        WHERE NOT t.tgisinternal AND c.relnamespace = 'public'::regnamespace
        ORDER BY c.relname, t.tgname
    """)
    for table, trg, definition, fn, enabled in data:
        print(f"| {table} | {trg} | — | {fn}() | {enabled} |")

    print()
    print("## 6. Trigger functions")
    print()
    for fn in ("surveys_block_all_mutation", "surveys_changes_lifecycle_guard"):
        cur.execute("SELECT 1 FROM pg_proc WHERE proname=%s", [fn])
        status = "present" if cur.fetchone() else "**MISSING**"
        print(f"- `{fn}()` → {status}")
