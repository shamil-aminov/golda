# Changelog

All notable changes to this project are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.17.1] — 2026-11-03

### Added

- **Deeper reviews.** Reviews have their own model, gemini-3.8-flash by
  default (gemini-4-argon with a paid key), apart from the fast one that hears
  purchases. Gemini now gets the period before to compare with, spending by
  weekday and time of day, the purchases that repeat, the regular payments and
  each purchase's time, all worked out by the app, and is asked for patterns
  with their numbers and an experiment or two for the next period.
- A `dev` build to live with next to the real app: "Golda dev", a graphite
  icon, its own data (`./gradlew assembleDev`).

### Fixed

- The mosaic shows at most four categories of five per cent or more; the
  small ones go together into one grey tile, "Ещё 6 категорий", always in the
  far corner, and the line under it names three at most.
- "15 тысяч" is read as fifteen thousand when the app checks the model's
  sum against the words; before, "куртка 15 тысяч рублей" was booked as 15 ₽.
  A transfer the model leaves without a sum takes the one the words say.
- The mosaic's corners follow each tile's size, so small tiles stay tiles
  instead of turning into pills.
- A spoken transfer with one sum ("погасил кредитку 15 тысяч с карты") is
  booked: when the model gives it as what arrived, between accounts in one
  currency the app takes it as both. Found by the Gemini evals, which now also
  score what the app books after its own checks.
- Settings keeps its place while Theme is open over it, and forgets it once
  closed. Pages slide in from the right going deeper and back out going back,
  as in Android's own settings.
- The entry form no longer cuts the first account card in half: the row moves
  only when the picked card would sit past the edge, and just enough to show it.

## [0.17.0] — 2026-11-03

### Added

- The demo lives on lari for the month: the rent is paid in dollars, so the
  spending card lasts and the screenshots show an ordinary month.
- **Gemini evals**, run by hand with a key: 32 spoken-style phrases through
  the app's own prompt and reply reading, every field scored.
- **Screenshot tests** (Roborazzi) of home, accounts, insights, a new
  expense and settings, light, dark and the clear theme; and the agreed
  design rules as tests.
- The entry form opens its account cards on the picked one.
- **Property tests** (Kotest) for the money rules: transfers make and lose
  nothing, an account spent to zero keeps no ruble cost, amounts read back as
  shown, debts and the spending period hold for any balance and day, the
  mosaic fills its area. Mutation score of the domain from 74 % to 84 %, and
  CI now fails below 82 %.
- **Themes.** Settings → Тема: eleven ready palettes (two for colour-vision deficiency) and a generator that
  builds a whole theme from one colour (a hue slider, the wallpaper's colour,
  or a surprise), a style (calm, vivid, bold, mono), light or dark, and a
  contrast; generated themes can be kept. Category and pace colours keep
  their hues and take the theme's depth.
- **Insights mosaic.** Categories as tiles sized by their share.
- **Gemini tags.** Purchases and income get a precise word from Gemini
  ("кафе", "такси", "подписка"), finer than the category. It shows in the
  operation's card, goes into the export and into reviews. Old records are
  tagged too, in one request; a note seen before reuses its tag.
- **Regular payments**, under the accounts. Two days before a subscription is
  charged a notification asks whether it is still needed; no amount means a
  free trial. A payment due in the next two days shows on
  the home screen under the hero.
- **Reviews.** Insights has an ice-blue "Review" button: Gemini reads the
  period's spending and writes a short Markdown note on where the money went
  and what could be cut. Past reviews are kept, listed and backed up.

### Removed

- **The Budget group in Settings.** The spending period opens from the home
  tile; each category already belongs to its bucket, so there is nothing to
  set.
- **Account groups.** One bank, one account: accounts are one list, and the
  account form no longer asks for a group.

### Changed

- **The coin.** The launcher icon is drawn the way a minted gold coin is: one gold hue,
  facets from cream to deep amber, the shaded ones fading toward their points
  (`tools/coin_ref.py`).

- **Design review fixes.** An account's page leads with "Изменить счёт" as
  the filled button, Сверить and Погасить досрочно stay small and tonal, and
  a band keeps rows from running under the clock. "Разборы" sits at the
  account title's height; its button is off while Gemini writes, and the
  empty page says when a key is needed. The settings and currency headers
  get a hairline once folded; the currency search lines up with the rows,
  picked currencies keep the white card. The third date choice reads
  "Дата"; field labels stay 14 sp; the early repayment results scroll clear
  of "Готово". The theme in use is filled like any choice, the picked hue
  carries a check, the preset row ends on a gap, and the theme's segmented
  rows sit 8 dp from the edge like Insights'. Category colours keep out of
  the review button's ice blue.
- **Theme presets stay put.** Picking one off to the side no longer swings
  the row back; half a chip shows at the edge, so the row plainly scrolls.
- **Account cards look like cards** in the entry form and the period sheet:
  a bank card's 1.586 : 1, the name at the top and the balance at the bottom.
- **Wording.** One name for voice items, "голосовые"; "поправка" for a
  reconciliation's difference; "Карта трат" for the account switch;
  "За 30 дней" on Insights; "Обновляю" while rates update; shorter
  onboarding lines; no full stops in one-line notices; English fixes
  ("Exchange losses", "Total interest", "Credit card", "Balance now" and
  others).

- **Back is at the bottom** on Settings and an account's page, as on the
  forms and reviews; the account page has edit next to it.
- **Insights** shows the total with the three buckets as filters, categories
  as bars and the days; the donut is gone.

- **"Safe to spend today" is the spending card's money over a period**, not
  money until payday. The period starts at the card's last top-up and lasts
  a month, or a week, two, or until a date you pick (Settings → Spending
  period, or tap the period on Home).
- **Burn from the balance.** Home shows how much a day actually leaves the
  card since the period began, reconciliations included, and the day the
  money runs out at that pace.
- **Buckets, four categories each.** An expense is everyday (food, transport,
  household, treats), regular (housing, phone, subscriptions, loans) or big
  (shopping, travel, health, other). Earlier categories move over by
  themselves.
- **A new entry form.** The bucket picks its four categories; currency, card
  and date are buttons on the form itself; cards look like cards and scroll
  sideways. "What it was" picks the category from earlier entries or, with a
  Gemini key, from the words; such a pick is outlined until you tap one. Back
  and Save sit together at the bottom.
- **Inter** is bundled: every currency sign comes from one font, so ฿ no
  longer shifts the line.
- Accounts says what the dearest debt's interest costs a month.
- **Reconciling a shortfall books it as everyday spending** "Not recorded"
  by default. Switch it off for a plain adjustment.
- Insights split the period into the three buckets; the bars and the daily
  average are everyday spending only.
- Voice: the amount is checked against the numbers in the transcript.
  "80,84" read as 8,84 or "10.50" as 1 050 is put right when the phrase has
  one amount, and marked ≈ with "check the amount" when it has several. An
  item left without a category takes the category its note had last time.
- A purchase in a currency other than the account's also shows its price in
  every shown currency, not only the card's charge.
- Pitest mutation testing of the domain: `./gradlew pitest`.

### Removed

- Income (rate, tax, hours, payday), "Not sure", goals and the wishlist.
  Their data stays in backups, and old wishlist reminders are cancelled.
- Monthly payments typed in settings: regular spending is counted from what
  is recorded.

## [0.16.0] — 2026-10-05

### Changed

- **Portrait only.** The app no longer turns sideways, and switching night
  mode no longer closes an open form.
- **Voice notes that fail are kept, not retried forever.** Without network
  or a key a note waits as before. If Gemini is busy it is tried at the next
  three starts; if Gemini refuses it or its reply can't be read, it is set
  aside at once. Settings → Voice shows "Not worked out: N" with Try again
  and Delete, so a purchase you already entered by hand is never booked
  twice. Saving a new key tries them again.
- Leaving the app mid-recording stops the recording and keeps nothing.
- "Buy" on a reached goal pays from the goal's own account when it holds
  enough.
- Reconciling a credit card or a loan takes the amount owed, as the bank
  shows it: "15 000" means −15 000.
- A payment made before its due date is no longer set aside again.
- Back on Accounts, Goals or Insights returns to Home; back in the setup
  goes to the previous step.
- A wish's reminder opens that wish in "Not sure".
- Picking another card keeps a purchase in the local currency (48,5 ₾
  charged to the dollar card); swapping a transfer's direction keeps the
  amounts with their currencies.
- "Buy" in "Not sure" is greyed out when there is nothing to pay from.

### Fixed

- After a voice note, the message at the bottom named the amount in rubles
  when the purchase was in the account's own currency, e.g. lari. It read the
  accounts as they were when the screen opened instead of the current ones.
- An unexpected reply from Gemini could crash the app, and then crash it on
  every start until its data was cleared.
- Typing or pasting a very long amount crashed the app.
- "Safe to spend today", "Today/Yesterday" and the days to payday stayed on
  yesterday after midnight until something changed; they now turn over at
  midnight, on return to the app and when the clock changes.
- "Yesterday" in a note understood a day later meant the wrong day.
- Editing an old transfer, even only its note, changed the markup, its
  exchange rates and its ruble value.
- Switching a new expense to Transfer and back changed its currency.
- A paid-off credit card or loan still reserved its monthly payment, and a
  small debt reserved more than was owed.
- "Since payday" started on the wrong day for paydays on the 29th to 31st.
- Income said in another currency lost the amount said and was not marked
  ≈; estimated charges had no ≈ on the account's page.
- Undo after "Buy" left the wish marked bought, and undo kept the markup the
  transfer had taught.
- Undoing a delete after its account was deleted crashed the app.
- A backup that failed to restore left the app empty; restoring now applies
  the backup's reconcile-reminder setting and its wishes' reminders.
- "Erase everything" kept voice notes not yet worked out.
- "Since a custom date" could show the last week after the app was restored.
- Only the first "I want to buy" of a note opened; a partly unclear note now
  says so.
- Clearing a number in the setup kept its last digit; negative rates were
  accepted.
- English: "next on Today" read oddly, and payments due tomorrow showed a
  date.

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

[Unreleased]: https://github.com/shamil-aminov/golda/compare/v0.17.1...HEAD
[0.17.1]: https://github.com/shamil-aminov/golda/releases/tag/v0.17.1
[0.17.0]: https://github.com/shamil-aminov/golda/releases/tag/v0.17.0
[0.16.0]: https://github.com/shamil-aminov/golda/releases/tag/v0.16.0
[0.15.0]: https://github.com/shamil-aminov/golda/releases/tag/v0.15.0
[0.14.1]: https://github.com/shamil-aminov/golda/releases/tag/v0.14.1
[0.14.0]: https://github.com/shamil-aminov/golda/releases/tag/v0.14.0
[0.13.1]: https://github.com/shamil-aminov/golda/releases/tag/v0.13.1
[0.13.0]: https://github.com/shamil-aminov/golda/releases/tag/v0.13.0
