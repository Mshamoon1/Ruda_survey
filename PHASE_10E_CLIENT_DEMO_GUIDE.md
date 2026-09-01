# PHASE 10E — CLIENT DEMO GUIDE

## APK Installation

1. Transfer `app-debug.apk` to the Android device
2. Enable "Install from unknown sources" if prompted
3. Tap the APK file to install
4. Open "RUDA Survey" from the app drawer

## Demo Credentials

| Account | Username | Password | Role |
|---|---|---|---|
| Surveyor | DEMO001 | demo123 | SURVEYOR |
| Supervisor | DEMO002 | demo123 | SUPERVISOR |

## Important Notice

**This APK is a temporary offline demonstration build.**

- All data is stored locally on the device
- No internet connection required
- No Django backend server needed
- No PostgreSQL database needed
- Production deployment uses the Django backend

## Demo Mode Indicator

After login, the dashboard shows:
- **Welcome: DEMO001**
- **Role: Demo Mode — Local Data**

The sync section is hidden because there is no server to sync with.

## Test Flow

### 1. Login
- Enter `DEMO001` / `demo123`
- Tap Login
- Dashboard opens with "Demo Mode — Local Data"

### 2. Search
- Tap "New Survey"
- **Direct Search**: Enter parcel code (e.g., `RUDA-P14-R00005`)
- **Advanced Search**:
  - Tap Village dropdown → select from available villages
  - Tap Tehsil dropdown → select from available tehsils
  - Enter Owner Name (case-insensitive)
  - Enter Khasra Number
  - Enter Mauza Number
- Tap "Search"

### 3. View Survey
- Tap a search result to open the survey form
- View all original/master data fields
- Fields are pre-populated from the imported Excel data

### 4. Edit Survey
- Modify any editable field
- Add/change: CNIC, Contact Number, Land Area, Owner Name, etc.
- Tap "Next" to proceed to review

### 5. Add Images
- **FRONT**: Capture front photo of the structure
- **SECOND**: Capture secondary photo
- **POINT evidence**: Capture GPS-stamped point images
- Camera captures GPS coordinates automatically
- Images are stored locally

### 6. Submit Revision
- Review changes on the summary screen
- Add a change reason (optional)
- Tap "Submit"
- Revision is saved locally (not uploaded to any server)

### 7. Survey Sheet
- View the submitted survey data
- See all fields, revisions, and images

### 8. Generate PDF
- Tap "Download PDF" to save to Downloads folder
- Tap "Share PDF" to share via other apps
- PDF includes all survey data and embedded images

## Search Fields

| Field | Search Type |
|---|---|
| Parcel Code | Exact match |
| Village | Dropdown selection |
| Tehsil | Dropdown selection |
| Owner Name | Partial match (case-insensitive) |
| Khasra Number | Exact match |
| Mauza Number | Exact match |

## Villages Available in Demo Data

- Arya Nagar
- Mralpaar
- Ratini Wal

## Tehsils Available in Demo Data

- Ferozewala
- Lahore City

## Known Limitations

1. **Excel export** is not available in demo mode
2. **Reverse geocoding** (address from GPS) requires internet
3. **QR code** generation requires camera hardware
4. **PDF** layout is simplified compared to production
5. **Only 20 records** are available (not the full 4,654)
6. **No server sync** — all data stays on device
7. **GPS accuracy** depends on device hardware and environment

## Switching to Production

To switch back to production mode:
1. Set `DEMO_MODE=false` in `local.properties`
2. Rebuild the APK
3. The app will use the Django backend and PostgreSQL database
