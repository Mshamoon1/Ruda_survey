"""TEST GROUP H — audit_logs: append-only at ORM and database level."""
from django.db import DatabaseError, connection, transaction
from django.test import TestCase
from django.utils import timezone

from surveys.models import AuditLog
from surveys.tests import base
from surveys.tests.base import requires_postgresql


class AuditLogTests(TestCase):
    def setUp(self):
        self.user = base.make_user()
        self.parcel = base.make_parcel()
        self.entry = AuditLog.objects.create(
            user=self.user,
            parcel=self.parcel,
            action="REVISION_CREATED",
            details={"revision_no": 1},
            device_info={"model": "Pixel 8", "os": "14"},
            ip_address="10.1.2.3",
        )

    def test_h1_audit_log_created_with_metadata(self):
        self.entry.refresh_from_db()
        self.assertEqual(self.entry.action, "REVISION_CREATED")
        self.assertEqual(self.entry.details, {"revision_no": 1})
        self.assertEqual(self.entry.device_info["model"], "Pixel 8")
        self.assertEqual(str(self.entry.ip_address), "10.1.2.3")

    def test_h2_system_event_allows_null_user_and_parcel(self):
        e = AuditLog.objects.create(action="IMPORT_COMPLETED")
        self.assertIsNone(e.user)
        self.assertIsNone(e.parcel)

    def test_h3_orm_update_blocked(self):
        with self.assertRaises(RuntimeError):
            self.entry.action = "TAMPERED"
            self.entry.save()

    def test_h4_orm_queryset_update_blocked(self):
        with self.assertRaises(RuntimeError):
            AuditLog.objects.update(action="TAMPERED")

    def test_h5_orm_delete_blocked(self):
        with self.assertRaises(RuntimeError):
            self.entry.delete()

    @requires_postgresql
    def test_h6_db_update_blocked_by_trigger(self):
        with self.assertRaisesRegex(DatabaseError, "RUDA-SURVEY"), transaction.atomic():
            with connection.cursor() as cursor:
                cursor.execute("UPDATE audit_logs SET action='TAMPERED' WHERE id=%s",
                               [self.entry.pk])

    @requires_postgresql
    def test_h7_db_delete_blocked_by_trigger(self):
        with self.assertRaisesRegex(DatabaseError, "RUDA-SURVEY"), transaction.atomic():
            with connection.cursor() as cursor:
                cursor.execute("DELETE FROM audit_logs WHERE id=%s", [self.entry.pk])

    def test_h8_original_information_intact_after_attempts(self):
        self.entry.refresh_from_db()
        self.assertEqual(self.entry.action, "REVISION_CREATED")
        self.assertTrue(AuditLog.objects.filter(pk=self.entry.pk).exists())

    def test_h9_correlation_id_queryable(self):
        import uuid as uuid_mod

        corr = uuid_mod.uuid4()
        e = AuditLog.objects.create(action="PARCEL_SEARCH", correlation_id=corr)
        found = AuditLog.objects.filter(correlation_id=corr).first()
        self.assertEqual(e.pk, found.pk)

    def test_h10_occurred_at_auto_populated(self):
        self.assertIsNotNone(self.entry.occurred_at)
