"""Auto-create a UserProfile (role SURVEYOR by default) for every user."""
from django.conf import settings
from django.db.models.signals import post_save
from django.dispatch import receiver

from .models import UserProfile, UserRole


@receiver(post_save, sender=settings.AUTH_USER_MODEL)
def ensure_user_profile(sender, instance, created, **kwargs):
    if created:
        UserProfile.objects.get_or_create(
            user=instance,
            defaults={"role": UserRole.SURVEYOR},
        )
