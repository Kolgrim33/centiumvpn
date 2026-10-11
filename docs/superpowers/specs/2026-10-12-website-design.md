# Centium website — design

## Goal

A free public page where people download Centium, plus a private count of how
many times the Download button was clicked. No visitor sign-ups, no tracking of
who visitors are. Success: a shareable `*.web.app` link, and the owner can read
the click count.

## Page

- One static page in `website/public/` (HTML, CSS, a small JS file; no build step,
  no framework).
- Content: what Centium does, the Download button, current version and the APK's
  SHA-256, install steps (allow installs from the browser; GitHub and F-Droid
  copies can't update each other), links to source code and changelog.
- Follows the Android design system: route line (phone → guard → middle → exit →
  internet), the "C" arc mark, Overpass (bundled woff2, no Google Fonts call),
  the app's colour tokens as CSS variables with light and dark modes, sentence-case
  plain-language copy. Works at phone width.
- The download count is **not** shown on the page.

## Download button

- On load, the page fetches
  `https://api.github.com/repos/Kolgrim33/centiumvpn/releases/latest` (CORS-enabled,
  60 req/h per visitor IP) and takes the `centium-*.apk` asset: its
  `browser_download_url`, the release `tag_name`, and the asset `digest`
  (`sha256:…`) for the checksum.
- The button's `href` is the GitHub releases/latest page by default, so it works
  without JS or if the API call fails; JS swaps in the direct APK URL once known.
- No redeploy needed when a new app version is released.

## Counter

- Firestore (default database, free Spark plan), one document `stats/downloads`
  with one integer field `count`.
- On click: the page sends `count: increment(1)` with a short timeout, then
  navigates to the APK whether or not the write succeeded. The download is never
  blocked on counting.
- Firebase JS SDK (modular, pinned version) is loaded only for this write; no
  Analytics, no cookies, nothing about the visitor is stored.
- `firestore.rules`: on `stats/downloads`, `update` allowed only when the new data
  has exactly the key `count` and `count == resource.data.count + 1`. Document
  creation, delete, and every read and write elsewhere are denied. The owner seeds
  the document with `count: 0` once (admin access bypasses rules).
- Known limit: a scripted client can inflate the number one click at a time; this
  can't be prevented without sign-ins or a server function, and is accepted.

## Reading the count

- Firebase console → Firestore → `stats/downloads`.
- `npm run downloads` (in `website/`): prints the website count (via the Firebase
  CLI's Firestore access or REST with the owner's login) alongside GitHub's own
  total `download_count` for APK assets, which also includes in-app updates and
  direct GitHub downloads.

## Firebase project

- New Firebase project for Centium on the owner's account (separate from Moro),
  Spark plan. Hosting site `centium` (`centium.web.app`), or the closest free
  variant if taken.
- `website/firebase.json`, `website/.firebaserc`, `website/firestore.rules`.
- Deploy: `cd website && firebase deploy`. App releases don't touch the site.

## Testing

- Rules: tests against the Firestore emulator — +1 accepted; +2, −1, set to a
  value, extra fields, create, delete and reads refused.
- Live: after deploy, click Download, confirm the count went up by one and the
  downloaded APK's SHA-256 matches the one shown; check phone width and dark mode;
  check the fallback link when the API call is blocked.
