"""Analyze actual DB schema for Phase 10A planning."""
import os, sys
os.chdir(os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
sys.path.insert(0, os.getcwd())
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'config.settings.dev')
import django
django.setup()

from django.db import connection
from django.db.models import Count
from surveys.models import SurveyMaster, Parcel, SurveyImage, SurveyChange

# 1. Actual DB columns
for table in ['survey_master', 'parcels', 'survey_images', 'survey_changes']:
    with connection.cursor() as cur:
        cur.execute(
            "SELECT column_name, data_type, is_nullable "
            "FROM information_schema.columns "
            "WHERE table_name = %s ORDER BY ordinal_position",
            [table]
        )
        rows = cur.fetchall()
        print(f"\n=== {table} ({len(rows)} columns) ===")
        for col_name, data_type, nullable in rows:
            print(f"  {col_name}: {data_type} {'NULL' if nullable == 'YES' else 'NOT NULL'}")

# 2. Check if khasra/mauza exist
print("\n=== Field existence check ===")
master_fields = [f.name for f in SurveyMaster._meta.get_fields()]
for check in ['khasra_number', 'mauza_number', 'elevation', 'accuracy', 'cnic_no']:
    exists = check in master_fields
    print(f"  {check}: {'EXISTS' if exists else 'MISSING'}")

parcel_fields = [f.name for f in Parcel._meta.get_fields()]
for check in ['khasra_number', 'mauza_number']:
    exists = check in parcel_fields
    print(f"  parcels.{check}: {'EXISTS' if exists else 'MISSING'}")

# 3. Image types
print(f"\n=== Image types ===")
print(f"  Available: {[c[0] for c in SurveyImage.ImageType.choices]}")
image_count = SurveyImage.objects.count()
print(f"  Total images: {image_count}")

# 4. Villages
print(f"\n=== Villages ===")
for v in SurveyMaster.objects.values('village').annotate(c=Count('id')).order_by('-c'):
    print(f"  {v['village']}: {v['c']} rows")

# 5. Coordinates
lat_set = SurveyMaster.objects.filter(latitude__isnull=False).count()
lon_set = SurveyMaster.objects.filter(longitude__isnull=False).count()
print(f"\n=== Coordinates ===")
print(f"  latitude set: {lat_set}/{SurveyMaster.objects.count()}")
print(f"  longitude set: {lon_set}/{SurveyMaster.objects.count()}")

# 6. Revisions
print(f"\n=== Revisions ===")
print(f"  Total: {SurveyChange.objects.count()}")
for s in SurveyChange.objects.values('status').annotate(c=Count('id')):
    print(f"  {s['status']}: {s['c']}")

# 7. Parcels
print(f"\n=== Parcels ===")
print(f"  Total: {Parcel.objects.count()}")
for v in Parcel.objects.values('village').annotate(c=Count('id')).order_by('-c'):
    print(f"  {v['village']}: {v['c']}")
