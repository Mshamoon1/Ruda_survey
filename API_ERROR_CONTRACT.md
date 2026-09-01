# API ERROR CONTRACT — FROZEN

**Status:** Phase 5 FROZEN

All error responses follow:
```json
{
  "error": {
    "code": "STABLE_ERROR_CODE",
    "message": "Human-readable description",
    "details": {}
  }
}
```

---

## Error Codes

| Code | HTTP | When |
|---|---|---|
| `AUTHENTICATION_REQUIRED` | 401 | No token or expired access token |
| `INVALID_CREDENTIALS` | 401 | Wrong username/password or invalid refresh token |
| `USER_INACTIVE` | 401 | Account is disabled |
| `TOKEN_EXPIRED` | 401 | Access token expired |
| `PERMISSION_DENIED` | 403 | Authenticated but insufficient role |
| `PARCEL_NOT_FOUND` | 404 | Parcel code does not exist |
| `NOT_FOUND` | 404 | Generic resource not found |
| `INVALID_REVISION` | 404 | Revision number does not exist for this parcel |
| `VALIDATION_ERROR` | 400 | Invalid request body, unknown fields, no changes, etc. |
| `INVALID_IMAGE` | 400 | File is not a valid JPEG/PNG or exceeds size limit |
| `REVISION_CONFLICT` | 409 | Stale parent revision or numbering conflict |
| `STALE_REVISION` | 409 | Client base revision is behind server (alias for REVISION_CONFLICT) |
| `IMAGE_ALREADY_EXISTS` | 409 | Image of this type already exists for the revision |
| `DUPLICATE_CLIENT_UUID` | 200 | Same client_uuid replayed (not an error — idempotent success) |
| `IDEMPOTENCY_CONFLICT` | 409 | Same client_uuid with conflicting data (currently returns 200 with replayed=true) |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | Wrong Content-Type header (e.g., text/plain on JSON endpoint) |
| `THROTTLED` | 429 | Rate limit exceeded |
| `PDF_GENERATOR_UNAVAILABLE` | 503 | PDF not yet implemented |
| `SERVER_ERROR` | 500 | Unexpected internal error (no stack trace exposed) |

---

## Client Error Handling Guide

| Error Code | Android Action |
|---|---|
| AUTHENTICATION_REQUIRED | Redirect to login |
| INVALID_CREDENTIALS | Show "Invalid username or password" |
| USER_INACTIVE | Show "Account disabled. Contact admin." |
| TOKEN_EXPIRED | Attempt refresh; if refresh fails → login |
| PERMISSION_DENIED | Show "Insufficient permissions" |
| PARCEL_NOT_FOUND | Show "Parcel not found. Check parcel ID." |
| INVALID_REVISION | Refresh revision list |
| VALIDATION_ERROR | Show field-level errors from `details` |
| INVALID_IMAGE | Show "Image must be JPEG or PNG, under 10 MB" |
| REVISION_CONFLICT / STALE_REVISION | Show "Survey updated by another user. Refresh and retry." |
| IMAGE_ALREADY_EXISTS | Show "Photo already uploaded for this revision" |
| UNSUPPORTED_MEDIA_TYPE | Check Content-Type header |
| THROTTLED | Show "Too many requests. Wait and retry." |
| SERVER_ERROR | Show "Server error. Try again later." |

---

## Status Transitions (Server-Side State Machine)

```
SUBMITTED → SYNCED (supervisor approval)
SUBMITTED → REJECTED (supervisor rejection)
SYNCED    → (terminal, no further transitions)
REJECTED  → (terminal, no further transitions)
```

- **SURVEYOR** can create revisions (status=SUBMITTED) but cannot transition status
- **SUPERVISOR** can transition SUBMITTED → SYNCED or REJECTED
- **ADMIN** can do everything SUPERVISOR can
- **No user** can transition from SYNCED or REJECTED
