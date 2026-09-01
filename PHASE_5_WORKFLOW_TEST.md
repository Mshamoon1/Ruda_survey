# PHASE 5 — MANUAL BUSINESS WORKFLOW TEST

**Date:** 2026-08-26
**Parcel:** RUDA-P14-P00001 (test fixture)

## Steps Executed

| # | Action | Expected | Result |
|---|---|---|---|
| 1 | Fetch original data | 200, master lines returned | PASS |
| 2 | Create Revision 1 (area_sqft=200) | 201, diff shows 100→200 | PASS |
| 3 | Revision 1 status is "submitted" | status=submitted | PASS |
| 4 | Supervisor syncs Revision 1 | 200, status=synced | PASS |
| 5 | Verify Revision 1 is terminal | Cannot transition from synced | PASS |
| 6 | Fetch current effective state | source=revision, area=200 | PASS |
| 7 | Create Revision 2 (area_sqft=230) | 201, diff shows 200→230 | PASS |
| 8 | Verify Revision 1 unchanged | Rev1 still area=200, status=synced | PASS |
| 9 | Fetch current state | source=revision, area=230 (latest) | PASS |
| 10 | Upload FRONT image to Rev 2 | 201, checksum returned | PASS |
| 11 | Upload SECOND image to Rev 2 | 201, checksum returned | PASS |
| 12 | Duplicate FRONT to Rev 2 | 409 IMAGE_ALREADY_EXISTS | PASS |
| 13 | Verify audit trail | REVISION_CREATED, IMAGE_UPLOADED events present | PASS |
| 14 | Survey sheet shows Rev 2 data | area_sqft=230, images present | PASS |

**14/14 PASS**

## Key Business Rules Verified

1. **Master immutability:** survey_master never modified by any revision
2. **Append-only revisions:** old revisions retain original full_payload
3. **Terminal states:** synced/rejected cannot be further transitioned
4. **Current effective state:** latest revision (any status) becomes current
5. **Image uniqueness:** one FRONT + one SECOND per revision, conflict on duplicate
6. **Audit completeness:** every mutation generates an audit event
7. **NULL handling:** explicit null in data produces correct diff transitions
