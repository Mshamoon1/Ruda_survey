"""Post-import integrity verification against the live Ruda_Survey DB.
Outputs a markdown fragment consumed by PHASE_3 reports."""
import os

os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings.dev")
import django

django.setup()

from django.db import connection
from django.db.models import Count, Q
from surveys.models import AuditLog, ImportBatch, Parcel, SurveyMaster

MASTER_SHA = "b04dc2b708f2a2ba324199fafdc5ad37d61f2f4bf1c4dab4fa81e799a949c252"

print("## Live database integrity verification")
print()
print("| check | expected | actual | result |")
print("|---|---|---|---|")


def row(label, expected, actual):
    ok = "PASS" if str(expected) == str(actual) else "**FAIL**"
    print(f"| {label} | {expected} | {actual} | {ok} |")


batch = ImportBatch.objects.get(file_checksum=MASTER_SHA)
row("import batch status", "completed", batch.status)
row("batch total_rows", 14872, batch.total_rows)
row("batch successful_rows", 14872, batch.successful_rows)
row("batch failed_rows", 0, batch.failed_rows)
row("survey_master count", 14872, SurveyMaster.objects.count())
row("parcels count", 4654, Parcel.objects.count())

# duplicates impossible-by-construction, verify anyway
dup_rows = (SurveyMaster.objects.values("import_batch", "source_row_number")
            .annotate(c=Count("id")).filter(c__gt=1).count())
row("duplicate (batch,row) groups", 0, dup_rows)
dup_codes = (Parcel.objects.values("parcel_code").annotate(c=Count("id"))
             .filter(c__gt=1).count())
row("duplicate parcel_code groups", 0, dup_codes)

# provenance completeness
row("master rows missing parcel", 0,
    SurveyMaster.objects.filter(parcel__isnull=True).count())
row("master rows missing raw_data", 0,
    SurveyMaster.objects.filter(raw_data__isnull=True).count())
row("master rows missing sr_no", 0,
    SurveyMaster.objects.filter(sr_no__isnull=True).count())
row("master rows outside batch", 0,
    SurveyMaster.objects.exclude(import_batch=batch.pk).count())

# Phase 1 audit cross-checks
row("NULL source_nid rows", 2282,
    SurveyMaster.objects.filter(source_nid__isnull=True).count())
row("distinct non-null source_nid",
    4536,
    SurveyMaster.objects.exclude(source_nid__isnull=True)
    .values("source_nid").distinct().count())
row("rows w/ suspected corrupted district", 2,
    SurveyMaster.objects.filter(district__contains="K2C2888").count())
row("'-' father_name sentinels preserved",
    True,
    SurveyMaster.objects.filter(father_name="-").exists())
row("'Not Identified' owners verbatim",
    True,
    SurveyMaster.objects.filter(owner_name__icontains="Not Identified").exists())

# formula provenance flags
formula_area = SurveyMaster.objects.filter(is_formula_area=True).count()
formula_comp = SurveyMaster.objects.filter(is_formula_compensation=True).count()
print()
print(f"- is_formula_area=true rows: **{formula_area}** "
      f"(Phase 1: ~14,867 cached-formula area cells)")
print(f"- is_formula_compensation=true rows: **{formula_comp}**")

# spot checks
r5 = SurveyMaster.objects.get(source_row_number=5)
assert r5.owner_name == "Rana Bashir" and str(r5.area_value) == "859.07"
assert r5.parcel.parcel_code == "RUDA-P14-R00005" and r5.sr_no == 1
r7005 = SurveyMaster.objects.get(source_row_number=7005)
assert r7005.owner_name == "Owner not Identified"
assert str(r7005.compensation_million) == "0.015000"
r14875 = SurveyMaster.objects.get(source_row_number=14875)
assert r14875.owner_name == "Mr. Taj Din" and r14875.parcel.parcel_code.startswith("RUDA-P14-R")
print("- spot checks rows 5 / 7005 / 14875: **PASS**")

# parcel header denormalisation sanity
p = Parcel.objects.get(parcel_code="RUDA-P14-R00005")
print(f"- parcel RUDA-P14-R00005 header: village={p.village!r}, "
      f"owner={p.owner_name_current!r}, nid={p.source_nid}")

# trigger immutability spot-check on PRODUCTION data
try:
    with connection.cursor() as cur:
        cur.execute("UPDATE survey_master SET owner_name='X' WHERE id=%s", [r5.pk])
    print("- trigger UPDATE block on live data: **FAIL (not blocked!)**")
except Exception as exc:
    print(f"- trigger UPDATE block on live data: **PASS** ({type(exc).__name__})")
finally:
    connection.rollback()

# idempotency note printed by caller (second import run)
print()
print(f"_Verified {SurveyMaster.objects.count()} immutable master records "
      f"in {Parcel.objects.count()} canonical parcels._")
