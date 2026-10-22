---
name: flagline-design
description: Review and restyle the Flagline Android app so it looks like a person designed it for beach safety, not like generated "AI" UI. Use when asked to review, polish, restyle, de-slop, or improve the look of Flagline or any of its screens.
---

# Flagline design review & restyle

Flagline is a beach safety app for San Diego beachgoers (flags, rip currents, stings, wildlife, tides, UV, water quality, SOS). It's Java + XML views + Material 3, English and Spanish.

The goal is **not** "make it look like Apple" or any other house style. Copying a famous style is one more default. The goal is that every visual choice is made for *this* app: people standing on a beach in bright sun, deciding whether to go in the water. Restyle, don't rebuild, and never break behavior.

## Ground rules

- Visual layer only: `res/values*` (colors, themes, styles, dimens, strings), `res/layout*`, `res/drawable*`, `res/font`, and view-styling code in Java where styling is set there. Don't change data, networking, location, map logic, scoring, or what any button does.
- Keep every `android:id` the Java code uses.
- Stay with XML views + Material 3. A bundled font in `res/font` is fine; no new UI libraries.
- Work on a branch: `git switch -c design/restyle`. If `git status` isn't clean, ask the user to commit first. Commit after every batch so any batch can be reverted.
- Any string change goes into **both** `values/strings.xml` and `values-es/strings.xml`.
- These colors depict physical things and must stay the same: `flag_*` (real beach flags), `rocks_ref_*` (a measuring scale), map markers. Coral/danger stays reserved for danger.
- Don't edit until the user has seen the plan, unless they say "just do it."

## What already reads as AI-generated in Flagline

Confirm these against the current code (it may have changed), then add anything else you find:

1. **ALL-CAPS tracked-out labels above sections and tiles.** `TextAppearance.App.Caption` and `TextAppearance.App.TileLabel` in `themes.xml` (`textAllCaps`, letterSpacing 0.09), used all over `activity_main.xml`. This is the most common tell. Replace with sentence-case labels, or drop the label when the content explains itself.
2. **Dashboard-style hero.** The home "verdict" card is a status dot plus a 2×2 grid of stat tiles (big value, tiny caps label) nested inside a card. It reads like an analytics dashboard, not a beach.
3. **Everything is a box.** `bg_surface_card` (hairline + 8dp radius) on almost every group, tiles nested inside cards, the same treatment regardless of importance. Use space, alignment, and a few rules to group content; keep boxes for things that are actually separate objects.
4. **Generic warm off-white ground.** `background #FDFBF7` with warm sand neutrals is the single most common generated palette. Either justify it as sand (push it until it clearly *is* sand, warm and a bit textured in tone) or replace it. Decide in the plan, don't leave it by default.
5. **Text too small for sunlight.** Body 13.5sp, subtitles 12.5sp, tile labels 10.5sp, tab labels 9.5sp. Outdoors on a bright beach these are hard to read. Body should be at least 16sp, nothing below 12sp, and the verdict huge.
6. **Default typeface.** The app falls back to Roboto (`sans-serif`); the comments mention Space Grotesk and Poppins, which are also stock "AI" picks. Choose a face for this subject (see direction).
7. **Copy punctuation tells.** About 50 em dashes in `strings.xml` (e.g. "Red — High hazard", "%1$s — %2$s"), middle-dot joins ("Done today · 3 days in a row"), and some Title Case ("Find Nearby Beaches"). Rewrite as plain sentences: "Red flag: high hazard", "Done today. 3 days in a row." Sentence case everywhere.

What's good and should be kept and built on: the physical flag colors, the drawn beach placeholder scenes, the wave height card, coral reserved for danger, the flat (not colored) header, and the safety-first copy voice.

## A direction to start from (propose it, don't assume it)

The most characteristic thing in this world is the **lifeguard's signage**: flags on a pole, the yellow tower, painted boards with big condensed lettering you can read from 50 meters. Lean on that.

- **The verdict is the flag.** The top of the home screen and beach detail should answer "can I swim?" the way a flag does: a bold band or shape in the flag's color with the verdict set very large ("Good to swim", "Take care", "Not safe to swim"). Readings (waves, wind, water temp, UV) go *under* it as a simple readable list, not a tile grid.
- **Signage type.** One condensed or semi-condensed sans with real weight for headlines and the verdict, like painted tower lettering (for example Barlow Semi Condensed, Archivo, or Saira Semi Condensed, bundled in `res/font`). Body can be the same family's regular width or system sans. Pick one, say why.
- **Tower yellow as a second voice.** `map_tower #FACE6B` already exists. It can carry the lifeguard/emergency surfaces (SOS, "call a lifeguard") so they feel official, while sea teal stays the interactive color.
- **Spend boldness in one place.** The verdict is the loud thing. Everything else is quiet: lists, generous spacing, sentence case, few boxes.

If, after looking at the app, a different direction fits better, propose it instead, but it must come from the beach/lifeguard subject, not from a generic style.

## Process

### 1. Survey
- Read `AndroidManifest.xml` (screens), `themes.xml`, `colors.xml` + `values-night/colors.xml`, and every layout. Note which drawables and styles each screen uses.
- If an emulator is running, install (`gradlew.bat installDebug` on Windows) and screenshot every screen: `adb exec-out screencap -p > before_<screen>.png`. Keep these "before" shots. (If the emulator shows "offline", see CLAUDE.local.md: cold boot it.)

### 2. Plan, then check the plan
Write a short plan:
- **Color:** 4–6 named hex values and their jobs.
- **Type:** the typeface(s), a type scale in sp (verdict, screen title, section, body, secondary, small), weights.
- **Layout:** an ASCII sketch of the home screen and the beach detail screen.
- **Principles:** 3 short rules for what makes Flagline look like Flagline.

Then check it: for each choice, ask "would I have picked this for any random app?" If yes, change it and say what changed. Also check it against the tell list below. Then show the user the plan plus the "what reads as AI" findings, and **stop and wait**.

### 3. Edit, in batches (commit after each)
1. Tokens: colors, type scale (`TextAppearance.App.*`), dimens, font. Fix both light and night.
2. Global theme and shared styles (cards, tiles, buttons, tab bar, sheets).
3. Home screen (`activity_main.xml`): verdict, readings, search, lists.
4. Beach detail (`activity_beach_detail.xml`).
5. Remaining screens one at a time: guides, alerts, sighting, rock rating, settings, saved, contact.
6. Copy, English and Spanish together.

### 4. Verify
- `gradlew.bat assembleDebug` and `gradlew.bat lintDebug`; fix anything you caused.
- Reinstall and take "after" screenshots of every screen; show before/after pairs to the user.
- Check light and dark mode, Spanish, a small phone (~360dp wide), and the largest system font size (nothing clipped).
- Tap through every button, list, sheet, and the map to confirm behavior is unchanged.
- Report what you couldn't verify.

## Tell list (things that make an app look generated)

Avoid unless there's a specific reason for this app:
- ALL-CAPS tracked labels ("eyebrows") above headings, tiles, or sections.
- Big number + tiny label stat tiles in a grid as the main feature.
- Content chopped into identical rounded cards, cards inside cards, one radius on everything, the same faint shadow or hairline on every box.
- Cream/off-white backgrounds with warm neutrals chosen by default; near-black with one neon accent; gradient washes as decoration.
- One word in a headline set in a different color or weight.
- Labels joined with " · " or built as "Word — fragment"; arrows appended to button text; em dashes as the go-to punctuation.
- Numbered markers (01, 02, 03) on things that aren't a real sequence. (The drowning and first-aid steps *are* a sequence; numbers are fine there.)
- Fade/slide-in animation on every section. One meaningful motion moment (the flag/verdict appearing) beats many.
- Stock "trendy" fonts picked by reflex: Inter, Poppins, Space Grotesk, Montserrat.
- Emoji as UI icons.

## Floor (always)
- Contrast at least 4.5:1 for text (3:1 for very large), in both themes and on every flag color. Yellow flags need dark text.
- Touch targets at least 48×48dp.
- `contentDescription` on every meaningful icon/image; `@null` on decorative ones.
- Text in `sp`; layouts survive the largest font setting.
- Respect "Remove animations" (animator duration scale 0).
