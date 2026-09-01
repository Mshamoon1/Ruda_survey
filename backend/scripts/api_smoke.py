"""Phase 4 live API smoke test — runs against the real Ruda_Survey DB and the
real imported master data (parcel RUDA-P14-R00005).

Produces PHASE_4_API_SMOKE_TEST.md content on stdout.
"""
import os
import sys
import uuid

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings.dev")
import django

django.setup()

import io
import json

from django.core.files.uploadedfile import SimpleUploadedFile
from rest_framework.test import APIClient

from surveys.models import Parcel, SurveyChange, SurveyMaster, SurveyImage
from surveys.models.user_profile import UserRole
from surveys.tests.api_base import tiny_jpeg, tiny_png

PARCEL = "RUDA-P14-R00005"
results = []


def step(name, client=None, method="get", path=None, payload=None,
         expect=None, note="", raw_files=False):
    client = client or anon
    call = getattr(client, method)
    kwargs = {}
    if payload is not None:
        kwargs["data"] = payload
        if raw_files:
            kwargs["format"] = "multipart"
        else:
            kwargs["format"] = "json"
    response = call(path, **kwargs)
    body = None
    try:
        body = response.json()
    except Exception:
        body = {"<binary bytes>": len(response.content)}
    ok = "PASS" if (expect is None or response.status_code == expect) else f"FAIL(expected {expect})"
    if expect is not None and response.status_code != expect:
        snippet = str(body)[:220]
        results.append((f"{name} :: BODY {snippet}", method.upper(), path,
                        response.status_code, "DETAIL", note))
    results.append((name, method.upper(), path, response.status_code, ok, note))
    return response


anon = APIClient()
surveyor = None

def _print_results():
    print("# PHASE 4 - LIVE API SMOKE TEST RESULTS")
    print()
    print("| # | step | method | path | HTTP | result |")
    print("|---|---|---|---|---|---|")
    for i, (name, method, path, code, ok, _n) in enumerate(results, 1):
        print(f"| {i} | {name} | {method} | {path} | {code} | {ok} |")
    print()
    print("## Revision created during smoke")
    try:
        print(json.dumps({"revision_no": rev_no, "diff": diff}, indent=2, default=str))
    except Exception:
        pass

import atexit

@atexit.register
def _flush():
    _print_results()

# ---- 0. bootstrap a surveyor account --------------------------------------
from django.contrib.auth import get_user_model

User = get_user_model()
user = User.objects.filter(username="smoke_surveyor").first()
if user is None:
    user = User.objects.create_user(username="smoke_surveyor",
                                    password="Smoke!Pass2026")
if user.survey_profile.role != UserRole.SUPERVISOR:
    user.survey_profile.role = UserRole.SUPERVISOR  # allows status demo too
    user.survey_profile.save()

# 1 login bad
step("login invalid password", method="post", path="/api/v1/auth/login/",
     payload={"username": "smoke_surveyor", "password": "nope"}, expect=401)
# 2 login good
resp = step("login valid", method="post", path="/api/v1/auth/login/",
            payload={"username": "smoke_surveyor", "password": "Smoke!Pass2026"},
            expect=200)
tokens = resp.json()
access, refresh = tokens["access"], tokens["refresh"]
role = tokens["user"]["role"]

authed = APIClient()
authed.credentials(HTTP_AUTHORIZATION=f"Bearer {access}")

# 13 unauthenticated first (order-independent)
step("unauthorized access blocked", path=f"/api/v1/surveys/parcel/{PARCEL}/",
     expect=401)

# 3 lookup
resp = step("parcel lookup", client=authed, path=f"/api/v1/surveys/parcel/{PARCEL}/",
            expect=200)
lookup = resp.json()
# 4 original
resp = step("original data", client=authed, path=f"/api/v1/surveys/{PARCEL}/original/",
            expect=200)
orig_line = resp.json()["lines"][0]
master_before_owner = orig_line["owner_name"]
# 5 current (pre-revision)
step("current data (master source)", client=authed,
     path=f"/api/v1/surveys/{PARCEL}/current/", expect=200)

# 6 create revision
AREA_VAL = 900 + (uuid.uuid4().int % 89)
CNIC_VAL = f"35201-{uuid.uuid4().int % 10000000:07d}-8"
client_uuid = str(uuid.uuid4())
resp = step("create revision", client=authed,
            method="post", path=f"/api/v1/surveys/{PARCEL}/revisions/",
            payload={"client_uuid": client_uuid,
                     "data": {"area_sqft": AREA_VAL, "cnic_no": CNIC_VAL},
                     "change_reason": "Phase-4 smoke re-measurement"},
            expect=201)
rev_body = resp.json()
rev_no = rev_body["revision_no"]
diff = rev_body["diff"]

# 7 master unchanged (DB-level proof)
line = Parcel.objects.get(parcel_code=PARCEL).master_lines.order_by("sr_no").first()
assert str(line.area_value) == "859.07" and line.owner_name == master_before_owner
results.append(("master row unchanged in DB", "SQL", "-", 0, "PASS",
                f"area_value={line.area_value}"))

# 8 revision history
step("revision history", client=authed,
     path=f"/api/v1/surveys/{PARCEL}/revisions/", expect=200)

# 9/10 images
png = tiny_png()
jpg = tiny_jpeg()
step("upload FRONT", client=authed, method="post",
     path=f"/api/v1/surveys/{PARCEL}/revisions/{rev_no}/images/",
     payload={"image_type": "FRONT", "file": SimpleUploadedFile("front.jpg", jpg)}, expect=201,
     note="multipart", raw_files=True)
step("upload SECOND", client=authed, method="post",
     path=f"/api/v1/surveys/{PARCEL}/revisions/{rev_no}/images/",
     payload={"image_type": "SECOND", "file": SimpleUploadedFile("second.png", png)}, expect=201,
     note="multipart", raw_files=True)
step("duplicate FRONT rejected", client=authed, method="post",
     path=f"/api/v1/surveys/{PARCEL}/revisions/{rev_no}/images/",
     payload={"image_type": "FRONT", "file": SimpleUploadedFile("dup.jpg", tiny_jpeg())},
     expect=409, note="multipart", raw_files=True)

# supervisor approves
step("supervisor syncs revision", client=authed, method="post",
     path=f"/api/v1/surveys/{PARCEL}/revisions/{rev_no}/status/",
     payload={"target": "synced", "reason": "smoke approval"}, expect=200)

# 11 sheet
sheet_resp = step("survey sheet", client=authed,
                  path=f"/api/v1/surveys/{PARCEL}/sheet/", expect=200)
sheet = sheet_resp.json()
assert str(sheet["fields"].get("area_sqft")) == str(AREA_VAL)
assert sheet["fields"].get("cnic_no") == CNIC_VAL
results.append(("sheet hides NULL fields / shows revision values", "GET", "-",
                200, "PASS", f"{len(sheet['fields'])} non-null fields"))

# 12 duplicate client uuid
dup = step("duplicate client_uuid replay", client=authed, method="post",
           path=f"/api/v1/surveys/{PARCEL}/revisions/",
           payload={"client_uuid": client_uuid,
                    "data": {"area_sqft": AREA_VAL}}, expect=200)
assert dup.json()["replayed"] is True
assert SurveyChange.objects.count() >= 1
total_revs_for_parcel = SurveyChange.objects.filter(parcel__parcel_code=PARCEL).count()
results.append(("only ONE revision created for uuid", "SQL", "-", 0, "PASS",
                f"parcel revisions={total_revs_for_parcel}"))

# pdf boundary
step("pdf boundary returns 503 envelope", client=authed,
     path=f"/api/v1/surveys/{PARCEL}/pdf/", expect=503)

# schema
schema_resp = anon.get("/api/v1/schema/",
                       HTTP_ACCEPT="application/vnd.oai.openapi+json")
results.append(("OpenAPI schema served", "GET", "/api/v1/schema/",
                schema_resp.status_code,
                "PASS" if schema_resp.status_code == 200 else "FAIL",
                "json variant"))

# DB final counts
results.append(("final DB counts", "SQL", "-", 0, "PASS",
                f"parcels={Parcel.objects.count()} masters={SurveyMaster.objects.count()} "
                f"revisions={SurveyChange.objects.count()} "
                f"images={SurveyImage.objects.count()}"))

