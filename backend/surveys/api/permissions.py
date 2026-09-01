"""Role-based permission classes for the RUDA Survey API.

Role hierarchy (Phase 4 brief):
    SURVEYOR  — read everything, create revisions, upload images
    SUPERVISOR — + review: change revision status (synced/rejected)
    ADMIN     — + audit access / administration
"""
from rest_framework.permissions import BasePermission

from surveys.models.user_profile import ROLE_RANK, UserRole

MIN_ROLE = {
    "SURVEYOR": ROLE_RANK[UserRole.SURVEYOR],
    "SUPERVISOR": ROLE_RANK[UserRole.SUPERVISOR],
    "ADMIN": ROLE_RANK[UserRole.ADMIN],
}


def user_rank(user) -> int:
    profile = getattr(user, "survey_profile", None)
    return profile.rank if profile else 0


class HasRoleAtLeast(BasePermission):
    """
    Generic role gate. Set `required_role` on the view
    (one of SURVEYOR / SUPERVISOR / ADMIN).
    """

    required_role = UserRole.SURVEYOR

    def has_permission(self, request, view):
        user = request.user
        if not (user and user.is_authenticated):
            return False
        return user_rank(user) >= MIN_ROLE.get(self.required_role, 99)


class IsSurveyorOrAbove(HasRoleAtLeast):
    required_role = UserRole.SURVEYOR


class IsSupervisorOrAbove(HasRoleAtLeast):
    required_role = UserRole.SUPERVISOR


class IsAdminRole(HasRoleAtLeast):
    required_role = UserRole.ADMIN
