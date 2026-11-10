# AMap integration analysis and implementation plan

## Status

The existing OpenStreetMap minimap path remains available. Phase 2/3 now includes the official AMap Web Service client, current/manual origins, destination search, candidate-route selection, an OSM-backed Compose route preview, explicit WGS84/GCJ-02 conversion, GPX 1.1 export, Android Downloads/share support, and JVM tests. The Android Maps SDK, Minimap source switching, and native-library insertion remain later phases.

## 1. Actual architecture and versions

The repository has one Android application module, app.

- app/src/main/kotlin/de/timklge/karooroutegraph: application, Karoo bridge, route state, elevation/surface processing, and tiles.
- datatypes: Karoo data fields.
- datatypes/minimap: Minimap data field, zoom actions, Web Mercator helpers, and Canvas drawing.
- pois: online/offline POI providers and alerts.
- screens: Compose settings and POI screens.
- app/src/main/res/xml/extension_info.xml: extension capabilities.
- app/src/test: JVM tests.
- amap: the new isolated API, coordinate, routing, and GPX code.

Actual versions: Gradle 9.4.1, AGP 9.2.1, Kotlin/Compose compiler catalog 2.3.21, serialization plugin 2.3.20, compileSdk 36, targetSdk 28, minSdk 26, JVM 1.8, karoo-ext 1.1.9, Compose UI 1.11.1, Material 3 1.4.0, Glance 1.1.1, and OkHttp 5.3.2. The pre-existing Kotlin patch-version difference was not changed.

## 2. Extension entry and lifecycle

AndroidManifest.xml registers KarooRouteGraphExtension with the io.hammerhead.karooext.KAROO_EXTENSION action and extension_info.xml metadata.

KarooRouteGraphExtension extends KarooExtension("karoo-routegraph", BuildConfig.VERSION_NAME).

- types constructs every data type, including MinimapDataType.
- startMap(Emitter<MapEffect>) publishes/removes temporary POIs and gradient Symbol.Icon overlays on the native Karoo map.
- onCreate() starts RouteGraphUpdateManager.
- onDestroy() stops it.

MainActivity hosts the Compose settings UI. KarooRouteGraphApplication.onCreate() starts Koin.

## 3. Route and location acquisition

KarooSystemServiceProvider owns KarooSystemService and exposes stream<T>(), streamDataFlow(), settings flows, and DataStore persistence.

RouteGraphUpdateManager.start() combines OnNavigationState, OnLocationChanged, OnGlobalPOIs, user profile, distance/on-route streams, and settings. NavigatingRoute.routePolyline is decoded at precision 5 and routeElevationPolyline at precision 1. NavigatingToDestination uses its own polyline/elevation data. The manager derives distance-along-route, rejoin geometry, climbs, sampled elevation, and POI distances, then updates RouteGraphViewModel.

LocationViewModelProvider.startUpdateJob() streams DataType.Type.LOCATION and reads LOC_LATITUDE/LOC_LONGITUDE. Karoo GPS and GPX are treated as WGS84 at the AMap boundary.

## 4. Minimap implementation

MinimapDataType is a graphical DataTypeImpl, not an Activity map view. Its startView():

1. combines route, display, settings, visibility, location, and surface state;
2. computes zoom/center using LineUtils.getOSMZoomLevelToFit() and previewRemainingRoute();
3. creates a Bitmap and Canvas;
4. obtains Web Mercator tiles through getRequiredTiles() and TileDownloadService;
5. draws overlays;
6. places the bitmap in GlanceRemoteViews and calls ViewEmitter.updateView();
7. wires ChangeMinimapZoomLevel as its click action.

TileDownloadService.fetchTileFromNetwork() uses the official OSM raster endpoint and Karoo OnHttpResponse. Disk files live in cacheDir/tiles. The memory cache has 50 entries and a 30-minute access age. An unlimited Channel is consumed sequentially. Disk cache size/age is currently not collected.

Drawing in MinimapDataType.startView():

- rejoin: red;
- completed route: light gray;
- remaining route: yellow, or magenta for point navigation;
- climbs: ClimbCategory minimap colors;
- surface: drawPolylineWithPaint();
- POIs: drawPoi();
- current position/bearing: drawCurrentPosition();
- route/chevrons: drawPolyline();
- scale/copyright: drawScaleBar() and drawCopyright().

KarooRouteGraphExtension.startMap() is a separate native-map symbol layer and does not control the Karoo basemap.

## 5. Settings and Android/Karoo boundary

RouteGraphSettings and RouteGraphPoiSettings are serializable data classes stored as JSON in AndroidX Preferences DataStore by KarooSystemServiceProvider. A future map-source enum should default to OSM.

karoo-ext runs third-party code in this app process and transfers declarative RemoteViews/events/effects across the Karoo boundary. The public 1.1.9 KarooEffect hierarchy has no route-create/import effect. RouteGraph may consume navigation/location state and dispatch documented effects, but must not write native databases or invoke private services.

Official references:

- https://github.com/hammerheadnav/karoo-ext
- https://hammerheadnav.github.io/karoo-ext/karoo-ext/io.hammerhead.karooext.models/-karoo-effect/index.html

## 6. AMap Web API decisions

Only official endpoints are used:

- GET https://restapi.amap.com/v5/direction/bicycling
- GET https://restapi.amap.com/v5/place/text

Bicycling requests show_fields=cost,navi,polyline and 1-3 candidates through alternative_route. The published bicycling parameters have origin and destination but no waypoint parameter. Multi-waypoint routing must therefore be a later segmented planner with boundary deduplication and aggregate metrics.

References:

- https://lbs.amap.com/api/webservice/guide/api/newroute
- https://lbs.amap.com/api/webservice/guide/api-advanced/newpoisearch
- https://lbs.amap.com/api/webservice/guide/tools/info/

AmapApiService maps HTTP errors, malformed JSON, API errors, timeouts, and quota codes to typed exceptions. KarooAmapTransport reuses the existing Karoo HTTP bridge.

The Web Service key is injected into BuildConfig from AMAP_WEB_API_KEY Gradle property, AMAP_WEB_API_KEY environment variable, or ignored local.properties key amap.webApiKey. Never commit it. An APK key is recoverable; production still needs AMap restrictions or a suitable server proxy. New code does not log request URLs containing the key.

## 7. Coordinates

Every GeoCoordinate carries WGS84 or GCJ02, preventing double conversion. AmapPlannedRoute retains GCJ-02 points; GPX export creates new WGS84 points.

CoordinateConverter applies the conventional forward transform only inside mainland-China bounds and uses iterative inverse correction because AMap does not publish a GCJ-02-to-WGS84 service. Outside the bounds, numeric coordinates remain unchanged while the marker changes.

References:

- https://lbs.amap.com/faq/advisory/others/39838
- https://lbs.amap.com/api/webservice/guide/api/convert

## 8. GPX and Karoo route import

Documented usable paths:

- Hammerhead Companion App: share a route file from the phone native share menu; Companion converts/syncs it.
- Dashboard: import the GPX as a route, then sync it.

Reference: https://support.hammerhead.io/hc/en-us/articles/25476943333275-Companion-App-Uploads-and-Syncing

Implemented and verified on a connected Karoo 3:

- GpxExporter writes UTF-8 GPX 1.1 trk/trkseg/trkpt in WGS84 with full shape, name, description, and distance text.
- It does not fabricate elevation, timestamps, waypoints, or turn extensions.
- GpxFileManager saves to Downloads/KarooRouteGraph and creates ACTION_SEND with a read-only content URI.
- The Karoo 3 system chooser opens correctly for the generated GPX, but this test device reports that no installed application can handle the share intent. Direct on-device import is therefore not available in the tested configuration; Dashboard or phone Companion import remains the usable workflow.

Not available through public karoo-ext: direct route-library insertion. Internal databases, private Dashboard endpoints, and firmware modifications are excluded.

## 9. Android Maps SDK and Minimap feasibility

AMap's official Android SDK uses MapView/fragments backed by GLSurfaceView or TextureMapView variants and requires Activity/Fragment lifecycle forwarding. The Karoo data field accepts Glance-generated RemoteViews. GL/Texture map containers cannot be expressed inside RemoteViews, so an AMap MapView cannot be directly embedded in the current MinimapDataType.

References:

- https://lbs.amap.com/api/maps-sdk-for-android/guide/create-map/show-map
- https://lbs.amap.com/api/maps-sdk-for-android/guide/create-project/android-studio-create-project

Compliant Phase 5 direction:

1. retain OSM as the default data-field renderer;
2. first add an independent Activity/Compose page hosting the official SDK;
3. test exact artifact, ARM ABI, privacy initialization, GPU/memory/power, network, offline entitlement, and licensing on Karoo 3;
4. consider a data-field bitmap bridge only if AMap officially supports the required screenshot lifecycle and real-device tests pass;
5. never call undocumented AMap tile URLs.

## 10. File plan

Changed now:

- app/build.gradle.kts: local/environment Web API key injection.
- KarooRouteGraphApplication.kt: AMap/GPX Koin services.
- AndroidManifest.xml and res/xml/file_paths.xml: safe GPX sharing.
- amap/api/AmapApiModels.kt: DTO/domain models.
- amap/api/AmapTransport.kt: Karoo HTTP adapter and typed errors.
- amap/api/AmapApiService.kt: bicycling and POI search.
- amap/routing/AmapRoutePlanner.kt: coordinate-normalizing facade.
- amap/coordinates/CoordinateConverter.kt: WGS84/GCJ-02 conversion.
- amap/gpx/GpxExporter.kt: GPX generation.
- amap/gpx/GpxFileManager.kt: save/share.
- app/src/test/.../amap: pure-logic tests.
- amap/routing/RoutePlannerViewModel.kt: current/manual origin, search, planning, selection, and export state.
- screens/RoutePlannerScreen.kt: route-planning UI, candidate list, cached OSM basemap preview with WGS84 route overlay, and GPX actions.
- TileDownloadService.kt: shared OSM cache plus a remembered OpenStreetMap Deutschland fallback for networks where the primary endpoint is unreachable.
- screens/MainScreen.kt and string resources: Route Planner menu integration.

Next validation slice:

- configure a local AMap Web Service key;
- verify search/planning against the live API;
- verify GPS-origin planning and GPX save/share on Karoo 3;
- confirm whether the Karoo share chooser exposes a supported import destination.

Later Minimap slice:

- add MapSource to RouteGraphSettings with OSM default;
- separate basemap and overlay responsibilities without changing overlay models;
- add the official-SDK Activity path after Karoo 3 validation;
- record real-device lifecycle and resource results.

## 11. Verification

Global GitHub Packages credentials are read from the Gradle user-home gradle.properties file,
with environment variables and project-local properties retained as fallbacks.

Build verification:

- Gradle sync: passed.
- app:testDebugUnitTest: 108 passed, 0 skipped, 0 failed.
- app:assembleDebug: passed.
- git diff --check: passed.

Karoo 3 real-device verification:

- Debug APK deployment and Route Planner launch: passed.
- Live AMap POI search with `Tiananmen`: passed; the API returned multiple Beijing results.
- GPS-origin error handling: passed; without an indoor GPS fix, the UI reported that the current position was unavailable.
- Manual WGS84-origin route planning: passed; the test returned a selectable 1.9 km / 11 min bicycle route.
- Candidate selection and Canvas route preview: passed.
- OSM basemap route preview: passed on Karoo 3; the route preview renders native 256 px tiles with medium filtering (without changing the Minimap's 700 px behavior). The primary tile endpoint timed out on the tested network, the OpenStreetMap Deutschland fallback loaded successfully, and subsequent tiles use the successful endpoint/cache.
- GCJ-02 route to WGS84 OSM overlay alignment: visually verified for the Beijing test route, including start/end markers and OSM attribution.
- GPX save: passed; the UI confirmed `Downloads/KarooRouteGraph`.
- GPX ACTION_SEND chooser launch: passed.
- Direct on-device GPX import target: unavailable on this Karoo 3 because the chooser reported no compatible installed application.
- Outdoor GPS-origin planning, Dashboard/Companion ingestion of the generated file, and AMap Android Maps SDK rendering remain to be verified.
