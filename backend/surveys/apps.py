from django.apps import AppConfig


class SurveysConfig(AppConfig):
    default_auto_field = "django.db.models.BigAutoField"
    name = "surveys"
    verbose_name = "RUDA Survey core tables"

    def ready(self):
        from . import signals  # noqa: F401  (register post_save profile hook)
