# Changelog

All notable changes to this project are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Fixed

- After a voice note, the message at the bottom named the amount in rubles
  when the purchase was in the account's own currency, e.g. lari. It read the
  accounts as they were when the screen opened instead of the current ones.

## [0.15.0] — 2026-10-02

### Added

- **Main currency** (Settings → Where I am). The big numbers and totals —
  Home, Accounts, Insights, goals, "Not sure" and the snackbars — can be shown
  in any shown currency instead of rubles. The books stay in rubles: a
  non-ruble figure is the ruble amount at today's display rate. Amounts in an
  account's own currency stay in it, and the hourly rate stays in rubles. It
  is saved in backups; older backups restore with rubles.

### Fixed

- The interest forecast on a foreign-currency debt was written in rubles; it
  is now in the account's currency.

## [0.14.1] — 2026-10-02

### Fixed

- **Changing the local currency now changes where a new purchase comes
  from.** The entry form started in the new currency but kept charging the
  account used last, so the change seemed to do nothing. It now picks the
  account the way voice does: the last one if it is in that currency, else a
  free-money account in it, else the last one with the charge converted. The
  setting also says what it is for.

### Added

- Scenario tests that change each setting in the real UI and check where it
  shows, run on an emulator in isolated storage (see CONTRIBUTING.md).

## [0.14.0] — 2026-10-02

### Changed

- **Runs on Android 8.0 (API 26) and newer**, not only Android 16. Where an
  older version lacks something, there is a fallback:
  - Voice notes are recorded as AAC on Android 8–9 (Opus needs Android 10)
    and sent to Gemini with the matching type. Notes already waiting in the
    queue are picked up in either format.
  - The app's own language is kept by the system from Android 13 and in the
    app's settings before that, and survives a restart either way.
  - Notifications ask for permission only on Android 13 and newer, where the
    permission exists; before that they are simply shown.
  - The Quick Settings tile and the widget work on every supported version;
    the widget has its own rounded corners before Android 12.
  - The keyboard slides the form's action bar up with it from Android 11;
    on older versions the form simply resizes.

## [0.13.1] — 2026-10-02

### Changed

- The eating-out category is called «Кафе» in Russian, so its name fits a
  selected category tile instead of ending in «Еда вне до…». Records saved
  under the old name still show as this category.
- In English, "Phone and internet" is now "Phone", for the same reason.

## [0.13.0] — 2026-10-02

First public release.

### Added

- **Every amount in several currencies.** Rubles are the base; you pick the
  rest, and any amount shows in all of them at once, at the rate your money
  actually cost rather than the official one.
- **A double-entry ledger with a ruble cost basis per account.** Money leaves
  an account at its average cost, and a transfer hands that cost to the
  account it lands on, so lari withdrawn from a dollar card cost what the
  dollars cost.
- **Learned exchange markup.** Display rates are the CBR rate × (1 + markup),
  and every ruble-to-currency transfer updates the markup. Exchange loss
  against the CBR rate is tracked per transfer.
- **Card purchases in another currency**, estimated at the CBR cross rate
  + 2 % and marked ≈ until corrected.
- **Voice entry** through Google Gemini (`gemini-3.5-flash-lite`) with your
  own API key: several items in one phrase, transfers with two amounts, dates
  like "yesterday". From the mic button, a home-screen widget, a Quick
  Settings tile and the app shortcut. Notes recorded offline or before a key
  is set wait in a queue. The model only extracts what was said; the app
  computes every number.
- **Safe to spend today**: spendable money minus payments due before payday,
  spread over the days left, with a pace check against the start of the
  period.
- **"Not sure"**: a purchase as hours of work, a share of the main goal and
  days of budget, then buy, think about it (a wishlist with a 24 h, 3 day or
  1 week timer and a reminder) or skip it (the money goes to the goal).
- **Goals**, linked to an account plus whatever you skipped buying.
- **Debts**: credit cards and loans with a rate, payment day and grace period,
  reminders, months left and total interest, an early-repayment calculator
  (shorter term or smaller payment, compared with a savings account), and a
  hint on which debt to pay first.
- **Savings interest forecast** on the monthly minimum balance.
- **Insights**: spending by day and by category, average per day, income and
  exchange loss, over a week, a month, since payday or a custom period.
- **Reconcile** against the bank, with a Sunday-evening reminder.
- **Backup** of everything except the API key to one JSON file, and restore.
- **Russian and English**, switchable in the app.
- Material 3 Expressive with a light graphite palette and a dark one; gold
  marks only the one action to take. Tabs swipe and share a floating toolbar.
- 36 unit tests over the money maths, the budget, debts, goals and the voice
  mapper.

### Notes

- All data stays on the phone. No account, no server, no analytics. The only
  network traffic is voice notes to the Gemini API with your own key and the
  daily CBR rates.
- The API key is encrypted with an Android Keystore key, sent as a header,
  never in a URL, and never included in backups.
- Spending is never recorded from bank notifications. Saying the purchase out
  loud is the point.

[Unreleased]: https://github.com/shamil-aminov/golda/compare/v0.14.0...HEAD
[0.15.0]: https://github.com/shamil-aminov/golda/releases/tag/v0.15.0
[0.14.1]: https://github.com/shamil-aminov/golda/releases/tag/v0.14.1
[0.14.0]: https://github.com/shamil-aminov/golda/releases/tag/v0.14.0
[0.13.1]: https://github.com/shamil-aminov/golda/releases/tag/v0.13.1
[0.13.0]: https://github.com/shamil-aminov/golda/releases/tag/v0.13.0
