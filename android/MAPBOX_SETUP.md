# Mapbox maps

Dashboard and Survey Map use the native Mapbox Maps SDK. The SDK is pinned to 11.10.3 (NDK 27 binaries), compatible with this project's Kotlin 1.9 toolchain.

Set `MAPBOX_ACCESS_TOKEN=pk.your_public_token` in the ignored `local.properties` file, or supply `MAPBOX_ACCESS_TOKEN` as a Gradle project property. This generates the `mapbox_access_token` Android resource. No secret token is needed for the public Mapbox Maven repository. Never put an `sk.` token in the app.

Sync Gradle and build the debug APK normally. Map tiles and styles require a valid public token and a network connection on first load. This change does not download offline map regions; locally saved surveys and the My Surveys filter still work independently of map tiles.

Green means the survey meets the same required-field and door-photo checks as the survey form; red means those details are still incomplete. Upload synchronization is separate: a complete survey saved offline is still green. A building's structural status is not used as a completion flag.

Points have a white border and translucent halo. At distant zoom levels they group into numbered clusters; tapping a cluster zooms in. Nearby/overlapping individual records can be selected from a list. Streets/satellite switching retains survey data and camera position. Default Mapbox attribution and logo controls remain available.
