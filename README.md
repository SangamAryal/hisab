# Hisab

Split shared costs with flatmates, trips, couples and friends. Free, no ads,
no daily limits, open source.

- Add expenses, split equally, by exact amounts or by shares
- See who owes whom and the fewest payments to settle up
- Pay with eSewa, Khalti, UPI, PayPal, Venmo (amount pre-filled where possible)
- Repeating bills (rent, Wi-Fi) added automatically
- Friends join from a link, no sign-up
- Works offline and syncs later

## Project layout

| Folder | What |
|---|---|
| `composeApp/` | The app for Android and iOS (Kotlin, Compose Multiplatform) |
| `iosApp/` | iOS wrapper (XcodeGen spec + SwiftUI entry point) |
| `backend/` | The server: PocketBase schema, rules and hooks ([README](backend/README.md)) |
| `deploy/` | Scripts to run the server on a free VM ([guide](docs/DEPLOY.md)) |

## Run it locally

1. Backend: `cd backend && ./get-pocketbase.sh && ./pocketbase serve`
2. Android: open the project in Android Studio and run `composeApp`
   on an emulator (it talks to `10.0.2.2:8090`, your computer).
3. iOS (on a Mac): `cd iosApp && xcodegen && open iosApp.xcodeproj`, run on a
   simulator (it talks to `127.0.0.1:8090`).

Tests: `./gradlew :composeApp:testDebugUnitTest` (shared logic) and
`cd backend && npm test` (API and access rules).

Releasing: [docs/RELEASE.md](docs/RELEASE.md).

Latest Android APK in Google Drive: [docs/APK-TO-DRIVE.md](docs/APK-TO-DRIVE.md).

## License

MIT
