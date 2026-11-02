# Product

<!-- impeccable:product-schema 1 -->

Written 2026-11-03 from the operator's own words in the dot:lab session (no separate interview: the operator
asked for one overnight answer). Lines marked (inferred) are Claude's reading of those words, not confirmed.

## Platform

android

## Stack

Native Android, Kotlin, plain Views and RemoteViews widgets (no Compose), minSdk 34. Chosen in the measurement
app (works/2026-11-02-parking-floor/app) and kept because the operator wants the app as light as possible.

## Users

- Drivers in Korea who park in multi-level garages (malls, hospitals, apartments, offices) and forget the floor.
- Families sharing one or more cars: whoever drove last parked it, the others need to know where (operator
  2026-11-03).
- (inferred) Older parents and partners who will not open an app or type anything.

## Product Purpose

Know where the car is, floor and pillar, without doing anything. The floor is estimated from the barometer, the
widget asks once (확인/변경), a pillar photo fills the pillar code and is never stored, and parking is detected by
the car's Bluetooth disconnecting. Success: the operator tries it in a real garage and wants to keep using it.

## Positioning

Competing apps are "save your spot" apps: the user has to remember to press a button. This one notices the park by
itself, estimates the floor itself, shows it on a transparent widget, and shares it with family. Research
(docs/archive/2026-11-01-lab-references/07) found no app combining auto floor + widget confirm + no-install share.

## Operating Context

- Moment of capture: just parked, engine off, walking away, often underground with no GPS.
- Moment of use: coming back hours later, standing at an elevator or a garage entrance, phone in one hand.
- Phones: Samsung Galaxy and Pixel, Android 14+, home screens with personal wallpapers.

## Capabilities and Constraints

- Light but powerful (operator 2026-11-03): no resident process, near-zero idle battery, small APK.
- Widget background is 100% transparent and must read on any wallpaper (operator 2026-11-01). Widget comes in many
  sizes (operator 2026-11-03).
- Pillar photos are never stored. No ads.
- Floor-height accuracy is still being measured (T-0184); the fixed 3.3 m model failed (0/5), per-place learning
  and absolute altitude are under study.
- Family sharing needs a small server (dot-lab Cloudflare Worker candidate) and push; the location-law status is
  unverified.
- Undecided: app name, monetisation, whether it is ever published.

## Brand Commitments

- Round 3 picks: transparent widget, painted-parking-pillar numerals (Big Shoulders Display 800 in the mockup),
  estimate shown lighter than a confirmed floor. Round 1 card-on-background widgets were rejected ("다 별론데").
- Lab copy never uses the word "AI".

## Evidence on Hand

- Foot test CSVs (docs/archive/2026-11-02-parking-floor/, outside git, contain GPS: never commit).
- Mockup rounds 1-3 (claude.ai artifacts) and 122 App Store reference screenshots from 2026-11-01.
- No users, reviews or metrics yet; do not invent any.

## Product Principles

1. Zero taps by default; one tap at most to correct.
2. Never ask what the phone can measure; ask once, then remember per place.
3. Light first: a feature that keeps the phone awake must earn it.
4. The widget is the product; the app is for setup and history.
5. Honest numbers: show "쯤" until confirmed.

## Accessibility & Inclusion

(inferred) Large numerals readable at arm's length in dim garages; works for older family members who only look at
the widget.
