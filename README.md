# Blinds OS (Android launcher)

An iOS-inspired home screen replacement for Android with a heavy "liquid glass" look and a lot of customization.
Kotlin + Jetpack Compose, no third-party libraries.

## Get the APK

1. Create a GitHub repo and push this folder to it (branch `main`).
2. Open the repo's **Actions** tab. The **Build Blinds OS APK** workflow runs on every push
   (or press **Run workflow**).
3. When it finishes, open the run and download the **BlindsOS-apk** artifact. Unzip it and install the `.apk`.
4. Press Home, choose **Blinds OS**, then **Always**. Or: Settings > Apps > Choose default apps > Home app
   (on One UI), or use *Customize > System > Set Blinds OS as the default home app*.

The APK is signed with the keystore committed in `app/blinds.keystore`, so every new build installs
over the previous one without uninstalling. (It's a hobby key. Swap it for your own before publishing anywhere.)

## What's in it

- **Liquid glass** on the dock, clock card, page dots, App Library, menus, search and settings.
  Each panel blurs its own slice of the wallpaper, then adds frost, a lens-like bright edge, a specular bloom
  and a rim light that follows your chosen light angle and shifts as you tilt the phone.
  (The blur itself needs Android 12+. Older phones get the same glass without the blur.)
- **iOS-style home screen**: icon grid pages, dock, page dots, springy icon presses, jiggle edit mode with
  tap-to-swap and remove badges, App Library page (apps grouped by category), Spotlight-style search
  (swipe down), long-press quick actions menu (shortcuts, add to dock, hide, app info, uninstall).
- **Customize sheet** (long-press the home screen > Customize): blur, frost, tint colour, edge light, depth,
  light angle, tilt lighting, icon shape (squircle / circle / rounded / square), icon size, gloss, themed icons,
  grid columns and rows, dock size, corner radius, clock widget style, wallpapers (6 animated presets or your own photo),
  parallax, light/dark/system theme, accent colour, haptics.

## Using the real Blinds OS logo

The launcher icon is a placeholder (blind slats on a blue to red gradient). To use the real logo, replace
`app/src/main/res/drawable/ic_launcher_foreground.xml` with a `ic_launcher_foreground.png` (delete the .xml),
and optionally replace `ic_launcher_background.xml` with a solid colour or image. Keep the important part of the
artwork inside the middle 66% of the square.

## Not built yet

- Drag-to-move icons (use tap one icon, tap another to swap while editing)
- Real Android widgets (`AppWidgetHost`)
- Notification badges
- Icon packs
