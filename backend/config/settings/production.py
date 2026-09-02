from .base import *

DEBUG = False

ALLOWED_HOSTS = [
    host.strip()
    for host in os.environ.get(
        "RUDA_ALLOWED_HOSTS",
        "ruda-survey.nespakprogresscenter.com,localhost,127.0.0.1",
    ).split(",")
    if host.strip()
]

# ------------------------------------------------------------
# PostgreSQL
# ------------------------------------------------------------
DATABASES = {
    "default": {
        "ENGINE": "django.db.backends.postgresql",
        "NAME": os.environ.get("RUDA_DB_NAME", "Ruda_Survey"),
        "USER": os.environ.get("RUDA_DB_USER", "postgres"),
        "PASSWORD": os.environ.get("RUDA_DB_PASSWORD", ""),
        "HOST": os.environ.get("RUDA_DB_HOST", "10.1.10.1"),
        "PORT": os.environ.get("RUDA_DB_PORT", "5432"),
        "CONN_MAX_AGE": 60,
    }
}

# ------------------------------------------------------------
# CSRF
# ------------------------------------------------------------
CSRF_TRUSTED_ORIGINS = [
    "http://ruda-survey.nespakprogresscenter.com",
    "https://ruda-survey.nespakprogresscenter.com",
]

# ------------------------------------------------------------
# Static files
# ------------------------------------------------------------
STATIC_URL = "/static/"
STATIC_ROOT = BASE_DIR / "staticfiles"

# ------------------------------------------------------------
# Media files
# ------------------------------------------------------------
MEDIA_URL = "/media/"
MEDIA_ROOT = BASE_DIR / "media"

# ------------------------------------------------------------
# Security
# ------------------------------------------------------------
SESSION_COOKIE_SECURE = True
CSRF_COOKIE_SECURE = True

X_FRAME_OPTIONS = "DENY"

SECURE_CONTENT_TYPE_NOSNIFF = True
SECURE_REFERRER_POLICY = "strict-origin-when-cross-origin"

# If HTTPS is terminated by Nginx:
SECURE_PROXY_SSL_HEADER = ("HTTP_X_FORWARDED_PROTO", "https")