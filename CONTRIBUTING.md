# Contributing

Thanks for taking an interest. Bug reports and pull requests are both welcome.

## Reporting a problem

Open an issue using the bug report form. Please leave out your real balances,
account names and API key. A few made-up numbers that reproduce the problem
are just as useful. For voice entry, the exact phrase you said and what Golda
recorded instead usually pin it down.

## Setting up

Android Studio, or JDK 21 or newer with the Android SDK. The JDK bundled with
Android Studio (`jbr`) works. The app needs a phone or emulator with
Android 8.0 (API 26) or newer; check changes on an old version (API 26) as well
as a current one.

```bash
./gradlew test lintDebug assembleDebug
```

CI runs exactly these on every push and pull request.

## Scenario tests (instrumented, emulator only)

`app/src/androidTest` drives the real UI: change a setting, go back, and check
that it changed what it should (the local currency, the shown currencies,
payday, rate and tax, markup, the language, and that settings survive a
restart). They run in a test application whose database is in memory and whose
settings live in a separate file, so they never see the app's own data.

**Run them on an emulator only.** Gradle installs the app on *every* connected
device and uninstalls it afterwards, and an uninstall wipes that phone's data.
The tests themselves refuse to start anywhere but an emulator, but that cannot
stop Gradle's own install and uninstall, so always name the emulator:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
```

Use the emulator's serial from `adb devices`. Never run the task without
`ANDROID_SERIAL` while a phone is plugged in.

## Trying it without your own data

Debug builds have two shortcuts that fill the app with a made-up person's
accounts, a couple of weeks of spending, goals and a wishlist. Both **erase
everything in the app first**, so never run them on an install that holds
real data:

```bash
adb shell am start -n sh.aminov.golda/.MainActivity --ez golda.demo true     # accounts only
adb shell am start -n sh.aminov.golda/.MainActivity --ez golda.samples true  # accounts, operations, goals, wishlist
```

To live with your changes next to the real app, build the `dev` variant:
`./gradlew assembleDev` gives a release build that installs as
`sh.aminov.golda.dev`, named "Golda dev" with a graphite icon, with its own
data. The demo shortcuts above work in debug builds only; to try them next to
a real install, add `applicationIdSuffix = ".demo"` to the `debug` build type
locally and don't commit that line.

A voice note can be fed in without speaking: copy a `.wav`, `.ogg` or `.aac` file,
named with its recording time in milliseconds, into the app's `files/voice/`
folder with `run-as`, then start the activity with
`--ez golda.voiceQueue true`.

## Guidelines

- **Match the code around you.** Comments explain *why*, not *what*. Names say
  what things are. The existing files are the style guide.
- **`domain/` stays free of Android.** Money maths, rates, the budget, debts and
  the voice mapper import nothing from `android.*` or Compose. That is what
  lets plain JVM tests cover them. A change there comes with a test.
- **Money is `Long` minor units**, never `Double`. Rates are the only
  floating-point numbers, and they are applied once, at the edge.
- **The model only extracts; the code computes.** The Gemini prompt asks for
  what was said, and nothing more. Conversions, card-charge estimates, hours
  of work and budget shares belong in `domain/`, where they can be tested.
- **Both languages, always.** Every piece of text goes through
  `tr("русский", "English")`. Add both halves in the same change.
- **One gold thing per screen.** Gold marks the one action to take. Anything
  else picked or highlighted is graphite (see `ui/Colors.kt`).
- **Nothing is recorded automatically.** Reading bank notifications or SMS is
  out of scope on purpose. Saying the purchase is what makes you notice it.
- **Room schema changes** need a version bump and a migration (usually an
  `AutoMigration`), and the exported schema in `app/schemas/` must be
  committed with them.

## Release signing

See [docs/RELEASING.md](docs/RELEASING.md). Never commit a keystore, a
`keystore.properties` file or a password.

## Commit messages

Start with a short summary line in the imperative, like "Carry the ruble cost
through ATM withdrawals". If the reason isn't obvious, add a paragraph
explaining why.
