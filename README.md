# Airgest

Airgest is an Android app for hands-free control of an Android tablet. This repository now contains a working MVP project with voice-command actions, on-device face gesture detection, and an Accessibility Service that performs taps/swipes and Home/Back navigation.

## Requirements

- Android Studio with JDK 17
- Android SDK 35
- Android 8.0+ device; developed for Android 15 / ARM64 tablets
- Internet connection for Gradle dependency resolution and Android's available speech recognition service

## Build and install

1. Open this repository in Android Studio and let Gradle sync.
2. Connect your tablet with USB debugging enabled, or create an APK from **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
3. Install and launch Airgest.

The repository currently does not include Gradle wrapper binaries. Build through Android Studio, or add a wrapper from Android Studio/Gradle before using `./gradlew`.

## First-use setup

1. Open Airgest and tap **Accessibility**.
2. Enable **Airgest gesture control** in Android Accessibility settings. Android will show its standard sensitive-permission warning because this service can perform screen gestures.
3. Return to Airgest. Tap **Speak a command** and try “tap”, “scroll down”, “swipe left”, “go home”, or “go back”.
4. Enable **Enable gesture actions** and grant camera permission to use face gestures.

## Face gesture mapping

- Smile: tap the center of the display.
- Turn head left/right: swipe left/right.
- Actions are disabled until you explicitly turn on **Enable gesture actions**.
- A cooldown limits repeated actions; camera processing uses ML Kit on-device and frames are not stored by Airgest.

## Voice controls and AI

Voice recognition uses Android's `RecognizerIntent`; actual recognition availability/offline support depends on the speech service installed on the tablet. Airgest's command interpretation is local and requires no paid AI API or subscription. The MVP does not include a general conversational generative-AI model.

## Limitations

- This MVP has voice and face gestures; hand-landmark tracking is not implemented yet.
- Taps currently target the center of the screen. A movable cursor, pinch-based mouse control, custom wake word, and named-app launching are not implemented.
- Gestures require the Airgest Accessibility Service to be enabled. Availability and behavior may vary by Android tablet manufacturer.
- Test in a safe app before relying on gestures; facial movement can trigger actions when gesture control is enabled.
