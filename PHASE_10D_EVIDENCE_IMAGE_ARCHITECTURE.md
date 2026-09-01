# PHASE_10D_EVIDENCE_IMAGE_ARCHITECTURE.md

**Date:** 2026-08-30  
**Phase:** 10D — GPS-Stamped Survey Evidence Image Architecture

## Overview

Professional GPS-stamped survey evidence image system. Captures photos with real-time GPS location, generates a visual overlay with location metadata and QR code, and uploads both original and stamped images.

## Capture Flow

```
Surveyor taps [Add Image]
    ↓
CameraFragment opens
    ↓
GPS status: "Acquiring location..." / "GPS Ready (12m)"
    ↓
Capture button → CameraX captures photo
    ↓
Location obtained from FusedLocationProvider
    ↓
Reverse geocode → area name (if available)
    ↓
QR code generated with evidence metadata
    ↓
ImageStampProcessor renders overlay onto photo
    ↓
Preview shown: [Retake] [Use Photo]
    ↓
Use Photo → PendingImage queued with all metadata
    ↓
After revision submit → flushPendingImages() → upload
```

## GPS Overlay Design

```
┌──────────────────────────────────────┐
│                                      │
│        ACTUAL SURVEY PHOTO           │
│                                      │
│                                      │
│                                      │
│  ┌────────────────────────────────┐  │
│  │ ▬▬▬▬▬▬ (accent bar)           │  │
│  │                                │  │
│  │ RUDA Survey Evidence           │  │
│  │ Date: 28/08/2026  Time: 04:32 PM│ │
│  │ Gujranwala, Punjab, Pakistan    │  │
│  │ Lat  32.268416°                │  │
│  │ Long 74.157333°                │  │
│  │ Accuracy: 12.5m                │  │
│  │ Parcel: RUDA-P14-R00005        │  │
│  │ Point: POINT_1        [QR]     │  │
│  └────────────────────────────────┘  │
└──────────────────────────────────────┘
```

## Data Model

### PendingImage (Android)
```kotlin
data class PendingImage(
    val imageType: String,          // FRONT, SECOND, POINT_1, POINT_2, etc.
    val originalBytes: ByteArray,   // Original camera capture
    val stampedBytes: ByteArray,    // GPS-stamped version
    val fileName: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    val areaName: String?,
    val capturedAt: Long,
    val pointId: String?,
    val sequenceNo: Int = 1,
    val qrPayload: String?
)
```

### SurveyImage (Backend) — new fields
```
latitude        DecimalField(10,7)  nullable
longitude       DecimalField(10,7)  nullable
accuracy        DecimalField(8,2)   nullable
area_name       CharField(255)      nullable
point_id        CharField(32)       nullable
sequence_no     PositiveIntegerField default=1
original_image_path  TextField     nullable
qr_payload      TextField           nullable
```

## QR Code Payload

```json
{
    "type": "SURVEY_EVIDENCE",
    "evidence_id": "uuid",
    "parcel": "RUDA-P14-R00005",
    "revision": 3,
    "point": "POINT_1",
    "lat": 32.268416,
    "lng": 74.157333,
    "captured_at": "2026-08-28T16:32:00+05:00"
}
```

No sensitive data (CNIC, phone, JWT) is included.

## Location Architecture

- **Provider**: FusedLocationProviderClient (Google Play Services)
- **Priority**: PRIORITY_HIGH_ACCURACY
- **Update interval**: 2000ms minimum
- **Fresh location**: 10s timeout via getMaxUpdates(1)
- **Reverse geocoding**: Android Geocoder (offline-capable)
- **Fallback**: If location unavailable, user is warned, no fake coordinates
- **Fallback for geocoding**: "Location name unavailable" — GPS coords still preserved

## Image Types

| Type | Description | Unique Constraint |
|------|-------------|-------------------|
| FRONT | General front evidence | One per revision |
| SECOND | General survey evidence | One per revision |
| POINT_N | GPS-stamped point evidence | Multiple per revision, per point+sequence |

## File Storage

- **Original**: `cacheDir/IMG_${type}_${timestamp}.jpg`
- **Stamped**: `cacheDir/STAMPED_IMG_${type}_${timestamp}.jpg`
- **Server**: `MEDIA_ROOT/surveys/{parcel}/{revision:04d}/{TYPE}_{hash[:10]}.{ext}`

## Dependencies Added

| Library | Version | Purpose |
|---------|---------|---------|
| play-services-location | 21.2.0 | FusedLocationProvider |
| ZXing core | 3.5.3 | QR code generation |

## Files Modified/Created

### Android (new)
- `utils/LocationHelper.kt` — GPS + reverse geocoding
- `utils/QrCodeGenerator.kt` — ZXing QR generation
- `utils/ImageStampProcessor.kt` — Canvas overlay rendering

### Android (modified)
- `build.gradle` — Added location + QR dependencies
- `AndroidManifest.xml` — Added location permissions
- `ui/camera/CameraFragment.kt` — Full rewrite: GPS, preview, retake/use
- `res/layout/fragment_camera.xml` — Added GPS status, preview, retake/use controls
- `ui/survey/SurveyViewModel.kt` — PendingImage type, GPS queue, flush
- `ui/survey/SurveyFormFragment.kt` — Updated image button text
- `ui/survey/ReviewFragment.kt` — Updated pending images display
- `domain/model/Models.kt` — Added PendingImage data class
- `domain/repository/SurveyRepository.kt` — Extended uploadImage signature
- `data/repository/SurveyRepositoryImpl.kt` — GPS metadata in upload
- `data/remote/SurveyApi.kt` — Extended multipart upload with GPS fields

### Backend (new)
- `migrations/0006_surveyimage_gps_fields.py`

### Backend (modified)
- `models/survey_images.py` — GPS fields, point_id, sequence_no
- `api/views.py` — GPS metadata in upload
- `api/serializers.py` — GPS fields in serializer
- `services/image_service.py` — GPS kwargs passthrough
- `services/pdf_service.py` — Actual image embedding
- `services/sheet_service.py` — GPS fields in sheet
- `config/urls.py` — MEDIA_URL serving in dev
