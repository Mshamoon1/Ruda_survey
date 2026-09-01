from .base import *  # noqa: F401,F403

DEBUG = False
CONN_MAX_AGE = 0  # test runner: no persistent connections
# Speed up password hashing in tests only.
PASSWORD_HASHERS = ["django.contrib.auth.hashers.MD5PasswordHasher"]
