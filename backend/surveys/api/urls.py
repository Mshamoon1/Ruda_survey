"""URL routing for /api/v1/ — the only API surface."""
from django.urls import path
from rest_framework_simplejwt.views import TokenRefreshView

from . import views
from .views import ThrottledTokenRefreshView

app_name = "api"

urlpatterns = [
    # ---- auth ----------------------------------------------------------
    path("auth/login/", views.LoginView.as_view(), name="auth-login"),
    path("auth/refresh/", ThrottledTokenRefreshView.as_view(), name="auth-refresh"),
    path("auth/logout/", views.LogoutView.as_view(), name="auth-logout"),
    path("auth/me/", views.me, name="auth-me"),

    # ---- surveys -------------------------------------------------------
    path("surveys/search/", views.survey_search, name="survey-search"),
    path("surveys/search-options/", views.search_options, name="survey-search-options"),
    path("surveys/owners/", views.owner_search, name="survey-owner-search"),
    path("surveys/sr-no/<int:sr_no>/", views.sr_no_lookup, name="survey-sr-no-lookup"),
    path("surveys/parcel/<str:parcel_code>/", views.parcel_lookup,
         name="parcel-lookup"),
    path("surveys/<str:parcel_code>/original/", views.original_data,
         name="survey-original"),
    path("surveys/<str:parcel_code>/current/", views.current_data,
         name="survey-current"),
    path("surveys/<str:parcel_code>/revisions/",
         views.RevisionListCreateView.as_view(), name="survey-revisions"),
    path("surveys/<str:parcel_code>/revisions/<int:revision_no>/images/",
         views.upload_image, name="revision-image-upload"),
    path("surveys/<str:parcel_code>/revisions/<int:revision_no>/status/",
         views.change_revision_status, name="revision-status"),
    path("surveys/<str:parcel_code>/sheet/", views.survey_sheet,
         name="survey-sheet"),
    path("surveys/<str:parcel_code>/pdf/", views.survey_pdf,
         name="survey-pdf"),
    path("surveys/<str:parcel_code>/export/", views.survey_export,
         name="survey-export"),

    # ---- admin ---------------------------------------------------------
    path("admin/audit-logs/", views.AuditLogListView.as_view(),
         name="admin-audit-logs"),
    path("admin/stats/", views.admin_stats, name="admin-stats"),
]
