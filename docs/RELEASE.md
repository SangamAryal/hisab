# Releasing to the Play Store and App Store

## Android (Google Play, $25 once)

1. Create an upload key once and keep it safe (losing it is painful):
   `keytool -genkey -v -keystore hisab-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload`
2. Add a `release` signing config that reads it (not committed; see
   `.gitignore`), then `./gradlew :composeApp:bundleRelease -Phisab.apiUrl=https://your-server`.
3. Upload `composeApp/build/outputs/bundle/release/composeApp-release.aab` in
   the Play Console. Play requires a privacy policy URL and a data safety
   form: Hisab stores names, amounts and optional payment IDs, nothing else.

Until the upload key exists, release builds are signed with the debug key so
they install for testing, but Play will reject them.

## iOS (Apple Developer Program, $99/year)

1. `brew install xcodegen && cd iosApp && xcodegen && open iosApp.xcodeproj`
2. In the target's Signing & Capabilities, pick your team. Bundle id: `app.hisab`.
3. `hisab.apiUrl` in `gradle.properties` already points at the public server; change it if you run your own.
4. Product → Archive, then upload to App Store Connect / TestFlight.
