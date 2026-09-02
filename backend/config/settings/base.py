"""
Base settings for the RUDA Survey backend.

Environment variables (optionally provided via backend/.env, never committed):
    RUDA_SECRET_KEY   Django secret key (REQUIRED in production)
    RUDA_DB_NAME      PostgreSQL database name (default: Ruda_Survey)
    RUDA_DB_USER      database role (default: ruda_app)
    RUDA_DB_PASSWORD  role password (required; see .env.example)
    RUDA_DB_HOST      default: localhost
    RUDA_DB_PORT      default: 5433 (local PG 18 instance chosen in Phase 2)
"""
import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent.parent  # backend/


def _load_env() -> None:
    """Minimal .env loader (KEY=VALUE lines). Process environment wins."""
    env_path = BASE_DIR / ".env"
    if not env_path.exists():
        return
    for line in env_path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip())


_load_env()

SECRET_KEY = os.environ.get("RUDA_SECRET_KEY", "")
if not SECRET_KEY:
    raise ValueError("RUDA_SECRET_KEY environment variable is required. Set it in backend/.env or your environment.")
DEBUG = False

ALLOWED_HOSTS = [h for h in os.environ.get(
    "RUDA_ALLOWED_HOSTS", "localhost,127.0.0.1,testserver").split(",") if h]

INSTALLED_APPS = [
    "django.contrib.admin",
    "django.contrib.auth",
    "django.contrib.contenttypes",
    "django.contrib.sessions",
    "django.contrib.messages",
    "django.contrib.staticfiles",
    # Third-party / platform features used by models:
    "django.contrib.postgres",  # ArrayField (survey_changes.base_master_row_ids)
    "rest_framework",
    "rest_framework_simplejwt.token_blacklist",  # server-side logout / rotation
    "drf_spectacular",
    # Project apps:
    "surveys.apps.SurveysConfig",
]

MIDDLEWARE = [
    "django.middleware.security.SecurityMiddleware",
    "django.contrib.sessions.middleware.SessionMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.contrib.auth.middleware.AuthenticationMiddleware",
    "django.contrib.messages.middleware.MessageMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
]

ROOT_URLCONF = "config.urls"

TEMPLATES = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [],
        "APP_DIRS": True,
        "OPTIONS": {
            "context_processors": [
                "django.template.context_processors.request",
                "django.contrib.auth.context_processors.auth",
                "django.contrib.messages.context_processors.messages",
            ],
        },
    },
]

WSGI_APPLICATION = "config.wsgi.application"

DATABASES = {
    "default": {
        "ENGINE": "django.db.backends.postgresql",
        "NAME": os.environ.get("RUDA_DB_NAME", "Ruda_Survey"),
        "USER": os.environ.get("RUDA_DB_USER", "postgres"),
        "PASSWORD": os.environ.get("RUDA_DB_PASSWORD", "Postgres"),
        "HOST": os.environ.get("RUDA_DB_HOST", "10.1.10.1"),
        "PORT": os.environ.get("RUDA_DB_PORT", "5432"),
        "CONN_MAX_AGE": 60,
    }
}

AUTH_PASSWORD_VALIDATORS = [
    {"NAME": "django.contrib.auth.password_validation.UserAttributeSimilarityValidator"},
    {"NAME": "django.contrib.auth.password_validation.MinimumLengthValidator"},
    {"NAME": "django.contrib.auth.password_validation.CommonPasswordValidator"},
    {"NAME": "django.contrib.auth.password_validation.NumericPasswordValidator"},
]

LANGUAGE_CODE = "en-us"
TIME_ZONE = "Asia/Karachi"
USE_I18N = True
USE_TZ = True

STATIC_URL = "static/"
MEDIA_URL = "media/"
MEDIA_ROOT = BASE_DIR / "media"

DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"

# ---------------------------------------------------------------------------
# Django REST Framework / JWT / OpenAPI
# ---------------------------------------------------------------------------
REST_FRAMEWORK = {
    "DEFAULT_AUTHENTICATION_CLASSES": (
        "rest_framework_simplejwt.authentication.JWTAuthentication",
    ),
    "DEFAULT_PERMISSION_CLASSES": (
        "rest_framework.permissions.IsAuthenticated",
    ),
    "DEFAULT_PAGINATION_CLASS": "surveys.api.pagination.DefaultPagination",
    "DEFAULT_THROTTLE_RATES": {
        "login": "30/min",
        "auth_refresh": "60/min",
        "revision_create": "120/hour",
        "image_upload": "60/hour",
    },
    "EXCEPTION_HANDLER": "surveys.api.errors.api_exception_handler",
    "DEFAULT_SCHEMA_CLASS": "drf_spectacular.openapi.AutoSchema",
}

SIMPLE_JWT = {
    "ACCESS_TOKEN_LIFETIME": __import__("datetime").timedelta(minutes=30),
    "REFRESH_TOKEN_LIFETIME": __import__("datetime").timedelta(days=7),
    "ROTATE_REFRESH_TOKENS": True,
    "BLACKLIST_AFTER_ROTATION": True,
    "AUTH_HEADER_TYPES": ("Bearer",),
    "USER_ID_FIELD": "id",
}

SPECTACULAR_SETTINGS = {
    "TITLE": "RUDA Survey API",
    "DESCRIPTION": "Immutable survey master + append-only revision workflow.",
    "VERSION": "v1",
    "SERVE_INCLUDE_SCHEMA": False,
    "COMPONENT_SPLIT_REQUEST": True,
}

# Upload safety (Phase 4): hard cap for evidence images.
DATA_UPLOAD_MAX_MEMORY_SIZE = 10 * 1024 * 1024       # 10 MB
SURVEY_IMAGE_MAX_BYTES = 10 * 1024 * 1024
