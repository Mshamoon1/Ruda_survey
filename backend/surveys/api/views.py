"""API views — /api/v1/ surface. Read-only over immutable data by design."""
from django.contrib.auth import get_user_model
from django.db.models import Count
from rest_framework import generics, status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.exceptions import AuthenticationFailed, ValidationError
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from rest_framework.throttling import ScopedRateThrottle
from rest_framework_simplejwt.exceptions import TokenError
from rest_framework_simplejwt.tokens import RefreshToken
from rest_framework_simplejwt.views import TokenRefreshView
from drf_spectacular.utils import OpenApiTypes, extend_schema

from surveys.api.errors import ApiError, ParcelNotFound
from surveys.api.permissions import (
    IsAdminRole,
    IsSurveyorOrAbove,
    IsSupervisorOrAbove,
    user_rank,
)
from surveys.api.serializers import (
    AuditLogSerializer,
    LoginSerializer,
    OriginalResponseSerializer,
    OwnerSuggestionSerializer,
    ParcelHeaderSerializer,
    RevisionCreateSerializer,
    RevisionListSerializer,
    RevisionResponseSerializer,
    CurrentDataSerializer,
    DetailSerializer,
    ImageUploadInputSerializer,
    ImageUploadResponseSerializer,
    LogoutSerializer,
    ParcelLookupSerializer,
    SearchResultSerializer,
    SrNoLookupSerializer,
    StatsSerializer,
    StatusChangeSerializer as StatusBodySerializer,
    StatusResponseSerializer,
    SheetSerializer,
    StatusChangeSerializer,
    UserSerializer,
    UserCreateSerializer,
    UserCreateResponseSerializer,
)
from surveys.models import AuditLog, Parcel, SurveyChange, SurveyMaster
from surveys.services import audit as audit_service
from surveys.services.image_service import get_revision_or_404, store_image
from surveys.services.revision_service import (
    current_state,
    create_revision,
    master_baseline,
)
from surveys.services.sheet_service import build_sheet, generate_pdf

User = get_user_model()


def _get_parcel_or_404(parcel_code: str) -> Parcel:
    parcel = Parcel.objects.filter(parcel_code=parcel_code.strip()).first()
    if parcel is None:
        raise ParcelNotFound(details={"parcel_code": parcel_code})
    return parcel


# --------------------------------------------------------------------------- #
# AUTH
# --------------------------------------------------------------------------- #


class LoginView(generics.GenericAPIView):
    serializer_class = LoginSerializer
    permission_classes = [AllowAny]
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = "login"

    def post(self, request):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        username = serializer.validated_data["username"]
        password = serializer.validated_data["password"]

        user = User.objects.filter(username=username).first()
        if user is None or not user.check_password(password):
            audit_service.record(action="LOGIN_FAILURE", request=request,
                                 details={"username_attempted": username})
            raise AuthenticationFailed("Invalid username or password.",
                                       code="invalid_credentials")
        if not user.is_active:
            audit_service.record(action="LOGIN_FAILURE", request=request,
                                 user=None,
                                 details={"username_attempted": username,
                                          "reason": "inactive"})
            raise AuthenticationFailed("Account is disabled.",
                                       code="user_inactive")

        refresh = RefreshToken.for_user(user)
        audit_service.record(action="LOGIN_SUCCESS", user=user, request=request)
        return Response({
            "access": str(refresh.access_token),
            "refresh": str(refresh),
            "user": UserSerializer(user).data,
        })


class LogoutView(generics.GenericAPIView):
    """Blacklists the supplied refresh token (access token simply expires)."""

    permission_classes = [IsAuthenticated]
    serializer_class = LogoutSerializer

    def post(self, request):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        raw_refresh = serializer.validated_data["refresh"]
        if not raw_refresh:
            raise ValidationError({"refresh": "This field is required."})
        try:
            token = RefreshToken(raw_refresh)
            token.blacklist()
        except (TokenError, AttributeError):
            raise ApiError("Invalid or expired refresh token.",
                           code="INVALID_CREDENTIALS", status=401)
        audit_service.record(action="LOGOUT", user=request.user, request=request)
        return Response({"detail": "Logged out."})


class ThrottledTokenRefreshView(TokenRefreshView):
    permission_classes = [AllowAny]
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = "auth_refresh"

    def post(self, request, *args, **kwargs):
        try:
            return super().post(request, *args, **kwargs)
        except (TokenError, Exception) as exc:
            # SimpleJWT raises InvalidToken (an AuthenticationFailed subclass)
            # for malformed/expired/blacklisted refresh tokens.
            message = "Invalid or expired refresh token."
            if hasattr(exc, "detail"):
                try:
                    message = str(exc.detail)
                except Exception:  # pragma: no cover
                    pass
            raise ApiError(message, code="INVALID_CREDENTIALS", status=401)


@extend_schema(responses=UserSerializer)
@api_view(["GET"])
@permission_classes([IsAuthenticated])
def me(request):
    return Response(UserSerializer(request.user).data)


class CreateUserView(generics.GenericAPIView):
    """POST /api/v1/auth/users/ — admin-only user creation."""

    serializer_class = UserCreateSerializer
    permission_classes = [IsAuthenticated, IsAdminRole]

    @extend_schema(
        request=UserCreateSerializer,
        responses={201: UserCreateResponseSerializer},
        tags=["auth"],
    )
    def post(self, request):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = serializer.save()
        audit_service.record(
            action="USER_CREATED",
            user=request.user,
            entity_type="User",
            entity_id=user.pk,
            details={"created_username": user.username, "role": serializer.validated_data.get("role", "SURVEYOR")},
            request=request,
        )
        return Response(
            UserCreateResponseSerializer(user).data,
            status=status.HTTP_201_CREATED,
        )


# --------------------------------------------------------------------------- #
# SURVEYS
# --------------------------------------------------------------------------- #


@extend_schema(responses={200: ParcelLookupSerializer})
@api_view(["GET"])
def parcel_lookup(request, parcel_code):
    parcel = _get_parcel_or_404(parcel_code)
    latest = parcel.revisions.order_by("-revision_no").first()
    original_state = master_baseline(parcel)

    payload = {
        "parcel": {
            "parcel_code": parcel.parcel_code,
            "source_nid": parcel.source_nid,
            "village": parcel.village,
            "tehsil": parcel.tehsil,
            "district": parcel.district,
            "master_line_count": parcel.master_lines.count(),
            "khasra_number": parcel.khasra_number,
            "mauza_number": parcel.mauza_number,
        },
        "original": original_state,
        "current_revision": None,
        "revision_no": latest.revision_no if latest else 0,
        "source": "revision" if latest else "master",
    }
    if latest is not None:
        payload["current_revision"] = {
            "revision_no": latest.revision_no,
            "full_payload": latest.full_payload,
            "changed_fields": sorted(latest.changes.keys()),
            "status": latest.status,
        }
    return Response(payload)


@extend_schema(responses={200: OriginalResponseSerializer})
@api_view(["GET"])
def original_data(request, parcel_code):
    parcel = _get_parcel_or_404(parcel_code)
    first_line = parcel.master_lines.select_related("import_batch").first()
    batch = first_line.import_batch if first_line else None
    payload = {
        "parcel": ParcelHeaderSerializer(parcel).data,
        "import_batch": {
            "id": batch.pk,
            "file_checksum": batch.file_checksum,
            "imported_at": batch.imported_at,
        } if batch else None,
        "lines": list(parcel.master_lines.order_by("sr_no")),
    }
    serializer = OriginalResponseSerializer(payload, context={"request": request})
    return Response(serializer.data)


@extend_schema(responses={200: CurrentDataSerializer})
@api_view(["GET"])
def current_data(request, parcel_code):
    parcel = _get_parcel_or_404(parcel_code)
    state, latest = current_state(parcel)
    return Response({
        "parcel": {"parcel_code": parcel.parcel_code},
        "source": "revision" if latest else "master",
        "revision_no": latest.revision_no if latest else 0,
        "data": state,
    })


@extend_schema(request=RevisionCreateSerializer,
               responses={201: RevisionResponseSerializer, 200: RevisionResponseSerializer},
               auth=[])
class RevisionListCreateView(generics.ListCreateAPIView):
    serializer_class = RevisionListSerializer

    def get_permissions(self):
        if self.request.method == "POST":
            return [IsAuthenticated(), *self._role_permission()]
        return [IsAuthenticated()]

    def _role_permission(self):
        from surveys.api.permissions import IsSurveyorOrAbove

        return [IsSurveyorOrAbove()]

    def get_throttles(self):
        if self.request.method == "POST":
            throttle = ScopedRateThrottle()
            throttle.throttle_scope = "revision_create"
            return [throttle]
        return super().get_throttles()

    def get_queryset(self):
        parcel = _get_parcel_or_404(self.kwargs["parcel_code"])
        return (parcel.revisions.select_related("changed_by")
                .prefetch_related("images").order_by("-revision_no"))

    def list(self, request, *args, **kwargs):
        queryset = self.filter_queryset(self.get_queryset())
        page = self.paginate_queryset(queryset)
        serializer = self.get_serializer(page or queryset, many=True)
        if page is not None:
            return self.get_paginated_response(serializer.data)
        return Response(serializer.data)

    def create(self, request, *args, **kwargs):
        body = RevisionCreateSerializer(data=request.data)
        body.is_valid(raise_exception=True)
        vd = body.validated_data

        outcome = create_revision(
            parcel_code=self.kwargs["parcel_code"],
            user=request.user,
            client_uuid=vd["client_uuid"],
            data=vd["data"],
            change_reason=vd.get("change_reason"),
            device_info=vd.get("device_info"),
            parent_revision_no=vd.get("parent_revision_no"),
            request=request,
        )
        response_body = {
            "replayed": not outcome.created,
            "id": outcome.revision.pk,
            "revision_no": outcome.revision.revision_no,
            "status": outcome.revision.status,
            "client_uuid": str(outcome.revision.client_uuid),
            "accepted_at": outcome.revision.accepted_at,
            "diff": outcome.diff,
            "full_payload": outcome.revision.full_payload,
        }
        return Response(response_body,
                        status=status.HTTP_200_OK if not outcome.created
                        else status.HTTP_201_CREATED)


@extend_schema(request=StatusBodySerializer, responses={200: StatusResponseSerializer})
@api_view(["POST"])
@permission_classes([IsAuthenticated, IsSupervisorOrAbove])
def change_revision_status(request, parcel_code, revision_no):
    parcel, revision = get_revision_or_404(parcel_code, revision_no)
    body = StatusChangeSerializer(data=request.data)
    body.is_valid(raise_exception=True)
    target = body.validated_data["target"]

    try:
        revision.transition_status(target)
    except Exception as exc:
        raise ApiError(str(exc), code="INVALID_STATUS_TRANSITION") from exc

    audit_service.record(
        action="REVISION_STATUS_CHANGED",
        user=request.user,
        parcel=parcel,
        revision=revision,
        entity_type="revision",
        entity_id=revision.pk,
        details={"target": target,
                 "reason": body.validated_data.get("reason", "")},
        request=request,
    )
    return Response({"revision_no": revision.revision_no,
                     "status": revision.status})


@extend_schema(request=ImageUploadInputSerializer, responses={201: ImageUploadResponseSerializer})
@api_view(["POST"])
@permission_classes([IsAuthenticated, IsSurveyorOrAbove])
def upload_image(request, parcel_code, revision_no):
    parcel, revision = get_revision_or_404(parcel_code, revision_no)

    ALLOWED_TYPES = {"FRONT", "SECOND", "POINT_1", "POINT_2", "POINT_3", "POINT_4"}
    image_type = (request.data.get("image_type") or "").strip().upper()
    if image_type not in ALLOWED_TYPES:
        raise ApiError(
            "image_type must be FRONT, SECOND, POINT_1, POINT_2, POINT_3, or POINT_4.",
            code="INVALID_IMAGE", details={"image_type": image_type},
        )

    uploaded = request.FILES.get("file")
    if uploaded is None:
        raise ApiError("`file` is required.", code="INVALID_IMAGE")

    # GPS metadata from form fields
    latitude = request.data.get("latitude")
    longitude = request.data.get("longitude")
    accuracy = request.data.get("accuracy")
    area_name = (request.data.get("area_name") or "").strip() or None
    point_id = (request.data.get("point_id") or "").strip() or None
    sequence_no = request.data.get("sequence_no", 1)
    try:
        sequence_no = int(sequence_no)
    except (TypeError, ValueError):
        sequence_no = 1

    image = store_image(
        parcel=parcel,
        revision=revision,
        image_type=image_type,
        uploaded_file=uploaded,
        user=request.user,
        latitude=latitude,
        longitude=longitude,
        accuracy=accuracy,
        area_name=area_name,
        point_id=point_id,
        sequence_no=sequence_no,
    )
    return Response({
        "id": image.pk,
        "image_type": image.image_type,
        "checksum_sha256": image.checksum_sha256,
        "storage_key": image.file_path,
        "content_type": image.content_type,
        "file_size": image.file_size,
        "width_px": image.width_px,
        "height_px": image.height_px,
        "latitude": image.latitude,
        "longitude": image.longitude,
        "point_id": image.point_id,
        "sequence_no": image.sequence_no,
    }, status=status.HTTP_201_CREATED)


@extend_schema(responses={200: SheetSerializer})
@api_view(["GET"])
def survey_sheet(request, parcel_code):
    parcel = _get_parcel_or_404(parcel_code)
    sheet = build_sheet(parcel)
    return Response(SheetSerializer(sheet).data)


@extend_schema(responses={200: OpenApiTypes.BINARY, 503: DetailSerializer})
@api_view(["GET"])
def survey_pdf(request, parcel_code):
    _get_parcel_or_404(parcel_code)   # auth + existence first
    return generate_pdf(parcel_code)  # returns HttpResponse with PDF


@extend_schema(responses={200: OpenApiTypes.BINARY})
@api_view(["GET"])
def survey_export(request, parcel_code):
    """Export survey data as Excel file."""
    from surveys.services.export_service import generate_excel_response
    _get_parcel_or_404(parcel_code)   # auth + existence first
    return generate_excel_response(parcel_code)


@extend_schema(responses={200: SearchResultSerializer(many=True)})
@api_view(["GET"])
def survey_search(request):
    """Search parcels by village, tehsil, and/or owner_name.

    All parameters are optional but at least one must be provided.
    Results are normalized for whitespace and case-insensitive matching.
    Supports partial matching via icontains.
    """
    village = request.query_params.get("village", "").strip()
    tehsil = request.query_params.get("tehsil", "").strip()
    owner = request.query_params.get("owner_name", "").strip()
    khasra = request.query_params.get("khasra_number", "").strip()
    mauza = request.query_params.get("mauza_number", "").strip()

    if not village and not tehsil and not owner and not khasra and not mauza:
        raise ApiError(
            "At least one of village, tehsil, owner_name, khasra_number, or mauza_number is required.",
            code="VALIDATION_ERROR",
            details={"required": ["village, tehsil, owner_name, khasra_number, or mauza_number"]},
        )

    qs = Parcel.objects.all()

    if village:
        qs = qs.filter(village__iexact=village)
    if tehsil:
        qs = qs.filter(tehsil__iexact=tehsil)
    if owner:
        qs = qs.filter(owner_name_current__icontains=owner)
    if khasra:
        qs = qs.filter(khasra_number__icontains=khasra)
    if mauza:
        qs = qs.filter(mauza_number__icontains=mauza)

    # Limit results to prevent abuse
    parcels = qs[:50]

    results = []
    for parcel in parcels:
        results.append({
            "parcel_code": parcel.parcel_code,
            "khasra_number": parcel.khasra_number,
            "mauza_number": parcel.mauza_number,
            "owner_name": parcel.owner_name_current,
            "village": parcel.village,
            "tehsil": parcel.tehsil,
            "district": parcel.district,
            "source_nid": parcel.source_nid,
        })

    return Response({"results": results, "count": len(results)})


@api_view(["GET"])
@permission_classes([IsAuthenticated])
def search_options(request):
    """Return distinct villages and tehsils for dropdown population.

    Supports optional ?tehsil=X filter to return villages belonging to that tehsil.
    Values are normalized for display: trimmed, deduplicated case-insensitively.
    """
    tehsil_filter = request.query_params.get("tehsil", "").strip()

    # Get distinct villages
    village_qs = Parcel.objects.exclude(village__isnull=True).exclude(village='').values_list('village', flat=True).distinct()
    if tehsil_filter:
        village_qs = village_qs.filter(
            tehsil__iexact=tehsil_filter
        )

    # Normalize for display: trim whitespace, deduplicate case-insensitively
    village_map = {}
    for v in village_qs:
        key = v.strip().lower()
        if key not in village_map:
            village_map[key] = v.strip()
    villages = sorted(village_map.values(), key=str.casefold)

    # Get distinct tehsils
    tehsil_qs = Parcel.objects.exclude(tehsil__isnull=True).exclude(tehsil='').values_list('tehsil', flat=True).distinct()
    tehsil_map = {}
    for t in tehsil_qs:
        key = t.strip().lower()
        if key not in tehsil_map:
            tehsil_map[key] = t.strip()
    tehsils = sorted(tehsil_map.values(), key=str.casefold)

    return Response({
        "villages": villages,
        "tehsils": tehsils,
    })


OWNER_AUTOCOMPLETE_LIMIT = 15
OWNER_AUTOCOMPLETE_MIN_LENGTH = 2


@extend_schema(
    parameters=[
        {"name": "q", "type": "string", "description": "Owner name search query (min 2 chars)"},
    ],
    responses={200: OwnerSuggestionSerializer(many=True)},
)
@api_view(["GET"])
@permission_classes([IsAuthenticated])
def owner_search(request):
    """Return unique matching owner names with record counts.

    Case-insensitive partial matching via PostgreSQL ilike.
    Excludes NULL/blank owner names. Returns at most 15 suggestions.
    Minimum query length: 2 characters.
    """
    q = (request.query_params.get("q") or "").strip()

    if len(q) < OWNER_AUTOCOMPLETE_MIN_LENGTH:
        return Response({"results": []})

    suggestions = (
        Parcel.objects
        .exclude(owner_name_current__isnull=True)
        .exclude(owner_name_current="")
        .filter(owner_name_current__icontains=q)
        .values("owner_name_current")
        .annotate(record_count=Count("id"))
        .order_by("owner_name_current")[:OWNER_AUTOCOMPLETE_LIMIT]
    )

    results = [
        {"owner_name": s["owner_name_current"], "record_count": s["record_count"]}
        for s in suggestions
    ]
    return Response({"results": results})


@extend_schema(
    responses={200: SrNoLookupSerializer},
)
@api_view(["GET"])
@permission_classes([IsAuthenticated])
def sr_no_lookup(request, sr_no):
    """Lookup parcels by serial number (sr_no).

    Returns all parcels that have SurveyMaster lines with the given sr_no,
    along with the count of master lines per parcel.
    """
    try:
        sr_no_int = int(sr_no)
    except (TypeError, ValueError):
        raise ApiError(
            "Serial number must be a valid integer.",
            code="VALIDATION_ERROR",
            details={"sr_no": sr_no},
        )

    if sr_no_int <= 0:
        raise ApiError(
            "Serial number must be positive.",
            code="VALIDATION_ERROR",
            details={"sr_no": sr_no_int},
        )

    # Find all parcels that have master lines with this sr_no
    parcels_with_count = (
        SurveyMaster.objects
        .filter(sr_no=sr_no_int)
        .values("parcel__parcel_code", "parcel__village", "parcel__tehsil",
                "parcel__district", "parcel__owner_name_current")
        .annotate(master_line_count=Count("id"))
        .order_by("parcel__parcel_code")
    )

    results = []
    for entry in parcels_with_count:
        results.append({
            "parcel_code": entry["parcel__parcel_code"],
            "village": entry["parcel__village"],
            "tehsil": entry["parcel__tehsil"],
            "district": entry["parcel__district"],
            "owner_name": entry["parcel__owner_name_current"],
            "master_line_count": entry["master_line_count"],
        })

    return Response({"sr_no": sr_no_int, "parcels": results})


# --------------------------------------------------------------------------- #
# ADMIN
# --------------------------------------------------------------------------- #


class AuditLogListView(generics.ListAPIView):
    queryset = (AuditLog.objects.select_related("user", "parcel", "revision")
                .order_by("-occurred_at"))
    serializer_class = AuditLogSerializer
    permission_classes = [IsAdminRole]


@extend_schema(responses=StatsSerializer)
@api_view(["GET"])
@permission_classes([IsAuthenticated, IsAdminRole])
def admin_stats(request):
    stats = {
        "parcels": Parcel.objects.count(),
        "master_lines": SurveyMaster.objects.count(),
        "revisions": SurveyChange.objects.count(),
        "audit_entries": AuditLog.objects.count(),
        "users_by_role": list(
            User.objects.values("survey_profile__role").annotate(c=Count("id"))
        ),
    }
    return Response(stats)


@api_view(["GET"])
@permission_classes([IsAuthenticated])
def whoami_rank(request):
    return Response({"username": request.user.username,
                     "rank": user_rank(request.user)})
