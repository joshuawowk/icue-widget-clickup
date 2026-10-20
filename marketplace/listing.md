# Maker Console listing: Task List for ClickUp

Copy-paste kit for the Elgato Maker Console (https://maker.elgato.com). Values below were
drafted against the Elgato product guidelines (https://docs.elgato.com/guidelines/products)
as researched in October 2026; re-check the guidelines before submitting, because limits can change.

## Product name

Task List for ClickUp

(21 characters; the limit is 30. "for ClickUp" wording marks this as a third-party widget that works with the service; it does not use the word "Official" or any ClickUp logo. The manifest `name` is "ClickUp Tasks"; the listing name deliberately puts the service after "for" instead of leading with it. The widget icon is a neutral checklist mark, not the ClickUp logo.)

## Tagline

A glanceable ClickUp task list for the Xeneon Edge, with filters, task details and touch quick-complete.

(104 characters.)

## Description

Keep your ClickUp tasks in view on the Xeneon Edge. The widget shows a My Day style list with circular checkboxes, due-date chips (Overdue, Today, Tomorrow), priority stars and flags, tags, and the list each task belongs to. By default it shows every open task assigned to you, ordered by due date and priority, or you can point it at a single list with its List ID. Touch the header to filter by status and priority, touch a task to read its description, fields, tags and subtasks and to change its status or priority, or tap the circle to complete it with a three-second Undo. Large accounts are read page by page, and the last good data stays on screen with an offline tag if the network drops. It fits every Xeneon Edge slot size, horizontal or vertical, and you can set text, accent and background colors, a background image and transparency. Requires iCUE 5.52 or later on Windows, a Xeneon Edge and a ClickUp personal API token. This is an independent, unofficial widget, not affiliated with ClickUp. It is free software under the MIT license, and the source is on GitHub.

(1079 characters; Maker Console asks for 250 to 1500. Counted with Python `len()` on the paragraph above.)

## Category

Productivity. (The widget is a task list; the closest observed category is Productivity.) The exact Maker Console category list for iCUE widgets was not verified when this kit was
written. The categories observed live on the Elgato Marketplace iCUE widgets page in October 2026 were Audio, Productivity,
Gaming, Artistic, Monitoring, Engagement and Social; pick the closest one the console offers.

## Release notes

Version 1.5.1

- Declares the `api.clickup.com` network permission in the manifest.
- Sets `min_app_version` to 5.52.
- Self-closes the head elements and bundles the media viewer inside the widget folder, so the package passes the iCUE widget validator.
- The `.icuewidget` is now built from a clean staging copy with `icuewidget package`.

(Condensed from the GitHub release notes for v1.5.1 (`gh release view v1.5.1 -R joshuawowk/icue-widget-clickup`); repository housekeeping lines were left out.)

## Requirements

- CORSAIR iCUE 5.52 or later (the manifest sets `min_app_version` to 5.52).
- Windows PC (the manifest targets `os` windows).
- A CORSAIR Xeneon Edge (`dashboard_lcd` device).
- A ClickUp account and a personal API token (ClickUp: avatar, Settings, Apps, API Token, starts with `pk_`).
- Internet access to `api.clickup.com`. No companion service is needed.

## Network permissions

The manifest declares one permission: `{ "type": "url", "domain": "api.clickup.com" }`. The widget talks only to `https://api.clickup.com` over HTTPS with your token: GET to read tasks, and PUT only when you change a task's status or priority. There are no other endpoints and no analytics. iCUE shows the permission for approval at install, and it can be revoked in the widget settings.

## License

MIT, see `LICENSE` in the repository root. Source: https://github.com/joshuawowk/icue-widget-clickup

## Submission notes

- Asset specification unverified. The iCUE-specific listing asset requirements (icon size, gallery image count and
  dimensions, description limits) were not found in Elgato's documentation when this kit was written. This kit reuses the
  sizes of the Stream Deck kit: a 288x288 listing icon and 1920x960 gallery images, with a description of 250 to 1500
  characters. Re-check the Maker Console form and resize or re-render (`marketplace/build.sh`) if it asks for something else.
- Submission route unverified. Elgato's become-a-maker page lists Stream Deck, Wave Link, OBS and 3D as self-serve Maker
  Console categories but does not list iCUE widgets. If the console has no iCUE widget option, the fallback is the
  maker@elgato.com "New Product Submission" email route.
- File to upload: `ClickUp-Tasks-1.5.1.icuewidget`, the `.icuewidget` attached to GitHub release v1.5.1:
  https://github.com/joshuawowk/icue-widget-clickup/releases/download/v1.5.1/ClickUp-Tasks-1.5.1.icuewidget
  Download it from the release; do not zip the folder by hand. To rebuild it, use `npm run package` (needs
  `npm install -g icuewidget-cli`).
- Manifest version is 1.5.1 and the file above matches release v1.5.1. If you submit a later release, update the
  version, release notes and file name in this kit first.
- Listing icon: `resources/icon.png` is 256x256 and was upscaled to 288x288 with ImageMagick (`convert resources/icon.png -resize 288x288!`), so it is slightly soft; re-export `resources/icon.svg` at 288 for a sharper icon if needed.
- Packaging caveat: `npm run package` stages an explicit list of widget files (`tools/package.js`), so the `marketplace/` folder is not included in the `.icuewidget`.
- Validator note: `icuewidget validate` (CLI 0.5.0, run in October 2026 on the unzipped local `dist/` build of that version) passes with no warnings. Re-run it on the file you upload.
- Not tested on hardware: the kit was written without Windows, iCUE or a Xeneon Edge. Gallery slides show the shipped preview and text only; no screenshot was taken from a running panel.
- The GitHub repository also holds an unrelated Android app. Its release tags start with `android-v`; only the `v` release carries the iCUE widget, so upload only the `.icuewidget` file linked above.

## Assets in this folder

| File | Size | Use |
| --- | --- | --- |
| `listing-icon-288.png` | 288x288 | Product icon |
| `gallery-01.png` | 1920x960 | Overview: product name and the widget preview (`resources/preview.png`) |
| `gallery-02.png` | 1920x960 | Feature cards: filtering, details, quick-complete, chips, sizing |
| `gallery-03.png` | 1920x960 | Connection to ClickUp, setup steps and network permission |

Slide 1 shows the widget's own `resources/preview.png` unmodified; its tasks are sample data ("Mock Workspace").
`marketplace/src/` holds the HTML sources and `marketplace/build.sh` re-renders the gallery PNGs with
headless Chromium.

## Trademark note

"ClickUp" is the name of a separate product. This widget is independent and unofficial, and uses no ClickUp logo.
