"""
ORM-level immutability guards.

These are the *application-layer* defence. The authoritative enforcement is
database-level (see migration 0002: PostgreSQL triggers). This module makes
accidental mutation impossible through normal Django code paths on any backend,
and keeps tests meaningful even when run against a non-PostgreSQL database.

Guarantees provided here:
* ImmutableModel        — INSERT-only model. save() only allowed for new rows,
                          delete()/queryset.delete()/update()/bulk_update() raise.
* SurveyChange          — append-only with a narrow, explicit lifecycle channel
                          (SurveyChange.transition_status) that is the ONLY way
                          to change status/accepted_at after insert.
"""
from django.db import models
from django.utils import timezone


class ImmutableRecordError(RuntimeError):
    """Raised when code attempts to mutate an immutable record."""


class ImmutableQuerySet(models.QuerySet):
    def update(self, **kwargs):
        raise ImmutableRecordError(
            f"{self.model.__name__} is immutable: UPDATE is forbidden "
            f"(database-level trigger also enforces this)."
        )

    def delete(self):
        raise ImmutableRecordError(
            f"{self.model.__name__} is immutable: DELETE is forbidden "
            f"(database-level trigger also enforces this)."
        )

    def bulk_update(self, objs, fields, batch_size=None, **kwargs):
        raise ImmutableRecordError(f"{self.model.__name__} is immutable: bulk_update is forbidden.")


ImmutableManager = models.Manager.from_queryset(ImmutableQuerySet)


class ImmutableModel(models.Model):
    """Abstract base for INSERT-only tables (survey_master, audit_logs).

    bulk_create() intentionally remains available: the Phase 3 Excel import
    inserts rows in bulk but never mutates them afterwards.
    """

    objects = ImmutableManager()

    class Meta:
        abstract = True

    def save(self, *args, **kwargs):
        if kwargs.get("force_update"):
            raise ImmutableRecordError(f"{type(self).__name__} is immutable: force_update is forbidden.")
        if not self._state.adding:
            raise ImmutableRecordError(
                f"{type(self).__name__} is immutable: existing rows cannot be re-saved."
            )
        kwargs["force_insert"] = True
        return super().save(*args, **kwargs)

    def delete(self, using=None, keep_parents=False):
        raise ImmutableRecordError(f"{type(self).__name__} is immutable: DELETE is forbidden.")


# Lifecycle fields that may legally change on a revision after insertion.
CHANGE_LIFECYCLE_FIELDS = frozenset({"status", "accepted_at"})

# Allowed status transitions (draft lives client-side; server accepts into
# submitted/synced; rejected is terminal).
STATUS_TRANSITIONS = {
    "draft": {"submitted", "rejected"},
    "submitted": {"synced", "rejected"},
    "synced": set(),
    "rejected": set(),
}


class ChangeQuerySet(ImmutableQuerySet):
    def limited_update(self, **kwargs):
        illegal = set(kwargs) - CHANGE_LIFECYCLE_FIELDS
        if illegal:
            raise ImmutableRecordError(
                f"Only {sorted(CHANGE_LIFECYCLE_FIELDS)} may change on SurveyChange; got {sorted(illegal)}."
            )
        return models.QuerySet.update(self, **kwargs)


class ChangeManager(models.Manager.from_queryset(ChangeQuerySet)):
    pass


class AppendOnlyModel(ImmutableModel):
    """Abstract base for survey_changes: insert-only + narrow lifecycle updates."""

    objects = ChangeManager()

    class Meta:
        abstract = True

    def save(self, *args, **kwargs):
        if not self._state.adding:
            raise ImmutableRecordError(
                "SurveyChange is immutable: use transition_status() for the "
                "only permitted post-insert change (status/accepted_at)."
            )
        return super().save(*args, **kwargs)

    def transition_status(self, new_status, accepted_at=None):
        """Move this revision through the status state machine.

        Returns the number of rows updated (0 or 1). Refreshes self.
        """
        if new_status not in STATUS_TRANSITIONS:
            raise ValueError(f"Unknown status {new_status!r}.")
        if new_status not in STATUS_TRANSITIONS[self.status]:
            raise ImmutableRecordError(
                f"Illegal transition {self.status!r} -> {new_status!r}."
            )
        if new_status in ("submitted", "synced") and accepted_at is None:
            accepted_at = timezone.now()
        updated = type(self).objects.filter(pk=self.pk).limited_update(
            status=new_status, accepted_at=accepted_at
        )
        self.refresh_from_db()
        return updated
