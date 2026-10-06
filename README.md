# NS Widget

An Android home-screen widget for the Dutch railways (NS) that shows

1. **Whether your off-peak discount is active** - and when it's about to start:
   *"Wait 23 min for discount"* appears during the hour before off-peak begins, and
   *"Discount ends in 25 min"* during the hour before peak starts.
2. **Upcoming departures from your nearest station**, with departure time, delay, train type,
   destination and platform (yellow NS-style chip; orange if the platform changed, red if cancelled).
   The yellow map pin left of the station name opens Google Maps on the station.
   Next to each destination a small "via Rotterdam, Delft" lists the major cities the train calls at
   (only as many as fit, so it never costs a departure row; switch it off in the app). Which places count
   as major is a list in [`MajorCities.kt`](app/src/main/java/app/nswidget/data/MajorCities.kt).
3. **Material You styling** (Material 3 Expressive, as used by recent Android versions): the widget and app
   follow your wallpaper's dynamic colours, use large rounded shapes and tonal containers, and the app icon
   supports themed icons. The discount banner stays green / amber / red and platform signs stay NS yellow so
   they read at a glance on any wallpaper. Buttons and the status badge are scalloped "cookie" shapes, like the
   icons on recent Android launchers. Rounded corners and dynamic colour need Android 12+.

## NS peak rules used

| | |
|---|---|
| Peak (no discount) | Mon-Fri 06:30-09:00 and 16:00-18:30 |
| Off-peak (discount) | everything else, all weekend, and on public holidays |
| Holidays | New Year's Day, Good Friday, Easter Monday, King's Day (26 Apr if the 27th is a Sunday), Ascension Day, Whit Monday, Christmas Day, Boxing Day, and Liberation Day (5 May) every 5th year |

The discount is decided by your **check-in time**, so "wait x minutes" means: check in after that moment.
The rules live in [`PeakRules.kt`](app/src/main/java/app/nswidget/discount/PeakRules.kt) and
[`Holidays.kt`](app/src/main/java/app/nswidget/discount/Holidays.kt) if NS changes them
(Good Friday in particular is worth double-checking against NS's own conditions).

## Setup

1. Open the folder in **Android Studio** (Koala or newer) and let Gradle sync, then run it on a phone (Android 8+).
2. Get a free NS API key: create an account at <https://apiportal.ns.nl>, subscribe to the **Ns-App** product, copy the key.
3. Open the *NS Widget* app, paste the key, and allow location.
   - For the widget to follow you while the app is closed, also tap **Allow in background** and choose
     *Allow all the time*. Without it the widget uses your last location from when the app was open.
   - Or switch off "Use nearest station" and pick a fixed station.
4. Long-press the home screen -> Widgets -> *NS Widget*. Resize it: taller means more departures.

The discount banner works without an API key or location.

## How it stays up to date

- An inexact alarm repaints the widget: every 15 min normally, every 5 min in the hour before the
  discount starts/ends, every minute in the last 10 minutes, and once just after the boundary so the
  banner flips on time. Repainting needs no network.
- Departures are re-fetched (WorkManager, needs network) when they're older than ~9 minutes,
  and when you tap the refresh icon on the widget.
- Between repaints the displayed minute count can be a little behind; the banner's second line shows
  the exact clock time. Tapping refresh updates it immediately.

## Layout

```
app/src/main/java/app/nswidget/
  discount/   peak rules, holidays, banner texts            (pure Kotlin, unit-tested)
  data/       NS API client + parsing, settings, caches, nearest-station maths
  location/   last-known / fresh location
  refresh/    alarm chain + WorkManager fetch
  widget/     Glance widget UI and its view-model
  ui/         settings screen (Compose)
```

Run the unit tests with `./gradlew test`.

## Notes

- Your API key is stored in the app's private storage and sent only to `gateway.apiportal.ns.nl`.
  Location never leaves the phone; only a station code is sent to NS.
- The free NS API tier allows 5,000 requests/day; this widget uses roughly 100-150.
