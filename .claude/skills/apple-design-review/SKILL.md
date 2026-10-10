---
name: apple-design-review
description: Review this already-built Android app (Java + XML views + Material Components) against Apple's clean design style, then restyle it to match in small reversible batches. Use when asked to review, polish, restyle, or "make it look like Apple" for the app.
---

# Apple Design Review & Restyle (Android, XML views)

You are reviewing and then restyling an Android app that already works. It is written in Java with XML layouts and Material Components (no Jetpack Compose). The goal is the calm, clean feel of Apple's design (lots of whitespace, one accent color, big confident type, soft rounded cards) **without breaking any functionality**. Restyle, don't rebuild.

## Ground rules

- Never change app logic: Java classes' behavior, data loading, networking (OkHttp/Gson), location, maps (osmdroid), image loading (Glide), navigation, permissions, or what a button does. Only the visual layer: `res/values*` (themes, colors, dimens, styles, strings for approved copy), `res/layout*`, `res/drawable*`, `res/font`, and view-styling code only where styling is set in Java.
- Keep every view `android:id` the code uses. Don't remove or rename views referenced from Java; you may wrap, re-order for layout, or restyle them.
- Stay with XML views + Material Components. Don't add Compose or a new UI library. A font file in `res/font` is fine.
- Before any edit, run `git status`. If there are uncommitted changes, ask the user to commit first. Commit after every batch with a clear message (e.g. `design: add apple-style color and type tokens`) so any batch can be reverted.
- Don't edit until the user has seen the review, unless they explicitly say "just do it."

## Phase 1 — Survey

1. Read `app/src/main/AndroidManifest.xml` to list every Activity, and find Fragments/dialogs/bottom sheets in `app/src/main/java`.
2. List every layout in `res/layout*` and which screen uses it. Note shared pieces (toolbar/app bar, buttons, cards, list items, text fields, dialogs, bottom nav).
3. Read `res/values/themes.xml`, `colors.xml`, `dimens.xml`, `styles.xml` (and `values-night/` if present). Note the parent theme (e.g. `Theme.Material3.*` or `Theme.MaterialComponents.*`).
4. Find hard-coded colors, text sizes, and margins inside layouts (`#xxxxxx`, `16sp`, `12dp` written inline).
5. If an emulator or device is running, build and install (`./gradlew installDebug`, or `gradlew.bat installDebug` on Windows) and capture each screen with `adb exec-out screencap -p > screen.png` to look at it. Otherwise read the layouts.

## Phase 2 — Review

Check every screen against the checklist below. Output a ranked list, highest impact first:

| # | Issue | Where (layout / screen) | Fix | Impact |
|---|-------|------------------------|-----|--------|

Group findings into: **Global** (theme, colors, type, dimens — fix once, affects everything), **Components**, **Screens**, **Copy**. End with a short summary of the 3–5 changes that would make the biggest visual difference. Then stop and wait.

## Phase 3 — Edit (in this order)

1. **Tokens first.** Put every value in one place: colors in `res/values/colors.xml` (+ `values-night/colors.xml`), sizes in `res/values/dimens.xml`, text styles as `TextAppearance.App.*` in `styles.xml`/`themes.xml`. Replace hard-coded values in layouts with these as you touch them.
2. **Theme.** Point the app theme's `colorPrimary`, `colorOnPrimary`, `colorSurface`, `colorOnSurface`, `android:colorBackground`, `colorSecondary`, status/nav bar colors, `shapeAppearance*`, and default `materialButtonStyle` / `materialCardViewStyle` / `textInputStyle` at the tokens. Commit.
3. **Shared components.** Buttons, cards, list items, text fields, toolbar, dialogs/bottom sheets, bottom nav. Prefer defining a style once and applying it via the theme. Commit.
4. **Screens, one at a time.** Spacing, hierarchy, grouping, alignment. Commit after each screen.
5. **Copy** (only changes the user approved) in `strings.xml`. Commit.
6. **Verify.** Run `./gradlew assembleDebug` and `./gradlew lintDebug`; fix anything you caused. If an emulator is available, reinstall and screenshot every screen in light and dark mode, and on a small (~360dp wide) and normal phone. Tap through every button, list, map, and form to confirm they still work. Report anything you couldn't verify.

## The Apple-style checklist (translated to Android)

### Typography
- Font: SF Pro can't be bundled in an Android app, so use **Inter** (add to `res/font` via Android Studio's Downloadable Fonts or the .ttf) or keep the system sans-serif. Set it once on the theme (`android:fontFamily` and `fontFamily`).
- Body: 17sp, line height ~24sp (`android:lineHeight` or `lineSpacingExtra`), regular weight.
- Large titles: 28–34sp, bold/semibold, slight negative letter spacing (`android:letterSpacing="-0.02"`). Section headers 20–22sp semibold. Captions 13sp secondary color.
- 3–4 text sizes per screen max. Hierarchy from size and weight, not from color or decoration.
- No ALL CAPS: set `android:textAllCaps="false"` on buttons and tabs (Material buttons default to caps in older themes).
- Always `sp` for text so system font scaling works.

### Color
- Mostly neutral. Text `#1D1D1F`, secondary text `#6E6E73`, background `#F2F2F7` (grouped screens) or `#FFFFFF`, cards/surfaces `#FFFFFF`.
- One accent: blue `#007AFF` for primary buttons, links, selected states, switches. (Pick another single accent only if the app has a strong brand color; still just one.)
- Dark mode (`values-night/colors.xml`): background `#000000`, surface/cards `#1C1C1E`, text `#FFFFFF`, secondary `#8E8E93`, accent `#0A84FF`, separators `#38383A`.
- Status colors only where status is shown: red `#FF3B30`, green `#34C759`, orange `#FF9500`.
- Remove extra accent colors, gradients, and tinted backgrounds that don't carry meaning. Make the status bar match the background (light status bar icons off in light mode, `windowLightStatusBar`).

### Spacing & layout
- 8dp grid: margins/paddings are multiples of 8 (4dp allowed for tight spots). Screen side margins 16–20dp.
- Generous vertical space between groups (24–32dp), tighter inside a group (8–12dp).
- Group related rows into rounded "inset grouped" cards on a `#F2F2F7` background (like iOS Settings) instead of using lots of divider lines.
- Align text to a consistent left edge. Don't center long paragraphs.
- One main action per screen.

### Components
- **Primary button:** `MaterialButton`, accent background, white text, 17sp semibold, height 50dp, `app:cornerRadius="12dp"` (or fully rounded `25dp` for pill), no elevation/shadow, `textAllCaps=false`. Full width at the bottom of forms.
- **Secondary button:** `Widget.Material3.Button.TextButton` (or `MaterialComponents` equivalent) in accent blue, no background.
- **Cards:** `MaterialCardView`, `app:cardCornerRadius="16dp"`, `app:cardElevation="0dp"`, `app:strokeWidth="0dp"`, white on gray background (`#1C1C1E` on black in dark mode).
- **List rows:** min height 48–56dp, 16dp horizontal padding, title 17sp + optional 15sp secondary line, trailing chevron (`>`) in secondary color for rows that open something. Hairline separators (`1px`/`0.5dp`, `#E5E5EA` light / `#38383A` dark) inset to the text start, only between rows inside a group.
- **Toolbar / app bar:** flat (elevation 0), same color as background, large left-aligned title on top-level screens (`CollapsingToolbarLayout` with a large title, or a 34sp title view), regular 17sp centered/left title on detail screens. No colored app bar.
- **Text fields:** `TextInputLayout` filled or outlined with 12dp corners, light gray fill `#F2F2F7` (or `#1C1C1E` dark), no underline-only style, accent-blue focus, 48dp+ height, hint/label in secondary color.
- **Switches/checks:** accent blue when on.
- **Dialogs & bottom sheets:** 14–20dp corner radius, plenty of padding, actions as text buttons, destructive action in red.
- **Bottom navigation (if any):** white/black translucent-looking surface, accent for selected, gray for unselected, labels always visible, no colored bar.
- **Icons:** one consistent set (Material Symbols "Rounded" or "Outlined" works best), 24dp, tinted with text or accent color.
- **Map screens (osmdroid):** keep the map full-bleed; float controls in small white rounded cards with soft shadow; don't cover the map with colored bars.
- **Images (Glide):** rounded corners 12–16dp (`ShapeableImageView` with a shape appearance), consistent aspect ratios.

### Motion
- Subtle and fast: 200–300ms, standard decelerate easing. Use default activity/fragment transitions or a simple fade/slide; nothing bouncy.
- Keep ripples but make them subtle (default ripple on transparent backgrounds is fine).
- Respect "Remove animations" (animator duration scale 0) — don't hard-code long delays.

### Copy
- Short, confident titles (1–4 words). Sentence case, not Title Case Everywhere.
- Plain verbs on buttons ("Save", "Take photo", "Find location"), not "Submit" or "Click here".
- Cut filler and exclamation marks. Error messages say what happened and what to do.

### Accessibility (non-negotiable)
- Text contrast at least 4.5:1 (3:1 for large text), in both light and dark mode.
- Touch targets at least 48×48dp (Android minimum).
- Every image/icon button has `android:contentDescription` (or `@null` if decorative).
- Text in `sp`, layouts that still work at the largest font size (no fixed heights that clip text).

### Things to remove
- Colored app bars, heavy card shadows/elevation, thick borders, multiple competing accent colors, gradients, ALL CAPS buttons, cramped 4dp spacing everywhere, hard-coded colors in layouts, more than one primary button per screen.
