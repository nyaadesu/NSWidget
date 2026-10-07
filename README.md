# NS Widget

An Android home-screen widget for the Dutch railways (NS) that shows

1. **Whether your off-peak discount is active** - and when it's about to start:
   *"Wait 23 min for discount"* appears during the hour before off-peak begins, and
   *"Discount ends in 25 min"* during the hour before peak starts.
2. **Upcoming departures from your nearest station**, with departure time, delay, train type,
   destination and platform (yellow NS-style chip; orange if the platform changed, red if cancelled).
   The yellow map pin left of the station name opens Google Maps on the station.
   Next to each destination, in small dimmed text, the major cities the train calls at are listed ("Rotterdam, Delft")
   (only as many as fit, so it never costs a departure row; switch it off in the app). Which places count
   as major is a list in [`MajorCities.kt`](app/src/main/java/app/nswidget/data/MajorCities.kt).
3. **Favourite stations** (up to 3, e.g. Home and Work, set in the app). Highlighting is colour only,
   no extra text: trains that stop at a favourite show their time and destination in the accent colour,
   and the train that starts the fastest journey there (earliest arrival from NS's journey planner,
   possibly with a change) gets a filled, fully rounded highlight and a bold destination. Each favourite costs one journey-planner call at most every
   5 minutes while the screen is on.
4. **Material You styling** (Material 3 Expressive, as used by recent Android versions): the widget and app
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

- The widget repaints once a minute, just after the clock minute changes, so countdowns are right
  and trains that have left drop off the list. Repainting reads saved data and needs no network.
- The alarm doesn't wake the phone: with the screen off nothing happens (no battery, no API calls),
  and the overdue repaint runs as soon as you turn the screen on. Android may deliver it a few
  seconds late, and can delay it more if the app hasn't been used for a long time.
- Departures (including delays and platform changes, which one call returns for the whole station)
  are fetched while the screen is on, more often when it matters: every 2 min while a train leaves
  within 15 min, every 5 min if the next one is 15-45 min away, otherwise every 10 min. Failed calls
  back off to 5 min, and past 2,000 calls in a day it slows to every 15 min. With the screen on for
  a few hours that's ~100-150 calls a day (the free tier allows 5,000); the app shows today's count.
- The refresh button also gets a fresh location fix, so the nearest station follows you. Outside the
  app that needs "Allow all the time" location; otherwise Android's last known location is used.
  With background access, a location fix older than 10 minutes is also renewed automatically.

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
