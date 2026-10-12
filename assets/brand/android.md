# Android logo assets

The Android app uses `pandora-logo-v2.png` as its brand mark. Earlier concepts are retained here for reference.

- `drawable-nodpi/ic_pandora.png` is a 512px transparent copy for the welcome screen and the launch window on Android 8–11.
- `pandora_launcher_foreground.png` centers the trimmed logo at 240px inside a 432px transparent canvas. This places 60dp of artwork inside Android's 108dp adaptive icon canvas.
- `pandora_launcher_monochrome.png` uses the dark contours and markings as an alpha mask for themed icons on Android 13 and later.
- The manifest uses the adaptive launcher resource for both standard and round icons. Android 12 and later use that resource for the platform splash screen too.

Regenerate the PNG resources from the repository root with ImageMagick:

```sh
magick assets/brand/pandora-logo-v2.png -resize 512x512 apps/android/app/src/main/res/drawable-nodpi/ic_pandora.png
magick assets/brand/pandora-logo-v2.png -trim +repage -resize 240x240 -gravity center -background none -extent 432x432 apps/android/app/src/main/res/drawable-nodpi/pandora_launcher_foreground.png
magick apps/android/app/src/main/res/drawable-nodpi/pandora_launcher_foreground.png '(' +clone -alpha extract ')' '(' -clone 0 -alpha off -grayscale Rec709Luma -threshold 55% -negate ')' -delete 0 -compose Multiply -composite -alpha copy apps/android/app/src/main/res/drawable-nodpi/pandora_launcher_monochrome.png
```

Launcher padding follows the [Android adaptive icon guidance](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive). Platform launch resources follow the [Android splash screen guidance](https://developer.android.com/develop/ui/views/launch/splash-screen).
