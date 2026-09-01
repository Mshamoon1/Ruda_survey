# PHASE 4 — LIVE API SMOKE TEST RESULTS

**Date:** 2026-08-26
**Environment:** PostgreSQL 18.6 localhost:5433, database `Ruda_Survey`, real imported data
**Parcel:** `RUDA-P14-R00005` (14,872 master rows, 4,654 parcels)
**Fix:** Multipart uploads migrated from `django.test.Client` to `rest_framework.test.APIClient` (DRF `format='multipart'` is required for proper multipart encoding)

---

## Results

| # | step | method | path | HTTP | result |
|---|---|---|---|---|---|
| 1 | login invalid password | POST | /api/v1/auth/login/ | 401 | PASS |
| 2 | login valid | POST | /api/v1/auth/login/ | 200 | PASS |
| 3 | unauthorized access blocked | GET | /api/v1/surveys/parcel/RUDA-P14-R00005/ | 401 | PASS |
| 4 | parcel lookup | GET | /api/v1/surveys/parcel/RUDA-P14-R00005/ | 200 | PASS |
| 5 | original data | GET | /api/v1/surveys/RUDA-P14-R00005/original/ | 200 | PASS |
| 6 | current data (master source) | GET | /api/v1/surveys/RUDA-P14-R00005/current/ | 200 | PASS |
| 7 | create revision | POST | /api/v1/surveys/RUDA-P14-R00005/revisions/ | 201 | PASS |
| 8 | master row unchanged in DB | SQL | - | 0 | PASS |
| 9 | revision history | GET | /api/v1/surveys/RUDA-P14-R00005/revisions/ | 200 | PASS |
| 10 | upload FRONT | POST | /api/v1/surveys/RUDA-P14-R00005/revisions/{n}/images/ | 201 | PASS |
| 11 | upload SECOND | POST | /api/v1/surveys/RUDA-P14-R00005/revisions/{n}/images/ | 201 | PASS |
| 12 | duplicate FRONT rejected | POST | /api/v1/surveys/RUDA-P14-R00005/revisions/{n}/images/ | 409 | PASS |
| 13 | supervisor syncs revision | POST | /api/v1/surveys/RUDA-P14-R00005/revisions/{n}/status/ | 200 | PASS |
| 14 | survey sheet | GET | /api/v1/surveys/RUDA-P14-R00005/sheet/ | 200 | PASS |
| 15 | sheet hides NULL fields / shows revision values | GET | - | 200 | PASS |
| 16 | duplicate client_uuid replay | POST | /api/v1/surveys/RUDA-P14-R00005/revisions/ | 200 | PASS |
| 17 | only ONE revision created for uuid | SQL | - | 0 | PASS |
| 18 | pdf boundary returns 503 envelope | GET | /api/v1/surveys/RUDA-P14-R00005/pdf/ | 503 | PASS |
| 19 | OpenAPI schema served | GET | /api/v1/schema/ | 200 | PASS |
| 20 | final DB counts | SQL | - | 0 | PASS |

**20/20 PASS**

---

## Revision created during smoke

```json
{
  "revision_no": 8,
  "diff": {
    "area_sqft": {
      "old": 923,
      "new": 959
    },
    "cnic_no": {
      "old": "35201-4597072-8",
      "new": "35201-2959443-8"
    }
  }
}
```

## Key observations

- **Image multipart**: Requires `rest_framework.test.APIClient` with `format='multipart'`. Django's `test.Client` ignores the `format` kwarg and sends empty bodies.
- **NULL hiding**: `sheet/fields` correctly excludes NULL values; `sheet/original` preserves them.
- **Idempotency**: Duplicate `client_uuid` returns 200 `replayed=true` with no additional DB row.
- **Master immutability**: Direct DB query confirmed `area_value` and `owner_name` unchanged after revision creation.
- **DB counts**: parcels=4654, masters=14872, revisions=8 (pre-existing + smoke), images=3 (smoke uploads).
