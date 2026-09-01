import os, sys
os.chdir(os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
sys.path.insert(0, os.getcwd())
os.environ['DJANGO_SETTINGS_MODULE'] = 'config.settings.dev'
import django
django.setup()

from surveys.models import Parcel, SurveyImage, SurveyMaster
from surveys.services.revision_service import master_baseline, ALLOWED_PAYLOAD_FIELDS, TEXT_LIMITS
from surveys.services.sheet_service import SHEET_FIELD_ORDER

p = Parcel.objects.first()
print('Parcel khasra:', p.khasra_number, 'mauza:', p.mauza_number)
print('ALLOWED_PAYLOAD_FIELDS:', sorted(ALLOWED_PAYLOAD_FIELDS))
print('TEXT_LIMITS:', TEXT_LIMITS)
print('SHEET_FIELD_ORDER:', SHEET_FIELD_ORDER)
print('Image types:', [c[0] for c in SurveyImage.ImageType.choices])
bl = master_baseline(p)
print('master_baseline khasra:', bl.get('khasra_number'), 'mauza:', bl.get('mauza_number'))
print('ALL BACKEND VERIFICATIONS PASSED')
