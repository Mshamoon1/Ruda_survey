"""User role profile for API authorisation.

Roles (Phase 4 brief): SURVEYOR < SUPERVISOR < ADMIN.
A profile is auto-created for every user with the least-privileged role.
"""
from django.conf import settings
from django.db import models


class UserRole(models.TextChoices):
    SURVEYOR = "SURVEYOR", "Surveyor"
    SUPERVISOR = "SUPERVISOR", "Supervisor"
    ADMIN = "ADMIN", "Admin"


ROLE_RANK = {
    UserRole.SURVEYOR: 1,
    UserRole.SUPERVISOR: 2,
    UserRole.ADMIN: 3,
}


class UserProfile(models.Model):
    user = models.OneToOneField(
        settings.AUTH_USER_MODEL,
        on_delete=models.PROTECT,
        related_name="survey_profile",
    )
    role = models.CharField(
        max_length=16,
        choices=UserRole.choices,
        default=UserRole.SURVEYOR,
    )
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "user_profiles"

    def __str__(self):
        return f"{self.user.username}:{self.role}"

    @property
    def rank(self) -> int:
        return ROLE_RANK.get(self.role, 0)
