# Aditya Dash

A personal Android app that puts **Google Maps navigation on the Royal Enfield
Himalayan 450 Tripper Dash with the phone locked**. The stock Royal Enfield app
stops the map when the phone screen turns off; this one keeps drawing the map
off-screen, so the phone can sit in a pocket with the display off.

Built on [OpenDash](https://github.com/subtlesayak/open-dash) (Apache-2.0), which
provides the dash connection, video streaming, media and call cards, ride history
and garage. See `NOTICE` and `docs/UPSTREAM_README.md`.

Independent hobby project. Not affiliated with Royal Enfield or Google. The dash
protocol is unofficial and reverse-engineered; it has only been verified by the
community on dash firmware 11.63.

## What this fork adds

| Feature | How it works |
| --- | --- |
| Google Maps on the dash | Google's Navigation SDK draws the map on a hidden 526 x 300 virtual display. Those frames go to the existing H.264 encoder and on to the dash. Routing, traffic, rerouting and spoken directions are Google's. Two-wheeler routing is requested. |
| Your own API key | Entered in **More > Settings > Google Maps & voice commands**. Stored on the phone only. Never in the source or the APK. |
| Offline map | Choose **Offline map** in the same place. OpenStreetMap tiles and the route are saved when you plan the trip and keep working without signal. If Google cannot start (no data, bad key), the ride falls back to this automatically. |
| Voice commands | Press the joystick in (or use **Try a command**), then speak through your earbuds: "navigate to home", "next song", "pause", "volume up", "zoom out", "how far", "mute directions", "stop navigation", "answer", "reject". |
| Saved places | Name plus `lat,lng` in Settings. "Navigate to home" then works with no signal. |
| Music controls | Track title on the dash, joystick left/right to skip, voice for play, pause and volume. Works with any music app once notification access is granted. |

## Setup

1. Install the APK and allow location, notifications and nearby devices.
2. In [Google Cloud Console](https://console.cloud.google.com/), create a project
   with billing, enable **Navigation SDK**, and create an API key. Restrict the key
   to Android apps with package `com.aditya.dash` and this build's signing
   certificate.
3. In the app: **More > Settings > Google Maps & voice commands**. Paste the key,
   **Save key**, then **Accept Google terms**.
4. Allow the microphone there for voice commands. Grant notification access for
   music and call cards.
5. Turn the bike on, put the dash in Digital mode, tap **Connect to dash**.
6. Share a place from Google Maps to Aditya Dash, review it, send it to the dash,
   lock the phone.

## What has and has not been tested

Compiled and unit-tested (25 tests, including the voice command parser). **Not yet
run on a phone or a bike.** The parts that need a first ride to confirm:

- Google's map keeps drawing on the hidden display once the phone is locked. If it
  stalls, the app notices within 4 seconds and shows the offline map with Google's
  route line and turn data instead.
- The joystick press-in code (`0x18`) reaches the phone outside media mode.
- The earbud microphone is used for commands, and how well it copes with wind.
- Only the "continue" turn glyph is known for the dash's own turn widget, so the
  actual turn ("350 m  Turn left") is drawn into the map picture instead.

## Building

```
echo "sdk.dir=/path/to/Android/sdk" > local.properties
./gradlew :app:assembleDebug
```

Needs JDK 17 or newer and Android SDK platform 36.

## Map data

Offline map tiles © OpenStreetMap contributors, fetched under the OSM tile usage
policy. Google Maps content comes from the Google Navigation SDK under Google's
terms.
