# Brand Assets

ChatMod Mobile's launcher identity uses the blue chat-bubble mark with green live-state dots.

## Android Launcher Icon

Checked-in Android resources:

- `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- `android/app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml`
- `android/app/src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml`
- `android/app/src/main/res/drawable/ic_launcher_foreground.xml`
- `android/app/src/main/res/drawable/ic_launcher_monochrome.xml`
- `android/app/src/main/res/values/colors.xml`

The manifest points `android:icon` and `android:roundIcon` at the adaptive launcher resources, while notification code can keep using the compact `ic_chatmod_mark` vector.

## Splash / Launch Screen

The launch theme uses `Theme.ChatModMobile.Launcher` with:

- `chatmod_launch_screen.xml` for the pre-Android-12 window preview.
- Android 12+ splash attributes under `values-v31/styles.xml`.
- A light control-room background matching the product UI.

`MainActivity` switches to the normal app theme before Compose setup so the splash/preview theme does not leak into the running app.

## Store Asset Gap

Google Play icon prep now has source and PNG assets:

- `marketing-assets/chatmod-play-icon.svg`
- `marketing-assets/chatmod-play-icon-512.png`

The Play Console upload itself remains a release-track task because it needs account access and final store-listing review.
