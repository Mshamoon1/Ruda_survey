"""Django Admin must expose every core model as READ-ONLY inspection."""
from django.contrib import admin
from django.contrib.auth import get_user_model
from django.test import RequestFactory, TestCase

from surveys.admin import ReadOnlyAdmin
from surveys.models import AuditLog, ImportBatch, Parcel, SurveyChange, SurveyImage, SurveyMaster

User = get_user_model()

CORE_MODELS = [Parcel, SurveyMaster, SurveyChange, SurveyImage, AuditLog, ImportBatch]


class AdminReadOnlyTests(TestCase):
    def setUp(self):
        self.superuser = User.objects.create_superuser(
            username="root", password="admin-pass-123", email="root@example.com"
        )
        self.request = RequestFactory().get("/admin/")
        self.request.user = self.superuser

    def test_all_core_models_registered_readonly(self):
        for model in CORE_MODELS:
            admin_obj = admin.site._registry[model]
            self.assertIsInstance(admin_obj, ReadOnlyAdmin, model.__name__)
            self.assertFalse(admin_obj.has_add_permission(self.request), model.__name__)
            self.assertFalse(admin_obj.has_change_permission(self.request), model.__name__)
            self.assertFalse(admin_obj.has_delete_permission(self.request), model.__name__)

    def test_viewing_still_allowed_for_staff(self):
        admin_obj = admin.site._registry[SurveyMaster]
        self.assertTrue(admin_obj.has_view_permission(self.request))

    def test_change_permission_false_even_for_existing_object(self):
        from surveys.tests import base as tb

        line = tb.make_master_line()
        admin_obj = admin.site._registry[SurveyMaster]
        self.assertFalse(
            admin_obj.has_change_permission(self.request, obj=line)
        )
