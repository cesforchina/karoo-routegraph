# Karoo Route Graph Extension

[![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/timklge/karoo-routegraph/android.yml)](https://github.com/timklge/karoo-routegraph/actions/workflows/android.yml)
[![GitHub Downloads (specific asset, all releases)](https://img.shields.io/github/downloads/timklge/karoo-routegraph/app-release.apk)](https://github.com/timklge/karoo-routegraph/releases)
[![GitHub License](https://img.shields.io/github/license/timklge/karoo-routegraph)](https://github.com/timklge/karoo-routegraph/blob/master/LICENSE)

This extension for Karoo devices adds a graphical data field showing the elevation profile of the current route,
including route-specific POIs that you have set up, global POIs and climbs. It also provides a minimap datafield and
a button to navigate to POIs along the route (e. g. upcoming gas stations for refueling). It's meant to be used
as a companion app on long rides where you want to have a quick overview of the route that includes upcoming checkpoints,
climbs and refueling opportunities so that you can pace yourself accordingly.

The app also includes an AMap-powered bicycle route planner for China. It can search for a destination, request candidate bicycle routes, preview a selected route on an OpenStreetMap basemap, and export it as a standard GPX file.

Compatible with Karoo 2 and Karoo 3 devices.

<img width="200" height="333" alt="Minimap" src="minimap.png" /> <img width="200" height="333" alt="POI navigation" src="poinav.png" /> <img width="200" height="333" alt="Vertical Route Graph" src="vertical_routegraph.png" /> <img width="200" height="333" alt="Surface conditions" src="routegraph_surface_conditions.png" />

## Installation

This extension is available from the extension library on your Karoo device. Find more information on extensions in the [Hammerhead FAQ](https://support.hammerhead.io/hc/en-us/articles/34676015530907-Karoo-OS-Extensions-Library).

### Building with AMap support

AMap route planning requires an AMap **Web Service API key**. Do not commit API keys or GitHub tokens to the repository. The key can be supplied through one of these local mechanisms, in precedence order:

1. Gradle property `AMAP_WEB_API_KEY`
2. Environment variable `AMAP_WEB_API_KEY`
3. `amap.webApiKey` in the ignored project `local.properties`

For a local source build that needs GitHub Packages credentials, keep them outside the repository in `~/.gradle/gradle.properties`:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_TOKEN
AMAP_WEB_API_KEY=YOUR_AMAP_WEB_SERVICE_KEY
```

A key embedded in an APK cannot be treated as a secret. Configure the restrictions required by AMap for production use, or place the Web Service calls behind an appropriate server-side proxy.

## Usage

After installing this app on your Karoo and opening it once from the main menu, you can add the following new data fields to your data pages:

- Minimap (see first screenshot): Shows a secondary map of the whole route, including climbs and POIs
- Route Graph (see second screenshot): Shows a graph of the current route, including POIs / checkpoints / refueling stops. This data field works just like the native
elevation profile data field of the Karoo, but adds markers for route-specific and global POIs and climbs. By touching the data field, you can cycle
between the full route and the upcoming 2km, 20km, 50km and 100km of the route.
- Vertical Route Graph (see third screenshot): Similar to the route graph, but flipped so that the vertical axis depicts the route distance. The name of POIs, the remaining distance and elevation
to them as well as climb lengths and total climb elevations are shown next to the elevation profile.
- POI Button: Shows a single button that opens a dialog with a list of all POIs along the route. You can tap on each POI to start navigation to it. In addition to the route-specific and global POIs set up on the Karoo, 
you can also look for nearby POIs of certain categories (e. g. cafes, restaurants etc.) using the Overpass API and downloaded POIs, or search for POIs by name using the Nominatim geocoding API.
By default, route graph datafields also feature a POI button in the top right corner.
- Distance to POI: Shows the distance to the next POI along the route. Note that this is the distance along the route and not the straight-line distance.
- Elevation to POI: Shows the remaining climbing to the next POI along the route. Note that this is the elevation along the route and not the straight-line elevation.
- ETA at POI: Shows the estimated time of arrival at the next POI along the route. The ETA is calculated based on your average power output over the last hour. If you don't have a powermeter, this uses a rough approximation based on your speed and the route gradient.
- ETA at end of route: Shows the estimated time of arrival at the end of the route.
- Gravel remaining: Shows the remaining distance on offroad surfaces (gravel, dirt, loose surfaces like sand or grass) along the route. Requires surface condition display to be enabled in the settings.

The **AMap route planner** is available from the app's main menu. It supports the current GPS position or manually entered WGS84 coordinates as the origin, AMap destination search, route candidate selection, distance/time summaries, an OSM-backed route preview, and GPX save/share actions.

Additionally, you can enable chevrons on the map to indicate gradients. The chevrons are drawn on top of the route (see third screenshot). Find more information below.

## Features

### Elevation profile datafields

<img width="200" height="333" alt="Vertical Route Graph" src="vertical_routegraph.png" />

The main data fields provided by this extension are the horizontal and vertical route graph data fields. Both data fields show the elevation profile of the current route, similar to the native elevation profile data field.
The horizontal route graph shows distance on the horizontal axis and elevation on the vertical axis, while the vertical route graph shows elevation on the horizontal axis and distance on the vertical axis.
Both data fields show markers for route-specific POIs as well as global POIs and climbs.

By touching the data field, you can cycle between the full route and the upcoming 2km, 20km, 50km and 100km of the route. If you enable surface condition display in the settings menu,
offroad sections of the route will be highlighted in the route graph (see section below).

For high zoom levels (2 km), instead of climbs, each 100 meter segment of the route will be colored individually, as is done in the Karoo's climber drawer.

### Minimap

<img width="200" height="333" alt="Minimap" src="minimap.png" />

The minimap data field shows a secondary map of the whole route, including climbs and POIs. The current position is indicated by a yellow arrow.
You can zoom in by touching the data field. The minimap uses OpenStreetMap raster tiles, preferring https://tile.openstreetmap.org and falling back to https://tile.openstreetmap.de when the primary endpoint is unreachable, so it requires an active internet connection to load uncached map tiles (provided by the companion app).
Downloaded tiles are cached on the Karoo for offline use.

### AMap bicycle route planner

The planner uses the official AMap Web Service APIs for destination search and bicycle directions. AMap route geometry is retained as GCJ-02 and converted to WGS84 before it is drawn on the OSM preview or exported.

The preview uses the same cached OSM tile service as the minimap, but renders native 256 px tiles so labels and roads remain sharp. The selected route is drawn with start/end markers and automatically fitted to the preview.

GPX export writes UTF-8 GPX 1.1 with the full WGS84 route shape, name, description, and distance. It does not invent elevation, timestamps, or turn instructions. Files can be saved to `Downloads/KarooRouteGraph` or shared through Android.

The public Karoo Extension SDK does not expose an API for inserting a route directly into the native Karoo route library. Import the generated GPX through Hammerhead Dashboard or share it to the Hammerhead Companion App. Availability of a compatible on-device share target depends on the apps installed on the Karoo.

### Surface Conditions

You can enable surface condition display on the route graph data fields in the settings menu. The extension will then use the maps downloaded on your Karoo to determine the surface type of each route segment
and highlight offroad segments (e. g. gravel, dirt roads) in the route graph. To do this, the extension will ask for permission to read the map files on your Karoo. Calculating the surface conditions
can take a few seconds when loading a route.

<img width="200" height="333" alt="Surface conditions" src="routegraph_surface_conditions.png" />

Gravel sections will be highlighted using a thick line and hatched fill pattern. Offroad sections (e. g. grass) will additionally be highlighted by a red outline.

### Gradient Indicators

You can enable gradient indicators (chevrons) on the map in the settings menu. The extension will then draw chevrons on top of the route on the main karoo map to indicate uphill and downhill sections.
Uphill chevrons use the known color scheme of the karoo (green-yellow-red for increasing gradients), downhill chevrons are shown in blue, with white / light blue for shallow descents and dark blue for steep descents.

![Chevrons](chevrons.png)

### Adding POIs

During riding, you can open a POI managment menu by clicking the button shown in the top right corner of any routegraph elevation profile datafield. This will open a menu with three pages:

- On the *Custom* page, your global POIs and the POIs of the currently loaded route are shown. These include POIs created on the HH dashboard and imported via Strava / RWGPS etc. for the current route. You can toggle the display to show the remaining distance and remaining elevation climbing along the route instead of straight-line distance.
- On the *Nearby* page, you can look up new POIs of selected categories near you (e. g. supermarkets, shelters, bike shops). You can download POIs for regions in the settings menu, so that you can look up POIs without an active internet connection during riding.
- On the *Search* page, you can look up POIs by typing in a search query (e. g. "Brandenburg Gate" or a postal address). This requires an active internet connection.

On all pages, you can use the dot menu for each POI to initiate navigation to that POI. For POIs looked up on the Nearby or Search pages, you can also choose to add that POI to the *Custom* page. They will then also appear in the route graph data fields (elevation profiles and minimap) as well as the main karoo map.

Furthermore, in the settings menu, you can select POI categories that should automatically be added to the map and elevation profiles when loading up a route.

<img width="200" height="333" alt="POI navigation" src="poinav.png" /> <img width="200" height="333" alt="POI download" src="poidl.png" /> <img width="200" height="333" alt="POI settings" src="poisettings.png" /> <img width="200" height="333" alt="POI categories" src="poicategories.png" />

## Extension Developers: Surface Type Data Type

If the user has installed the routegraph extension on his karoo, you can stream the surface type data type from other extensions via `karoo-ext`.
Use data type id `TYPE_EXT::karoo-routegraph::surfacetype`.

- The `surfacetype` datatype contains a single field with the classification of the current route segment's surface as determined from the Karoo's offline map data:
  - `0.0` = paved / asphalt (also used as fallback when the segment surface is unknown)
  - `1.0` = gravel / dirt
  - `2.0` = loose (sand, grass, mud, snow, ice)
- The stream emits `NotAvailable` when no route is loaded or the rider position along the route is unknown.
- Requires the user to have enabled surface condition display in the routegraph extension settings (so that the map files have been read and surface segments have been calculated).

## Credits

- OpenStreetMap Data © <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors (<a href="https://opendatacommons.org/licenses/odbl/index.html">ODbL</a>), <a href="https://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, <a href="https://openstreetmap.org/fixthemap">participate/fix mistakes</a>
  - OpenStreetMap raster tiles for the minimap and route preview are served by https://tile.openstreetmap.org, with https://tile.openstreetmap.de as a fallback
  - Nearby POI search uses https://overpass-api.de
  - POI search box uses nominatim API for geocoding: https://nominatim.openstreetmap.org/
  - Offline POIs are downloaded from https://routegraph.timklge.de
- AMap destination search and bicycle directions use the official [AMap Web Service APIs](https://lbs.amap.com/api/webservice/summary).
- Icons are from [boxicons.com](https://boxicons.com) ([MIT-licensed](icon_credits.txt))
- Uses [karoo-ext](https://github.com/hammerheadnav/karoo-ext) (Apache2-licensed)
- Uses the [ANTLR grammar](https://github.com/opening-hours/opening_hours_grammar) for OSM opening hours definitions (MIT-licensed)
