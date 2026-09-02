"""Explicit DRF serializers — no blind model exposure.

Protected/server-derived fields (id, created_at, changed_by, revision_no,
accepted_at, status, client_uuid…) are NEVER writable through the API.
"""
from django.contrib.auth import get_user_model
from rest_framework import serializers

from surveys.models import (
    AuditLog,
    Parcel,
    SurveyChange,
    SurveyImage,
    SurveyMaster,
    UserProfile,
)
from surveys.models.user_profile import UserRole

User = get_user_model()

# ---- auth -------------------------------------------------------------------


class RoleSerializer(serializers.ModelSerializer):
    class Meta:
        model = UserProfile
        fields = ("role",)


class UserSerializer(serializers.ModelSerializer):
    role = serializers.CharField(source="survey_profile.role", read_only=True)

    class Meta:
        model = User
        fields = ("id", "username", "first_name", "last_name", "role")


class LoginSerializer(serializers.Serializer):
    username = serializers.CharField(max_length=150, trim_whitespace=True)
    password = serializers.CharField(max_length=128, trim_whitespace=False,
                                     write_only=True)


class UserCreateSerializer(serializers.Serializer):
    """POST /api/v1/auth/users/ — admin-only user creation."""

    username = serializers.CharField(max_length=150, trim_whitespace=True)
    password = serializers.CharField(max_length=128, trim_whitespace=False,
                                     write_only=True)
    first_name = serializers.CharField(max_length=150, required=False,
                                       default="", trim_whitespace=True)
    last_name = serializers.CharField(max_length=150, required=False,
                                      default="", trim_whitespace=True)
    email = serializers.EmailField(required=False, default="")
    role = serializers.ChoiceField(choices=UserRole.choices,
                                   default=UserRole.SURVEYOR)

    def validate_username(self, value):
        if User.objects.filter(username=value).exists():
            raise serializers.ValidationError("A user with this username already exists.")
        return value

    def validate_email(self, value):
        if value and User.objects.filter(email=value).exists():
            raise serializers.ValidationError("A user with this email already exists.")
        return value

    def create(self, validated_data):
        password = validated_data.pop("password")
        role = validated_data.pop("role", UserRole.SURVEYOR)
        user = User(**validated_data)
        user.set_password(password)
        user.save()
        UserProfile.objects.filter(user=user).update(role=role)
        user.refresh_from_db()
        return user


class UserCreateResponseSerializer(serializers.ModelSerializer):
    """Safe response — never exposes password."""

    role = serializers.CharField(source="survey_profile.role", read_only=True)

    class Meta:
        model = User
        fields = ("id", "username", "first_name", "last_name", "email",
                  "role", "is_active", "date_joined")


# ---- parcel / master --------------------------------------------------------


class ParcelHeaderSerializer(serializers.ModelSerializer):
    class Meta:
        model = Parcel
        fields = (
            "parcel_code", "source_nid", "village", "tehsil", "district",
            "owner_name_current", "khasra_number", "mauza_number",
        )


class MasterLineSerializer(serializers.ModelSerializer):
    """Immutable original line-item. raw_data is intentionally NOT exposed."""

    class Meta:
        model = SurveyMaster
        fields = (
            # provenance
            "sr_no", "source_row_number", "source_nid",
            "is_formula_area", "is_formula_compensation",
            # mapped data fields (doc 02 Part D)
            "chainage_m", "affected_persons_count", "phase_code",
            "latitude", "longitude", "project_component", "owner_name",
            "father_name", "caste", "village", "tehsil", "district",
            "ownership_documents", "structure_status", "structure_name",
            "structure_count", "tenure_status", "length_ft", "width_ft",
            "area_value", "construction_nature", "unit_rate_rs",
            "compensation_million", "impact_extent", "river_location",
            "in_row_yn", "cl_offset_m", "extra_note",
        )


class OriginalResponseSerializer(serializers.Serializer):
    parcel = ParcelHeaderSerializer()
    import_batch = serializers.DictField()
    lines = MasterLineSerializer(many=True)


# ---- revisions --------------------------------------------------------------


class RevisionImageSerializer(serializers.ModelSerializer):
    class Meta:
        model = SurveyImage
        fields = ("image_type", "checksum_sha256", "file_path",
                  "content_type", "file_size", "uploaded_at",
                  "latitude", "longitude", "accuracy",
                  "area_name", "point_id", "sequence_no")


class RevisionListSerializer(serializers.ModelSerializer):
    changed_by = serializers.CharField(source="changed_by.username")
    images = RevisionImageSerializer(many=True, read_only=True)

    class Meta:
        model = SurveyChange
        fields = (
            "revision_no", "status", "changed_by", "changed_at", "created_at",
            "accepted_at", "change_reason", "client_uuid", "changes",
            "images",
        )


class RevisionCreateSerializer(serializers.Serializer):
    """Request body for POST /surveys/{code}/revisions/."""

    client_uuid = serializers.UUIDField()
    # explicit nulls are REQUIRED for value->NULL transitions (Phase 4 brief)
    data = serializers.DictField(
        child=serializers.JSONField(required=False, allow_null=True),
        allow_empty=False,
    )
    change_reason = serializers.CharField(required=False, allow_blank=True,
                                          max_length=255)
    device_info = serializers.JSONField(required=False)
    parent_revision_no = serializers.IntegerField(required=False, min_value=0)


class RevisionResponseSerializer(serializers.Serializer):
    replayed = serializers.BooleanField()
    id = serializers.IntegerField()
    revision_no = serializers.IntegerField()
    status = serializers.CharField()
    client_uuid = serializers.UUIDField()
    accepted_at = serializers.DateTimeField()
    diff = serializers.DictField()
    full_payload = serializers.DictField()


class StatusChangeSerializer(serializers.Serializer):
    target = serializers.ChoiceField(choices=["synced", "rejected"])
    reason = serializers.CharField(required=False, allow_blank=True,
                                   max_length=255)


# ---- sheet ------------------------------------------------------------------


class SheetSerializer(serializers.Serializer):
    parcel = ParcelHeaderSerializer()
    source = serializers.CharField()
    current_revision_no = serializers.IntegerField(allow_null=True)
    surveyor = serializers.CharField(allow_null=True)
    changed_at = serializers.DateTimeField(allow_null=True)
    accepted_at = serializers.DateTimeField(allow_null=True)
    images = serializers.ListField(child=RevisionImageSerializer())
    fields = serializers.DictField()          # NULL-free UI collection
    original = serializers.DictField()        # complete baseline incl. NULLs


# ---- audit ------------------------------------------------------------------


class AuditLogSerializer(serializers.ModelSerializer):
    user = serializers.CharField(source="user.username", default=None)

    class Meta:
        model = AuditLog
        fields = (
            "id", "occurred_at", "action", "user", "parcel", "revision",
            "entity_type", "entity_id", "details",
        )


# ---- additional typed contracts for function views --------------------------

class ParcelLookupSerializer(serializers.Serializer):
    parcel = ParcelHeaderSerializer()
    original = serializers.DictField()
    current_revision = serializers.DictField(allow_null=True)
    revision_no = serializers.IntegerField()
    source = serializers.CharField()


class CurrentDataSerializer(serializers.Serializer):
    parcel = serializers.DictField()
    source = serializers.CharField()
    revision_no = serializers.IntegerField()
    data = serializers.DictField()


class LogoutSerializer(serializers.Serializer):
    refresh = serializers.CharField()


class DetailSerializer(serializers.Serializer):
    detail = serializers.CharField()


class StatusResponseSerializer(serializers.Serializer):
    revision_no = serializers.IntegerField()
    status = serializers.CharField()


class ImageUploadInputSerializer(serializers.Serializer):
    image_type = serializers.ChoiceField(
        choices=["FRONT", "SECOND", "POINT_1", "POINT_2", "POINT_3", "POINT_4"],
    )
    file = serializers.FileField()
    latitude = serializers.DecimalField(
        max_digits=10, decimal_places=7, required=False, allow_null=True,
    )
    longitude = serializers.DecimalField(
        max_digits=10, decimal_places=7, required=False, allow_null=True,
    )
    accuracy = serializers.DecimalField(
        max_digits=8, decimal_places=2, required=False, allow_null=True,
    )
    area_name = serializers.CharField(max_length=255, required=False, allow_blank=True)
    point_id = serializers.CharField(max_length=32, required=False, allow_blank=True)
    sequence_no = serializers.IntegerField(required=False, min_value=1, default=1)


class ImageUploadResponseSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    image_type = serializers.CharField()
    checksum_sha256 = serializers.CharField()
    storage_key = serializers.CharField()
    content_type = serializers.CharField()
    file_size = serializers.IntegerField()
    width_px = serializers.IntegerField()
    height_px = serializers.IntegerField()
    latitude = serializers.DecimalField(max_digits=10, decimal_places=7, allow_null=True)
    longitude = serializers.DecimalField(max_digits=10, decimal_places=7, allow_null=True)
    point_id = serializers.CharField(allow_null=True)
    sequence_no = serializers.IntegerField()


class StatsSerializer(serializers.Serializer):
    stats = serializers.DictField()


class SearchResultSerializer(serializers.Serializer):
    parcel_code = serializers.CharField()
    khasra_number = serializers.CharField(allow_null=True)
    mauza_number = serializers.CharField(allow_null=True)
    owner_name = serializers.CharField(allow_null=True)
    village = serializers.CharField(allow_null=True)
    tehsil = serializers.CharField(allow_null=True)
    district = serializers.CharField(allow_null=True)
    source_nid = serializers.IntegerField(allow_null=True)


class OwnerSuggestionSerializer(serializers.Serializer):
    owner_name = serializers.CharField()
    record_count = serializers.IntegerField()


class SrNoLookupResultSerializer(serializers.Serializer):
    parcel_code = serializers.CharField()
    village = serializers.CharField(allow_null=True)
    tehsil = serializers.CharField(allow_null=True)
    district = serializers.CharField(allow_null=True)
    owner_name = serializers.CharField(allow_null=True)
    master_line_count = serializers.IntegerField()


class SrNoLookupSerializer(serializers.Serializer):
    sr_no = serializers.IntegerField()
    parcels = SrNoLookupResultSerializer(many=True)
