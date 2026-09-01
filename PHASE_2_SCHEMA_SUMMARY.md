# PHASE 2 — SCHEMA VERIFICATION SUMMARY (live PostgreSQL)

Database: `Ruda_Survey` · host localhost:5433 · PostgreSQL 18.6 · role `ruda_app`
Generated: 2026-08-25 13:20

## 1. Tables present

| table | rows(schema check) |
|---|---|
| parcels | EXISTS |
| survey_master | EXISTS |
| survey_changes | EXISTS |
| survey_images | EXISTS |
| audit_logs | EXISTS |
| import_batches | EXISTS |

## 2. Columns, types and nullability

### `parcels`

| column | type | nullable | default |
|---|---|---|---|
| id | bigint | NO |  |
| parcel_code | character varying | NO |  |
| source_nid | bigint | YES |  |
| village | character varying | YES |  |
| tehsil | character varying | YES |  |
| district | character varying | YES |  |
| owner_name_current | character varying | YES |  |
| created_at | timestamp with time zone | NO |  |
| updated_at | timestamp with time zone | NO |  |
| current_revision_id | bigint | YES |  |

### `survey_master`

| column | type | nullable | default |
|---|---|---|---|
| id | bigint | NO |  |
| source_row_number | integer | NO |  |
| sr_no | integer | NO |  |
| source_nid | bigint | YES |  |
| chainage_m | numeric | YES |  |
| affected_persons_count | integer | YES |  |
| phase_code | character varying | YES |  |
| latitude | numeric | YES |  |
| longitude | numeric | YES |  |
| project_component | character varying | NO |  |
| owner_name | character varying | NO |  |
| father_name | character varying | YES |  |
| caste | character varying | YES |  |
| village | character varying | NO |  |
| tehsil | character varying | YES |  |
| district | character varying | YES |  |
| ownership_documents | character varying | YES |  |
| structure_status | character varying | NO |  |
| structure_name | character varying | YES |  |
| structure_count | integer | YES |  |
| tenure_status | character varying | YES |  |
| length_ft | numeric | YES |  |
| width_ft | numeric | YES |  |
| area_value | numeric | YES |  |
| construction_nature | character varying | YES |  |
| unit_rate_rs | integer | YES |  |
| compensation_million | numeric | YES |  |
| impact_extent | character varying | NO |  |
| river_location | character varying | YES |  |
| in_row_yn | character varying | YES |  |
| cl_offset_m | integer | YES |  |
| extra_note | character varying | YES |  |
| is_formula_area | boolean | NO |  |
| is_formula_compensation | boolean | NO |  |
| raw_data | jsonb | NO |  |
| imported_at | timestamp with time zone | NO |  |
| import_batch_id | bigint | NO |  |
| parcel_id | bigint | NO |  |

### `survey_changes`

| column | type | nullable | default |
|---|---|---|---|
| id | bigint | NO |  |
| client_uuid | uuid | NO |  |
| revision_no | integer | NO |  |
| base_master_row_ids | ARRAY | NO |  |
| changes | jsonb | NO |  |
| full_payload | jsonb | NO |  |
| change_reason | character varying | YES |  |
| changed_at | timestamp with time zone | NO |  |
| created_at | timestamp with time zone | NO |  |
| accepted_at | timestamp with time zone | YES |  |
| status | character varying | NO |  |
| device_info | jsonb | YES |  |
| changed_by_id | integer | NO |  |
| parcel_id | bigint | NO |  |
| parent_revision_id | bigint | YES |  |

### `survey_images`

| column | type | nullable | default |
|---|---|---|---|
| id | bigint | NO |  |
| image_type | character varying | NO |  |
| file_path | text | NO |  |
| original_filename | character varying | YES |  |
| content_type | character varying | YES |  |
| file_size | bigint | YES |  |
| checksum_sha256 | character varying | NO |  |
| width_px | integer | YES |  |
| height_px | integer | YES |  |
| captured_at | timestamp with time zone | YES |  |
| uploaded_at | timestamp with time zone | NO |  |
| client_uuid | uuid | NO |  |
| sync_status | character varying | NO |  |
| parcel_id | bigint | NO |  |
| revision_id | bigint | NO |  |
| uploaded_by_id | integer | YES |  |

### `audit_logs`

| column | type | nullable | default |
|---|---|---|---|
| id | bigint | NO |  |
| occurred_at | timestamp with time zone | NO |  |
| action | character varying | NO |  |
| entity_type | character varying | YES |  |
| entity_id | bigint | YES |  |
| correlation_id | uuid | YES |  |
| details | jsonb | YES |  |
| device_info | jsonb | YES |  |
| ip_address | inet | YES |  |
| user_id | integer | YES |  |
| parcel_id | bigint | YES |  |
| revision_id | bigint | YES |  |

### `import_batches`

| column | type | nullable | default |
|---|---|---|---|
| id | bigint | NO |  |
| source_filename | character varying | NO |  |
| file_checksum | character varying | NO |  |
| sheet_name | character varying | YES |  |
| total_rows | integer | NO |  |
| successful_rows | integer | NO |  |
| failed_rows | integer | NO |  |
| warning_count | integer | NO |  |
| status | character varying | NO |  |
| error_summary | jsonb | YES |  |
| imported_at | timestamp with time zone | NO |  |
| imported_by_id | integer | YES |  |

## 3. Constraints (PK / FK / UNIQUE / CHECK)

### `parcels`

| name | type | definition |
|---|---|---|
| ck_parcels_code_nonempty | CHECK | CHECK ((NOT ((parcel_code)::text = ''::text))) |
| parcels_current_revision_id_84028aca_fk_survey_changes_id | FOREIGN KEY | FOREIGN KEY (current_revision_id) REFERENCES survey_changes(id) DEFERRABLE INITIALLY DEFERRED |
| parcels_created_at_not_null | n | NOT NULL created_at |
| parcels_id_not_null | n | NOT NULL id |
| parcels_parcel_code_not_null | n | NOT NULL parcel_code |
| parcels_updated_at_not_null | n | NOT NULL updated_at |
| parcels_pkey | PRIMARY KEY | PRIMARY KEY (id) |
| parcels_parcel_code_key | UNIQUE | UNIQUE (parcel_code) |

### `survey_master`

| name | type | definition |
|---|---|---|
| ck_master_source_row_positive | CHECK | CHECK ((source_row_number > 0)) |
| ck_master_sr_no_positive | CHECK | CHECK ((sr_no > 0)) |
| survey_master_source_row_number_check | CHECK | CHECK ((source_row_number >= 0)) |
| survey_master_sr_no_check | CHECK | CHECK ((sr_no >= 0)) |
| survey_master_import_batch_id_74ac4ef5_fk_import_batches_id | FOREIGN KEY | FOREIGN KEY (import_batch_id) REFERENCES import_batches(id) DEFERRABLE INITIALLY DEFERRED |
| survey_master_parcel_id_4dff5f96_fk_parcels_id | FOREIGN KEY | FOREIGN KEY (parcel_id) REFERENCES parcels(id) DEFERRABLE INITIALLY DEFERRED |
| survey_master_id_not_null | n | NOT NULL id |
| survey_master_impact_extent_not_null | n | NOT NULL impact_extent |
| survey_master_import_batch_id_not_null | n | NOT NULL import_batch_id |
| survey_master_imported_at_not_null | n | NOT NULL imported_at |
| survey_master_is_formula_area_not_null | n | NOT NULL is_formula_area |
| survey_master_is_formula_compensation_not_null | n | NOT NULL is_formula_compensation |
| survey_master_owner_name_not_null | n | NOT NULL owner_name |
| survey_master_parcel_id_not_null | n | NOT NULL parcel_id |
| survey_master_project_component_not_null | n | NOT NULL project_component |
| survey_master_raw_data_not_null | n | NOT NULL raw_data |
| survey_master_source_row_number_not_null | n | NOT NULL source_row_number |
| survey_master_sr_no_not_null | n | NOT NULL sr_no |
| survey_master_structure_status_not_null | n | NOT NULL structure_status |
| survey_master_village_not_null | n | NOT NULL village |
| survey_master_pkey | PRIMARY KEY | PRIMARY KEY (id) |
| uq_survey_master_batch_source_row | UNIQUE | UNIQUE (import_batch_id, source_row_number) |

### `survey_changes`

| name | type | definition |
|---|---|---|
| ck_survey_changes_revision_no_positive | CHECK | CHECK ((revision_no > 0)) |
| ck_survey_changes_status_valid | CHECK | CHECK (((status)::text = ANY ((ARRAY['draft'::character varying, 'submitted'::character varying, 'synced'::character varying, 'rejected'::character varying])::text[]))) |
| survey_changes_revision_no_check | CHECK | CHECK ((revision_no >= 0)) |
| survey_changes_changed_by_id_64ae8a07_fk_auth_user_id | FOREIGN KEY | FOREIGN KEY (changed_by_id) REFERENCES auth_user(id) DEFERRABLE INITIALLY DEFERRED |
| survey_changes_parcel_id_1878ae7b_fk_parcels_id | FOREIGN KEY | FOREIGN KEY (parcel_id) REFERENCES parcels(id) DEFERRABLE INITIALLY DEFERRED |
| survey_changes_parent_revision_id_66cabc88_fk_survey_changes_id | FOREIGN KEY | FOREIGN KEY (parent_revision_id) REFERENCES survey_changes(id) DEFERRABLE INITIALLY DEFERRED |
| survey_changes_base_master_row_ids_not_null | n | NOT NULL base_master_row_ids |
| survey_changes_changed_at_not_null | n | NOT NULL changed_at |
| survey_changes_changed_by_id_not_null | n | NOT NULL changed_by_id |
| survey_changes_changes_not_null | n | NOT NULL changes |
| survey_changes_client_uuid_not_null | n | NOT NULL client_uuid |
| survey_changes_created_at_not_null | n | NOT NULL created_at |
| survey_changes_full_payload_not_null | n | NOT NULL full_payload |
| survey_changes_id_not_null | n | NOT NULL id |
| survey_changes_parcel_id_not_null | n | NOT NULL parcel_id |
| survey_changes_revision_no_not_null | n | NOT NULL revision_no |
| survey_changes_status_not_null | n | NOT NULL status |
| survey_changes_pkey | PRIMARY KEY | PRIMARY KEY (id) |
| survey_changes_client_uuid_key | UNIQUE | UNIQUE (client_uuid) |
| uq_survey_changes_parcel_revision_no | UNIQUE | UNIQUE (parcel_id, revision_no) |

### `survey_images`

| name | type | definition |
|---|---|---|
| ck_survey_images_size_nonnegative | CHECK | CHECK (((file_size IS NULL) OR (file_size >= 0))) |
| ck_survey_images_sync_status_valid | CHECK | CHECK (((sync_status)::text = ANY ((ARRAY['pending'::character varying, 'synced'::character varying])::text[]))) |
| ck_survey_images_type_valid | CHECK | CHECK (((image_type)::text = ANY ((ARRAY['FRONT'::character varying, 'SECOND'::character varying])::text[]))) |
| survey_images_height_px_check | CHECK | CHECK ((height_px >= 0)) |
| survey_images_width_px_check | CHECK | CHECK ((width_px >= 0)) |
| survey_images_parcel_id_4783f4af_fk_parcels_id | FOREIGN KEY | FOREIGN KEY (parcel_id) REFERENCES parcels(id) DEFERRABLE INITIALLY DEFERRED |
| survey_images_revision_id_df6883e1_fk_survey_changes_id | FOREIGN KEY | FOREIGN KEY (revision_id) REFERENCES survey_changes(id) DEFERRABLE INITIALLY DEFERRED |
| survey_images_uploaded_by_id_1c303b21_fk_auth_user_id | FOREIGN KEY | FOREIGN KEY (uploaded_by_id) REFERENCES auth_user(id) DEFERRABLE INITIALLY DEFERRED |
| survey_images_checksum_sha256_not_null | n | NOT NULL checksum_sha256 |
| survey_images_client_uuid_not_null | n | NOT NULL client_uuid |
| survey_images_file_path_not_null | n | NOT NULL file_path |
| survey_images_id_not_null | n | NOT NULL id |
| survey_images_image_type_not_null | n | NOT NULL image_type |
| survey_images_parcel_id_not_null | n | NOT NULL parcel_id |
| survey_images_revision_id_not_null | n | NOT NULL revision_id |
| survey_images_sync_status_not_null | n | NOT NULL sync_status |
| survey_images_uploaded_at_not_null | n | NOT NULL uploaded_at |
| survey_images_pkey | PRIMARY KEY | PRIMARY KEY (id) |
| survey_images_client_uuid_key | UNIQUE | UNIQUE (client_uuid) |
| uq_survey_images_revision_type | UNIQUE | UNIQUE (revision_id, image_type) |

### `audit_logs`

| name | type | definition |
|---|---|---|
| audit_logs_parcel_id_5c7ffc04_fk_parcels_id | FOREIGN KEY | FOREIGN KEY (parcel_id) REFERENCES parcels(id) DEFERRABLE INITIALLY DEFERRED |
| audit_logs_revision_id_1057bbfb_fk_survey_changes_id | FOREIGN KEY | FOREIGN KEY (revision_id) REFERENCES survey_changes(id) DEFERRABLE INITIALLY DEFERRED |
| audit_logs_user_id_752b0e2b_fk_auth_user_id | FOREIGN KEY | FOREIGN KEY (user_id) REFERENCES auth_user(id) DEFERRABLE INITIALLY DEFERRED |
| audit_logs_action_not_null | n | NOT NULL action |
| audit_logs_id_not_null | n | NOT NULL id |
| audit_logs_occurred_at_not_null | n | NOT NULL occurred_at |
| audit_logs_pkey | PRIMARY KEY | PRIMARY KEY (id) |

### `import_batches`

| name | type | definition |
|---|---|---|
| ck_import_batches_status_valid | CHECK | CHECK (((status)::text = ANY ((ARRAY['pending'::character varying, 'running'::character varying, 'completed'::character varying, 'failed'::character varying])::text[]))) |
| import_batches_failed_rows_check | CHECK | CHECK ((failed_rows >= 0)) |
| import_batches_successful_rows_check | CHECK | CHECK ((successful_rows >= 0)) |
| import_batches_total_rows_check | CHECK | CHECK ((total_rows >= 0)) |
| import_batches_warning_count_check | CHECK | CHECK ((warning_count >= 0)) |
| import_batches_imported_by_id_52313233_fk_auth_user_id | FOREIGN KEY | FOREIGN KEY (imported_by_id) REFERENCES auth_user(id) DEFERRABLE INITIALLY DEFERRED |
| import_batches_failed_rows_not_null | n | NOT NULL failed_rows |
| import_batches_file_checksum_not_null | n | NOT NULL file_checksum |
| import_batches_id_not_null | n | NOT NULL id |
| import_batches_imported_at_not_null | n | NOT NULL imported_at |
| import_batches_source_filename_not_null | n | NOT NULL source_filename |
| import_batches_status_not_null | n | NOT NULL status |
| import_batches_successful_rows_not_null | n | NOT NULL successful_rows |
| import_batches_total_rows_not_null | n | NOT NULL total_rows |
| import_batches_warning_count_not_null | n | NOT NULL warning_count |
| import_batches_pkey | PRIMARY KEY | PRIMARY KEY (id) |

## 4. Indexes

### `parcels`

| index name | columns | unique |
|---|---|---|
| parcels_current_revision_id_84028aca | current_revision_id | no |
| parcels_parcel_code_996cfab2_like | parcel_code | no |
| parcels_parcel_code_key | parcel_code | yes |
| parcels_pkey | id | yes |
| parcels_source_nid_7d523f66 | source_nid | no |

### `survey_master`

| index name | columns | unique |
|---|---|---|
| idx_master_struct_status | structure_status | no |
| idx_master_village | village | no |
| survey_master_import_batch_id_74ac4ef5 | import_batch_id | no |
| survey_master_parcel_id_4dff5f96 | parcel_id | no |
| survey_master_pkey | id | yes |
| survey_master_source_nid_7887bea9 | source_nid | no |
| uq_survey_master_batch_source_row | source_row_number, import_batch_id | yes |

### `survey_changes`

| index name | columns | unique |
|---|---|---|
| idx_changes_changed_at | changed_at | no |
| survey_changes_changed_by_id_64ae8a07 | changed_by_id | no |
| survey_changes_client_uuid_key | client_uuid | yes |
| survey_changes_parcel_id_1878ae7b | parcel_id | no |
| survey_changes_parent_revision_id_66cabc88 | parent_revision_id | no |
| survey_changes_pkey | id | yes |
| survey_changes_status_bb4d308a | status | no |
| survey_changes_status_bb4d308a_like | status | no |
| uq_survey_changes_parcel_revision_no | revision_no, parcel_id | yes |

### `survey_images`

| index name | columns | unique |
|---|---|---|
| survey_images_checksum_sha256_3a979ad4 | checksum_sha256 | no |
| survey_images_checksum_sha256_3a979ad4_like | checksum_sha256 | no |
| survey_images_client_uuid_key | client_uuid | yes |
| survey_images_parcel_id_4783f4af | parcel_id | no |
| survey_images_pkey | id | yes |
| survey_images_revision_id_df6883e1 | revision_id | no |
| survey_images_uploaded_by_id_1c303b21 | uploaded_by_id | no |
| uq_survey_images_revision_type | image_type, revision_id | yes |

### `audit_logs`

| index name | columns | unique |
|---|---|---|
| audit_logs_correlation_id_0f7a4c87 | correlation_id | no |
| audit_logs_occurred_at_01074287 | occurred_at | no |
| audit_logs_parcel_id_5c7ffc04 | parcel_id | no |
| audit_logs_pkey | id | yes |
| audit_logs_revision_id_1057bbfb | revision_id | no |
| audit_logs_user_id_752b0e2b | user_id | no |
| idx_audit_action | action | no |
| idx_audit_parcel_time | occurred_at, parcel_id | no |
| idx_audit_user_time | occurred_at, user_id | no |

### `import_batches`

| index name | columns | unique |
|---|---|---|
| import_batches_file_checksum_41af2b46 | file_checksum | no |
| import_batches_file_checksum_41af2b46_like | file_checksum | no |
| import_batches_imported_by_id_52313233 | imported_by_id | no |
| import_batches_pkey | id | yes |

## 5. Immutability triggers

| table | trigger | event | function | enabled |
|---|---|---|---|---|
| audit_logs | trg_audit_logs_immutable | — | surveys_block_all_mutation() | enabled |
| parcels | trg_parcels_no_delete | — | surveys_block_all_mutation() | enabled |
| survey_changes | trg_survey_changes_appendonly | — | surveys_changes_lifecycle_guard() | enabled |
| survey_master | trg_survey_master_immutable | — | surveys_block_all_mutation() | enabled |

## 6. Trigger functions

- `surveys_block_all_mutation()` → present
- `surveys_changes_lifecycle_guard()` → present
