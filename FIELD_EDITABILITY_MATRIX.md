# FIELD EDITABILITY MATRIX

**Status:** Phase 10A UPDATED — khasra_number and mauza_number added to parcels for search
**Source:** `surveys.services.revision_service.ALLOWED_PAYLOAD_FIELDS`

---

## Survey Fields (Editable via revision data payload)

| Field | Source | Type | Nullable | Read Only? | Editable? | Visible When NULL? | Notes |
|---|---|---|---|---|---|---|---|
| rd_value | Field survey | number | Yes | No | Yes | Hidden | Rate/valuation |
| latitude | Master + survey | number | Yes | No | Yes | Hidden | GPS coordinate |
| longitude | Master + survey | number | Yes | No | Yes | Hidden | GPS coordinate |
| package_no | Fixed | string(32) | No | No | Yes | N/A | Always "PKG-14" |
| village | Master + survey | string(128) | No | No | Yes | N/A | |
| owner_name | Master + survey | string(255) | No | No | Yes | N/A | |
| father_name | Master + survey | string(255) | No | No | Yes | N/A | "-" is genuine source sentinel |
| cnic_no | Field survey | string(15) | Yes | No | Yes | Hidden | CNIC number |
| khasra_number | Field survey | string(64) | Yes | No | Yes | Hidden | Khasra/plot number (Phase 10A: added to parcels for search) |
| mauza_number | Field survey | string(64) | Yes | No | Yes | Hidden | Mauza/revenue village number (Phase 10A: added to parcels for search) |
| contact_number | Field survey | string(20) | Yes | No | Yes | Hidden | |
| land_owner_doc | Master + survey | string(128) | No | No | Yes | N/A | Ownership documents |
| electricity_connection_name | Field survey | string(128) | Yes | No | Yes | Hidden | |
| land_area | Field survey | number | Yes | No | Yes | Hidden | |
| structure_status | Master + survey | string(64) | No | No | Yes | N/A | |
| structure_name | Master + survey | string(255) | No | No | Yes | N/A | |
| length_ft | Master + survey | number | Yes | No | Yes | Hidden | |
| width_ft | Master + survey | number | Yes | No | Yes | Hidden | |
| area_sqft | Master + survey | number | Yes | No | Yes | Hidden | Primary measurement |
| construction_nature | Master + survey | string(32) | No | No | Yes | N/A | |

---

## System/Protected Fields (NEVER editable by client)

| Field | Location | Why Protected |
|---|---|---|
| id | revision response | Database primary key |
| revision_no | revision response | Server-generated sequential |
| changed_by | revision response | Server-derived from JWT |
| changed_at | revision response | Server-generated timestamp |
| accepted_at | revision response | Server-generated timestamp |
| status | revision response | Server-controlled lifecycle |
| client_uuid | revision request | Idempotency key (client sends, server uses for dedup) |
| parent_revision_no | revision request | Optimistic locking reference |
| base_master_row_ids | revision response | Server-derived provenance |
| parcel_code | URL path | Server-derived from URL |
| source_nid | parcel response | Immutable provenance |
| database IDs | various | Never secret, never editable |

---

## NULL Handling Rules

1. **NULL in master = field was not captured in original Excel.** Never display as "N/A", "-", or "Unknown" unless the source genuinely contains that string.
2. **Explicit null in data payload** = user intentionally cleared a value. Serializer `allow_null=True` supports this.
3. **NULL → value** produces diff `{"old": null, "new": "value"}`
4. **Value → NULL** produces diff `{"old": "value", "new": null}`
5. **Empty string "" ≠ NULL.** Empty string is a valid value. NULL means "not captured."
6. **"-" is a genuine source sentinel** from the Excel — do not treat as NULL.
