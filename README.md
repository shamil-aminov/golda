<p align="center">
  <img src="docs/icon.svg" width="160" alt="Golda">
</p>

<h1 align="center">Golda</h1>

<p align="center">
  <b>A voice-first money tracker for people who earn in one currency and live in another.</b>
</p>

<p align="center">
  <a href="https://github.com/shamil-aminov/golda/actions/workflows/ci.yml"><img src="https://github.com/shamil-aminov/golda/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://github.com/shamil-aminov/golda/releases/latest"><img src="https://img.shields.io/github/v/release/shamil-aminov/golda?label=release&color=D9A93E" alt="Release"></a>
  <a href="https://github.com/shamil-aminov/golda/releases"><img src="https://img.shields.io/github/downloads/shamil-aminov/golda/total?label=downloads&color=52525B" alt="Downloads"></a>
  <a href="#requirements"><img src="https://img.shields.io/badge/Android-8.0%2B-52525B?logo=android&logoColor=white" alt="Android 8.0+"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/shamil-aminov/golda?color=D9A93E" alt="License: MIT"></a>
</p>

<p align="center"><i>Русская версия: <a href="README.ru.md">README.ru.md</a></i></p>

<p align="center">
  <img src="docs/screenshots/home.png" width="24%" alt="Home: safe to spend today">
  <img src="docs/screenshots/insights.png" width="24%" alt="Insights">
  <img src="docs/screenshots/entry-dark.png" width="24%" alt="A new expense, dark theme">
  <img src="docs/screenshots/accounts-dark.png" width="24%" alt="Accounts, dark theme">
</p>
<p align="center"><sub>Demo data, 0.17.5. Home and insights in the light theme; a new expense and accounts in Midnight.</sub></p>

---

Golda is a personal money tracker for Android. It is for one person on one
phone. Income arrives in rubles, but you pay in lari, dollars or baht, after a
chain of transfers, card conversions and ATM withdrawals. Golda shows what each
purchase really cost you in rubles, at the rate you actually got rather than
the official one.

You record a purchase by saying it: "shawarma 15 lari". That is deliberate.
Golda never reads your bank notifications, because saying the purchase out
loud is the moment you notice you are spending.

## Features

- **Every amount in several currencies.** Pick any currencies the CBR quotes,
  from a searchable list, and every amount shows in all of them:
  `15 ₾ ≈ 530 ₽ · 5,8 $`.
- **Main currency.** Big numbers and totals can be shown in dollars, lari or
  any shown currency. The books stay in rubles; a non-ruble figure is the ruble
  amount at today's display rate.
- **Real cost, not the official rate.** Each account remembers what its money
  cost in rubles, and spending and transfers carry that cost along.
- **Voice entry.** Tap the mic, the home-screen widget, the Quick Settings tile
  or the app shortcut. Then say one or more purchases, an income or a transfer
  with two amounts. Notes recorded offline, or before you add an API key, wait
  in a queue.
- **Card purchases in another currency.** For "48.5 lari" paid from a dollar
  card, the charge is estimated at the CBR cross rate + 2 % and marked **≈**
  until you correct it.
- **Safe to spend today.** Put money on the card you spend from for a week,
  two or a month: Golda splits what is left over the days left. Income does
  not come into it.
- **Burn from the balance.** How much a day actually leaves the card since
  the top-up, and the day it runs out at that pace: "Spending 70 ₾ a day:
  runs out November 20, 6 days early". Reconcile the card and it holds even
  for days you wrote nothing down.
- **Three buckets.** Everyday, regular (rent, phone, subscriptions) and big
  (clothes, travel) spending, apart in insights.
- **Regular payments**, under the accounts, with the day each is charged. Two
  days before a subscription is charged you're asked whether you still need
  it; a payment due soon shows on the home screen.
- **Gemini tags.** Each purchase gets a precise word ("кафе", "такси",
  "подписка") on top of its category.
- **Honest reconciling.** Less money on an account than recorded is booked as
  everyday spending "Not recorded".
- **Debts.** Credit cards and loans have a rate, a payment day and a grace
  period. You get reminders, a payoff estimate, an early-repayment calculator
  and a hint on which debt to pay first.
- **Insights.** Spending by category as a mosaic sized by share, by day as
  bars stacked by category, the average per day, income, and how much currency
  exchange cost you against the CBR rate.
- **Reviews.** Gemini checks a period's spending like a careful bookkeeper:
  what to look into (a payment that looks doubled, fees, exchange losses, a
  regular payment that came out different), or "nothing unusual", and the
  regular payments still ahead against what is on the accounts. You can ask it
  follow-up questions ("how much would I save without taxis?"). Reviews and
  the talks under them are kept and backed up.
- **Reconcile** any account against the bank, with a Sunday reminder.
- **Backup** everything to one JSON file and restore from it.
- **Themes.** Twenty-two ready themes, from calm to bold, each with a light and
  a dark half, one switch for light, dark or the system's, and a generator that
  builds a whole theme from one colour, a style and a contrast. "Ясная" is made
  for colour-vision deficiency; every theme keeps text at 7:1 contrast. Category
  colours follow the theme.
- **Russian and English.** Material 3 Expressive, one card per group, one
  accent for the one action to take.

## How it works

```mermaid
flowchart LR
    A["🎤 'coffee 8 lari'"] --> B["Opus note<br/>queued on the phone"]
    B --> C["Gemini<br/>speech → JSON"]
    C --> D["Golda's code<br/>accounts, rates, cost"]
    D --> E["Saved, with Undo"]
```

The model only extracts what was said. Every number is computed by the app:

- **Double-entry ledger.** Every operation is a set of postings. Each posting
  holds an amount in the account's currency and its cost in rubles. The sum
  of an account's postings gives both its balance and its ruble cost basis.
- **Average cost.** Money leaves an account at the account's average ruble cost
  per unit. A transfer passes that cost on to the receiving account. Dollars
  bought at 92 ₽ and taken out of an ATM as lari make those lari cost what the
  dollars cost.
- **Display rates** are the CBR rate × (1 + markup). The markup starts at 10 %
  and is learned from your own ruble-to-currency transfers: 46 000 ₽ for 500 $
  at a CBR rate of 83.25 means +10.5 %.
- **Exchange loss** compares each currency exchange with the CBR rates of both
  currencies on the day it happened.
- Money is stored as `Long` minor units, never `Double`.

The full design, in Russian, is in [SPEC.md](SPEC.md).

## Requirements

- **Phone:** Android 8.0 (API 26) or newer. Voice notes are recorded as
  Opus from Android 10 and as AAC on Android 8–9.
- **For voice entry:** your own Google Gemini API key (see below). Everything
  else works without it.

The app is in English and Russian. It follows the system language and can be
switched in Settings.

## Getting started

1. **Download** the APK from the [latest release](../../releases/latest) and
   install it. Android will ask you to allow installs from your browser or
   file manager.
2. **Set up** income, currencies and accounts with their current balances.
   Rough numbers are fine; reconciling fixes them later.
3. **Get a Gemini API key** in
   [Google AI Studio](https://aistudio.google.com/apikey). Then open
   Settings (the gear on the Home tile) → **Gemini key** and paste it. The free
   tier is enough to try it, and with billing on, a voice note costs a tiny
   fraction of a cent.
4. **Tap the gold mic** and say what you bought.

## Privacy

- Accounts, operations and settings stay in the app's private storage
  on your phone. There is no server, no account and no analytics.
- Voice notes are sent to Google's Gemini API with **your own** key, only to
  be turned into text and numbers. Google's terms for the Gemini API apply to
  that audio.
- The key is encrypted with AES-GCM under an Android Keystore key. It is sent
  in the `x-goog-api-key` header, never in a URL, and is left out of backups.
- The only other network request fetches the daily exchange rates of the Bank
  of Russia from `cbr-xml-daily.ru`.

## Building

Requirements: Android Studio, or JDK 21+ and the Android SDK. The JDK bundled
with Android Studio (`jbr`) works.

```bash
./gradlew assembleDebug
```

Release builds, signing and publishing are described in
[docs/RELEASING.md](docs/RELEASING.md). Without a key, a release build is
signed with the debug key.

## Testing

```bash
./gradlew test
```

The unit tests need no device. They cover the ledger and cost basis, rates
and card-charge estimates, the spending period and its burn, buckets, savings
interest, debts and the prepayment calculator, analytics, how voice answers
turn into operations, and the amounts read back from the transcript.
Besides examples, property tests (Kotest) check rules on thousands of random
inputs: moving money never makes or loses any, an account spent down to zero
keeps no ruble cost, an amount as shown reads back to the same kopecks, the
mosaic covers its area without overlaps.

`./gradlew pitest` mutation-tests the domain against them: it breaks the code
one small way at a time and reports every break no test noticed. CI fails
below 82 % of breaks caught; the floor only goes up.

Screenshot tests draw the main screens on the JVM (Robolectric and Roborazzi)
with a fixed made-up life abroad and compare them with the images in
`app/src/test/screenshots`. After an intended change of the look,
`./gradlew recordRoborazziDebug` stores the new images;
`./gradlew verifyRoborazziDebug` fails on any difference. Design rules the
owner agreed on (no full stops in one-sentence texts, no ellipses, type from
the scale) are tests too.

Evals of what Gemini makes of speech run by hand, with your key: 32 phrases
in `app/src/test/resources/evals/phrases.tsv` (and any recordings in
`evals/voice/`) go through the app's own prompt and reply reading, and every
field is scored. `GEMINI_API_KEY=… ./gradlew testDebugUnitTest --tests "*GeminiEvalTest"`;
the report is in `app/build/evals.txt`.

Debug builds can fill the app with a made-up demo, which **erases everything
in the app first**. [CONTRIBUTING.md](CONTRIBUTING.md) explains how to run it
next to a real install safely.

## Project layout

```
domain/   Money maths with no Android dependency: rates, ledger, budget,
          interest, debts, the voice-answer mapper. Covered by JVM tests.
data/     Room database, DataStore settings, CBR rates, Gemini client,
          Keystore vault, backups, reminders, the demo fill.
ui/       Jetpack Compose screens, the toolbar and the palette.
tools/    coin.py, which draws the launcher icon.
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT](LICENSE). The icons are from [Iconoteka](https://iconoteka.com) (MIT,
see [third_party/iconoteka/LICENSE](third_party/iconoteka/LICENSE)), and the
colours follow Tailwind CSS's Zinc scale. Third-party components are listed in
[NOTICE](NOTICE).
