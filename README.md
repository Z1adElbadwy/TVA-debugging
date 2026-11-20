# TVA — Kotlin / Jetpack Compose port

Open this folder in Android Studio (Koala+), let Gradle sync, run on a device (minSdk 26, portrait).

**Status: NOT compiled.** The authoring environment had no Android SDK or Maven access, so expect a few compile errors
(imports, API signatures) on first build. Pure logic (`Logic.kt`, `Chess.kt`) has no Android dependencies and can be unit-tested directly.

Layout: Theme/Icons (design tokens, 1 CSS unit = screenWidth/878), AppState (items, overlay stack, persistence),
Tiles/Drawer/Sheets/Panels (screens), ChessGame + ChessUi, Mascot (sprite + AI bubble), Ai (chat/search/transcribe), HomeScreen, MainActivity.

Known approximations: settings row sizes, sub-sheet transition (simple slide-up), tile stagger / some micro-animations,
glitch skew, king-check fade, promotion dim only taps on the board to cancel.
