# Phase 11 — Production User Creation API

**Date:** Sep 2 2026
**Status:** ✅ COMPLETE

---

## Summary

Admin-only user creation endpoint added at `POST /api/v1/auth/users/`. Admins can create Surveyor, Supervisor, or Admin accounts. Passwords are hashed, credentials never returned, audit trail recorded.

---

## Endpoint

### `POST /api/v1/auth/users/`

**Auth:** Bearer token + `ADMIN` role required

**Request body:**
```json
{
  "username": "new_surveyor",
  "password": "secure-pass-123",
  "email": "new@ruda.pk",           // optional
  "first_name": "Ahmed",            // optional
  "last_name": "Khan",              // optional
  "role": "SURVEYOR"                // SURVEYOR | SUPERVISOR | ADMIN (default: SURVEYOR)
}
```

**Response (201):**
```json
{
  "id": 5,
  "username": "new_surveyor",
  "email": "new@ruda.pk",
  "first_name": "Ahmed",
  "last_name": "Khan",
  "role": "SURVEYOR",
  "is_active": true,
  "date_joined": "2026-09-02T..."
}
```

**Error responses:**
- `400` — missing username/password, invalid role, duplicate username/email, invalid email
- `401` — unauthenticated
- `403` — non-admin
- `403` — is_staff/is_superuser injection rejected

---

## Files Modified

| File | Change |
|------|--------|
| `backend/surveys/api/serializers.py` | Added `UserCreateSerializer`, `UserCreateResponseSerializer` |
| `backend/surveys/api/views.py` | Added `CreateUserView` (GenericAPIView) |
| `backend/surveys/api/urls.py` | Added `auth/users/` route |
| `backend/surveys/tests/test_api_user_creation.py` | Created — 24 tests |

---

## Tests (24/24 PASS)

| # | Test | Category |
|---|------|----------|
| 01 | Admin creates user → 201 | Success |
| 02 | Password is hashed (bcrypt) | Success |
| 03 | Raw password not returned | Success |
| 04 | Created user can login | Success |
| 05 | Created user hits /me/ | Success |
| 06 | UserProfile auto-created | Success |
| 07 | Audit log recorded | Success |
| 08 | Audit log has no credentials | Success |
| 09 | Create admin role | Success |
| 10 | Create supervisor role | Success |
| 11 | Optional fields default correctly | Success |
| 12 | Unauthenticated → 401 | Auth |
| 13 | Surveyor → 403 | Auth |
| 14 | Supervisor → 403 | Auth |
| 15 | Missing username → 400 | Validation |
| 16 | Missing password → 400 | Validation |
| 17 | Invalid role → 400 | Validation |
| 18 | Duplicate username → 400 | Validation |
| 19 | Duplicate email → 400 | Validation |
| 20 | Invalid email → 400 | Validation |
| 21 | is_superuser injection blocked | Security |
| 22 | is_staff injection blocked | Security |
| 23 | Empty body → 400 | Security |
| 24 | Existing login regression green | Security |

---

## Smoke Test (Manual)

Full flow verified against live server:

1. Admin login → token ✅
2. `POST /api/v1/auth/users/` → 201, user created ✅
3. New user login → token ✅
4. New user `/me/` → correct profile ✅

---

## Regression

- 24 Phase 11 tests: **ALL PASS**
- 65 core tests (auth, surveys, revisions, user creation): **ALL PASS**
- `makemigrations --check`: No pending migrations
- `manage.py check`: No issues

---

## Bug Fixes in This Phase

### Role assignment race condition
**Root cause:** `UserProfile` signal auto-creates profile with default `SURVEYOR` role. `create()` updated role via `UserProfile.objects.filter().update()` but the serializer response read the stale profile.

**Fix:** Added `user.refresh_from_db()` after role update to ensure response reflects actual DB state.

### Unexpected keyword arguments
**Root cause:** `User(**validated_data)` received `role` which doesn't belong to Django's `User` model.

**Fix:** Pop `role` from `validated_data` before creating User; update UserProfile separately.
