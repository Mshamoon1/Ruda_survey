# ANDROID API CONTRACT — FROZEN

**Status:** Phase 5 FROZEN — Do NOT modify without explicit approval
**Base URL:** `/api/v1/`
**Auth:** JWT Bearer token in `Authorization` header

---

## Authentication

### POST /api/v1/auth/login/

**Request:**
```json
{
  "username": "string",
  "password": "string"
}
```
**Response 200:**
```json
{
  "access": "eyJ...",
  "refresh": "eyJ...",
  "user": {
    "id": 1,
    "username": "surveyor1",
    "first_name": "Ali",
    "last_name": "Ahmed",
    "role": "SURVEYOR"
  }
}
```
**Errors:** 401 INVALID_CREDENTIALS, 401 USER_INACTIVE, 429 THROTTLED

### POST /api/v1/auth/refresh/

**Request:**
```json
{
  "refresh": "eyJ..."
}
```
**Response 200:**
```json
{
  "access": "eyJ..."
}
```
**Errors:** 401 INVALID_CREDENTIALS

### POST /api/v1/auth/logout/

**Request:**
```json
{
  "refresh": "eyJ..."
}
```
**Response 200:**
```json
{
  "detail": "Logged out."
}
```
**Errors:** 401 INVALID_CREDENTIALS

### GET /api/v1/auth/me/

**Response 200:**
```json
{
  "id": 1,
  "username": "surveyor1",
  "first_name": "Ali",
  "last_name": "Ahmed",
  "role": "SURVEYOR"
}
```
**Errors:** 401 AUTHENTICATION_REQUIRED

---

## Surveys

### GET /api/v1/surveys/parcel/{parcel_code}/

**Response 200:**
```json
{
  "parcel": {
    "parcel_code": "RUDA-P14-R00005",
    "source_nid": 606,
    "village": "Arya Nagar",
    "tehsil": "Ferozwala",
    "district": "Sheikhupura",
    "owner_name_current": "Rana Bashir",
    "khasra_number": null,
    "mauza_number": null
  },
  "original": {
    "area_sqft": 100.0,
    "owner_name": "Rana Bashir",
    ...
  },
  "current_revision": null,
  "revision_no": 0,
  "source": "master"
}
```
**When revision exists:** `current_revision` contains `{revision_no, full_payload, changed_fields, status}` and `source: "revision"`.

**Errors:** 404 PARCEL_NOT_FOUND, 401 AUTHENTICATION_REQUIRED

### GET /api/v1/surveys/search/

**Search parcels by khasra_number and/or mauza_number.** Both parameters optional but at least one required. Case-insensitive, whitespace-trimmed.

**Request:**
```
GET /api/v1/surveys/search/?khasra_number=123&mauza_number=45
```

**Response 200:**
```json
{
  "results": [
    {
      "parcel_code": "RUDA-P14-R00005",
      "khasra_number": "123",
      "mauza_number": "45",
      "owner_name": "Rana Bashir",
      "village": "Arya Nagar",
      "tehsil": "Ferozwala",
      "district": "Sheikhupura",
      "source_nid": 606
    }
  ],
  "count": 1
}
```

**When no results:** `"results": [], "count": 0`

**Errors:** 400 VALIDATION_ERROR (no search params), 401 AUTHENTICATION_REQUIRED

**Note:** Maximum 50 results returned. `khasra_number` and `mauza_number` are field-survey-only fields — they will be NULL until populated through the revision workflow.

### GET /api/v1/surveys/{parcel_code}/original/

**Response 200:**
```json
{
  "parcel": { "parcel_code": "...", "source_nid": ..., "village": "...", ... },
  "import_batch": { "id": 1, "file_checksum": "...", "imported_at": "..." },
  "lines": [ { "sr_no": 1, "source_row_number": 5, "owner_name": "...", ... } ]
}
```
**Errors:** 404 PARCEL_NOT_FOUND

### GET /api/v1/surveys/{parcel_code}/current/

**Response 200:**
```json
{
  "parcel": { "parcel_code": "RUDA-P14-R00005" },
  "source": "master|revision",
  "revision_no": 0,
  "data": { "area_sqft": 100.0, "owner_name": "...", ... }
}
```
**Behavior:** Returns latest revision's `full_payload` if any revision exists, otherwise master baseline.

### GET /api/v1/surveys/{parcel_code}/revisions/

**Response 200 (paginated):**
```json
{
  "count": 3,
  "next": null,
  "previous": null,
  "results": [
    {
      "revision_no": 3,
      "status": "submitted",
      "changed_by": "surveyor1",
      "changed_at": "2026-08-26T10:00:00Z",
      "created_at": "2026-08-26T10:00:00Z",
      "accepted_at": "2026-08-26T10:00:00Z",
      "change_reason": "re-measurement",
      "client_uuid": "550e8400-e29b-41d4-a716-446655440000",
      "changes": { "area_sqft": { "old": 100.0, "new": 120 } },
      "images": []
    }
  ]
}
```
**Note:** Does NOT include `full_payload` (only create response does).

### POST /api/v1/surveys/{parcel_code}/revisions/

**Request (FROZEN — Android must send exactly this):**
```json
{
  "client_uuid": "550e8400-e29b-41d4-a716-446655440000",
  "data": {
    "area_sqft": 120,
    "cnic_no": "35201-1234567-8",
    "owner_name": "New Owner",
    "structure_name": null
  },
  "change_reason": "Field re-measurement",
  "device_info": { "model": "Pixel 7", "os": "Android 14" },
  "parent_revision_no": 2
}
```

**Response 201 (fresh creation):**
```json
{
  "replayed": false,
  "id": 5,
  "revision_no": 3,
  "status": "submitted",
  "client_uuid": "550e8400-e29b-41d4-a716-446655440000",
  "accepted_at": "2026-08-26T10:00:00Z",
  "diff": {
    "area_sqft": { "old": 100.0, "new": 120 },
    "cnic_no": { "old": null, "new": "35201-1234567-8" }
  },
  "full_payload": { ... }
}
```

**Response 200 (idempotent replay):**
```json
{
  "replayed": true,
  "id": 5,
  "revision_no": 3,
  "status": "submitted",
  "client_uuid": "...",
  "accepted_at": "...",
  "diff": {},
  "full_payload": { ... }
}
```

**Server-side generated (NEVER client-controlled):**
- `revision_no`
- `changed_by`
- `changed_at`
- `status` (always "submitted")
- `accepted_at`
- `id`

**Errors:**
- 400 VALIDATION_ERROR (unknown fields, no changes, validation)
- 404 PARCEL_NOT_FOUND
- 409 REVISION_CONFLICT (stale parent or numbering conflict)
- 401 AUTHENTICATION_REQUIRED

### POST /api/v1/surveys/{parcel_code}/revisions/{revision_no}/images/

**Request:** `multipart/form-data`
- `image_type`: "FRONT", "SECOND", "POINT_1", or "POINT_2"
- `file`: JPEG or PNG image

**Image Types:**
| Type | Description |
|---|---|
| FRONT | Front evidence photo |
| SECOND | Secondary/survey evidence photo |
| POINT_1 | Point/coordinate evidence image 1 (NEW - Phase 10A) |
| POINT_2 | Point/coordinate evidence image 2 (NEW - Phase 10A) |

**Response 201:**
```json
{
  "id": 1,
  "image_type": "FRONT",
  "checksum_sha256": "a1b2c3...",
  "storage_key": "surveys/RUDA-P14-R00005/0001/FRONT_a1b2c3d4e5.jpg",
  "content_type": "image/jpeg",
  "file_size": 102400,
  "width_px": 1920,
  "height_px": 1080
}
```

**Errors:**
- 400 INVALID_IMAGE (wrong type, oversized, corrupt)
- 404 INVALID_REVISION
- 409 IMAGE_ALREADY_EXISTS
- 401 AUTHENTICATION_REQUIRED

### POST /api/v1/surveys/{parcel_code}/revisions/{revision_no}/status/

**Request:**
```json
{
  "target": "synced",
  "reason": "Approved after field verification"
}
```
**Valid targets:** `synced`, `rejected`

**Response 200:**
```json
{
  "revision_no": 1,
  "status": "synced"
}
```
**Permissions:** SUPERVISOR or ADMIN only (403 for SURVEYOR)

### GET /api/v1/surveys/{parcel_code}/sheet/

**Response 200:**
```json
{
  "parcel": { "parcel_code": "...", ... },
  "source": "master|revision",
  "current_revision_no": 1,
  "surveyor": "surveyor1",
  "changed_at": "2026-08-26T10:00:00Z",
  "accepted_at": "2026-08-26T10:00:00Z",
  "images": [ { "image_type": "FRONT", "checksum_sha256": "...", ... } ],
  "fields": { "area_sqft": 120, "owner_name": "...", ... },
  "original": { "area_sqft": 100.0, "owner_name": "...", "cnic_no": null, ... }
}
```
**Note:** `fields` omits NULL values. `original` preserves them.

### GET /api/v1/surveys/{parcel_code}/pdf/

**Response 503:**
```json
{
  "error": {
    "code": "PDF_GENERATOR_UNAVAILABLE",
    "message": "PDF generation is not available yet...",
    "details": {}
  }
}
```

---

## Pagination

All list endpoints support:
- `?page=N` (default 1)
- `?page_size=N` (default set by server, max 100)

---

## Request/Response Headers

**Required:** `Authorization: Bearer {access_token}` (except login/refresh)
**Content-Type:** `application/json` (or `multipart/form-data` for image upload)
