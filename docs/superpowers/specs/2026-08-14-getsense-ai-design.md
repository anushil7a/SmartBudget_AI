# getSense AI — Design Spec

**Date:** 2026-08-14
**Status:** Approved for planning

## Premise

Budgeting apps fail for a behavioral reason: manual entry is tedious enough that
people quietly stop. getSense AI removes the typing. It reads receipts with OCR,
categorizes them with a classifier that learns from corrections, and searches
history in plain language.

## Where the code actually is today

The repository already contains code for all three pillars. Two of them are hollow.

| Pillar | File | Real state |
|---|---|---|
| OCR | `OcrProcessor.java` | Runs. `extractAmount` returns the **highest** dollar figure anywhere in the text, which on a real receipt is frequently not the total. Merchant is "first line longer than 3 characters." No receipt image is stored. No confidence signal. |
| Classifier | `CategoryPredictor.java` | **Constructed nowhere.** No caller in the app. `learn()` — the entire "gets smarter" premise — is never invoked. `ReceiptScanActivity` instead uses `OcrProcessor.suggestCategory`, a duplicate keyword table. |
| NL search | `NaturalLanguageParser.java`, `OpenAIService.java` | Local regex parser plus an OpenAI call that ships the whole expense history in the prompt. `extractDateRange` maps "last month" to the 1st of the **current** month (line 115). The exact-amount fallback fires on stray digits. |

Three independent copies of the same category keyword table exist:
`OcrProcessor.java:198`, `CategoryPredictor.java:94`, `NaturalLanguageParser.java:61`.

`Expense` carries `amount, category, title, date, note`. There is no merchant, no
capture source, no confidence, no receipt path — so the app cannot show why a
category was chosen, cannot distinguish scanned from typed, and has nothing to
learn from.

The UI is stock Material 3 teal/amber with `MaterialCardView` around every
section. Logic sits in large activities (`SummaryActivity` 704 lines,
`MainActivity` 526, `activity_main.xml` 446).

## Decisions taken

1. **Classifier**: on-device multinomial Naive Bayes that learns from corrections.
   Not TFLite, not an LLM call.
2. **Search**: natural language compiles to a typed filter executed locally
   against Room. LLM is a fallback translator only.
3. **Visual direction**: dark instrument panel. **Dark only** — no light theme.
4. **UI stack**: Java + XML Views. No Kotlin, no Compose.
5. **Scope**: streak/badges and CSV preview are cut. Chat survives folded into
   Search. The scan reveal animation is kept.

---

## 1. `sense/capture` — reading receipts

`OcrProcessor` is replaced by `ReceiptParser` + `ReceiptStore`.

### Field model

```java
public class Field<T> {
    public final T value;              // null if not found
    public final float confidence;     // 0.0 – 1.0
    public final String sourceLine;    // the OCR line it came from
    public final Rect boundingBox;     // ML Kit line bounds, for the overlay
}

public class ReceiptExtraction {
    public Field<String> merchant;
    public Field<Double> total;
    public Field<String> date;         // yyyy-MM-dd
    public String rawText;
}
```

`Field.boundingBox` is what makes the scan reveal animation possible and is the
reason the parser must consume `Text.TextBlock`/`Text.Line` geometry rather than
`text.getText()`.

### Total extraction

Replaces "biggest number wins."

1. Walk lines bottom-to-top. Match total keywords in priority order:
   `grand total` > `total` > `amount due` > `balance due` > `subtotal`.
2. Take the currency amount on the matched line; if absent, take the first
   amount on the next line.
3. Reject a candidate whose line also matches `change`, `cash`, `tender`,
   `card`, `visa`, `mastercard`, `debit`, `credit`, `tip`, or `cash back`.
4. Prefer the **last** matching occurrence in the document.
5. If only `subtotal` matched and a tax line exists, total = subtotal + tax.
6. Confidence: 0.95 for an explicit `total` match, 0.75 for `amount/balance
   due`, 0.55 for a subtotal-derived total, 0.30 for the fallback
   (largest amount, current behavior).

### Merchant extraction

1. Consider only lines whose bounding box top is within the top 25% of the
   receipt's total height.
2. Score each: +2 for glyph height above the page median (store names print
   larger), −3 for matching a 5-digit zip, a phone pattern, or a street suffix
   (`st|street|ave|avenue|rd|road|blvd|suite|ste|#\d`), −2 for being
   majority-digits, −2 for matching `receipt|invoice|order|welcome|thank you`.
3. Highest score wins. Title-case the result, strip `#*` artifacts.
4. Confidence: 0.9 if the winner's glyph height is the page maximum, else 0.6,
   else 0.3 for the first-substantial-line fallback.

### Date extraction

Existing patterns are retained, plus:

- Reject dates in the future.
- Reject dates more than 24 months old.
- Prefer a candidate in the top or bottom 20% of the receipt over one in the
  middle (item lines contain digit runs that parse as dates).
- Fall back to today at confidence 0.2, flagged so the UI marks it for review.

### `ReceiptStore`

Writes the captured bitmap to `filesDir/receipts/<uuid>.jpg` (JPEG, quality 85,
longest edge capped at 2048px). Returns the relative path, which is persisted on
the `Expense` row. Deleting an expense deletes its receipt file.

### Confidence policy

A single threshold constant, `REVIEW_THRESHOLD = 0.6`. Any field below it is
rendered in the review state (amber ring, focused for edit). This constant is
shared with the classifier so "needs review" means one thing across the app.

---

## 2. `sense/classify` — the learning classifier

Multinomial Naive Bayes in log-space over the merchant string.

### Features

For an input merchant string, tokens are:

- Lowercased word tokens after stripping non-alphanumerics.
- Character trigrams of each word token (handles `MCDONALDS #4021` → `mcd`,
  `cdo`, `don`, …, so a truncated or misspelled OCR read still lands).

Word tokens are weighted 3×; trigrams 1×.

### Storage

The model is Room state, not a serialized blob, so it is inspectable and
updatable incrementally.

```java
@Entity(tableName = "token_counts",
        primaryKeys = {"token", "category"})
public class TokenCount {
    @NonNull public String token;
    @NonNull public ExpenseCategory category;
    public int count;
}

@Entity(tableName = "category_counts")
public class CategoryCount {
    @PrimaryKey @NonNull public ExpenseCategory category;
    public int docCount;
    public int tokenTotal;   // sum of counts, cached for the denominator
}
```

### Scoring

For category `c` with vocabulary size `V` and Laplace smoothing `α = 1.0`:

```
score(c) = log(docCount[c] + α) − log(totalDocs + α·|C|)
         + Σ_t weight(t) · [ log(count[t][c] + α) − log(tokenTotal[c] + α·V) ]
```

`confidence` is the softmax-normalized posterior of the winning category over
all categories. Below `REVIEW_THRESHOLD` the UI asks rather than asserts.

```java
public class Prediction {
    public ExpenseCategory category;
    public float confidence;
    public List<String> evidenceTokens;  // top 3 by per-token log-ratio
}
```

`evidenceTokens` lets the UI say "FOOD, 94% — `starbucks`, `latte`" instead of
asserting an unexplained result.

### Seeding

`SeedCorpus` holds one table, replacing all three current duplicates. Each
keyword is inserted on first run as a pseudo-document for its category with
weight 5. The keyword sets are carried over verbatim from
`CategoryPredictor.initBuiltInMappings` and the general keyword lists in
`CategoryPredictor.predictFromKeywords` and `OcrProcessor.suggestCategory`,
deduplicated.

Seeding runs once, guarded by a flag in `SharedPreferences`, inside the same
background executor as the rest of the data layer.

### Learning

- `learn(merchant, category)` — increment token counts and `docCount`. Called on
  **every** expense save, scanned or manual.
- `unlearn(merchant, category)` — decrement, floored at zero. Called with the
  old category when the user corrects one, so the wrong class does not retain
  its counts. This is the piece with no equivalent in the current code.
- A correction is `unlearn(old)` followed by `learn(new)`, in one transaction.

### Overrides

`MerchantCategory` is kept but its meaning changes: it is now an explicit user
pin ("Trader Joe's is always GROCERIES") that short-circuits the model and
returns confidence 1.0. Explicit beats learned. It is written only when the user
chooses "always categorize this merchant as…", never implicitly.

`CategoryPredictor` is deleted; `NaiveBayesClassifier` replaces it.

---

## 3. `sense/search` — plain-language queries

### Filter model

```java
public class SearchFilter {
    public Set<ExpenseCategory> categories;   // empty = any
    public String startDate, endDate;         // yyyy-MM-dd, nullable
    public Double minAmount, maxAmount;
    public List<String> merchantTerms;
    public Sort sort;                         // DATE_DESC, AMOUNT_DESC, AMOUNT_ASC
    public Integer limit;                     // "top 5"
    public boolean isEmpty();
    public List<Chip> toChips();              // one removable chip per constraint
}
```

### Parser

`QueryParser` tokenizes once and consumes tokens, rather than sweeping the whole
string with independent regexes (the current design, which is why the amount
fallback misfires on date digits).

Grammar covered:

- **Categories** — the seed keyword table, reused. Multiple categories allowed
  ("food and groceries").
- **Relative dates** — `today`, `yesterday`, `this week`, `last week`,
  `this month`, `last month`, `this year`, `last N days|weeks|months`,
  `since <month|date>`, weekday names.
  `last month` resolves to the **previous calendar month**, fixing
  `NaturalLanguageParser.java:115`. `this month` resolves to the 1st of the
  current month through today.
- **Explicit months** — `november`, `nov 2025`.
- **Amounts** — `over|above|more than|>`, `under|below|less than|<`,
  `between X and Y`, `around|about X` (±10%). A bare number is treated as an
  amount **only** if no date token consumed it.
- **Sort/limit** — `most|least expensive`, `biggest`, `top N`, `cheapest`.
- **Remaining tokens** become `merchantTerms`, after stop-word removal.

### Execution

`SearchFilterSqlBuilder` compiles a `SearchFilter` to a parameterized
`SupportSQLiteQuery`, run through a new `@RawQuery` method on `ExpenseDao`:

```java
@RawQuery
List<Expense> search(SupportSQLiteQuery query);
```

Merchant terms match `merchant LIKE %term%` OR `title LIKE %term%` OR
`note LIKE %term%`, ANDed across terms.

### LLM fallback

`LlmFilterTranslator` runs only when **all** of the following hold: the local
parser returned `isEmpty()`, an API key is configured, and the network is
available. It sends the query string and the category enum names, and requests a
JSON object matching `SearchFilter`. It never sends transactions.

The response is validated field-by-field before use; anything unparseable is
discarded and the UI reports that the query was not understood. There is no
prose-answer path — `OpenAIService.searchExpenses` and
`buildSearchSystemPrompt` are deleted.

### Ask mode

`OpenAIService.sendMessage` and the chat prompt are retained for the "Ask"
toggle inside Search, which is the free-form conversational surface. It keeps
its current behavior of sending expense context, and the Search screen states
plainly that Ask mode sends data to OpenAI while Find mode does not.

---

## 4. Data model — database v4

### `Expense` additions

| Field | Type | Meaning |
|---|---|---|
| `merchant` | `String` | Raw merchant as captured; distinct from the user-editable `title` |
| `source` | `CaptureSource` | `SCANNED`, `MANUAL` |
| `categorySource` | `CategorySource` | `PREDICTED`, `USER`, `OVERRIDE` |
| `predictionConfidence` | `Float` | Nullable; null for manually chosen categories |
| `receiptPath` | `String` | Nullable; relative path under `filesDir/receipts/` |
| `createdAt` | `long` | Epoch millis |

Two new enums with `Converters` entries. New tables: `token_counts`,
`category_counts`.

### Migration

`fallbackToDestructiveMigration()` is removed and replaced with a written
`MIGRATION_3_4`:

- `ALTER TABLE expenses ADD COLUMN` for each of the six new columns, with
  defaults (`merchant` = existing `title`, `source` = `'MANUAL'`,
  `categorySource` = `'USER'`, `createdAt` = 0).
- `CREATE TABLE` for the two model tables plus their indices.

Existing seeded and user data survives the upgrade. A migration test asserts
this.

### Threading

`allowMainThreadQueries()` is removed. This is the largest structural ripple in
the spec and touches every screen that currently reads a DAO inline.

- `ExpenseRepository` owns a single-thread `ExecutorService` and exposes
  `LiveData` for reads and `void` + callback for writes.
- One `ViewModel` per screen (`HomeViewModel`, `ScanViewModel`,
  `SearchViewModel`, `InsightsViewModel`). Activities keep their current role as
  screen hosts but hold no data-access or business logic.
- New dependencies: `androidx.lifecycle:lifecycle-viewmodel`,
  `androidx.lifecycle:lifecycle-livedata`, and `androidx.room:room-testing`
  (test-only), added to `gradle/libs.versions.toml`.

---

## 5. Visual system

**Dark only.** `Theme.GetSense` replaces `Theme.Project3_aadhika8_sguragai` in
the manifest and parents from `Theme.Material3.Dark.NoActionBar` — a fixed dark
parent, not `DayNight`, so the system setting cannot flip it. `values-night/`
is deleted entirely, since a single palette leaves nothing for it to override.
`android:forceDarkAllowed="false"` is also set, which prevents the platform's
auto-dark pass from re-tinting anything; it does not by itself force dark mode,
and is not what makes this theme dark.

### Tokens

All layouts reference theme attributes. Raw hex in layout XML is what let the
current design drift, and is disallowed.

| Role | Attr | Value |
|---|---|---|
| canvas | `android:colorBackground` | `#0B0D0C` |
| surface | `colorSurface` | `#141816` |
| surface raised | `colorSurfaceVariant` | `#1C211E` |
| hairline | `senseHairline` | `#2A302C` |
| text primary | `colorOnSurface` | `#F2F5F1` |
| text secondary | `colorOnSurfaceVariant` | `#9AA39B` |
| text tertiary | `senseTextTertiary` | `#616B63` |
| accent | `colorPrimary` | `#E8FF5A` |
| on accent | `colorOnPrimary` | `#0B0D0C` |
| positive | `sensePositive` | `#5AE8A0` |
| alert | `colorError` | `#FF6B4A` |

The accent marks the primary metric and the active nav item only. Nothing else
uses it.

### Category palette (retuned for a dark ground)

`food #FF8A5B` · `transport #5BA8FF` · `entertainment #C77DFF` ·
`groceries #5AE8A0` · `bills #FF6B8A` · `shopping #FFD166` · `other #7A8A80`

Each must clear 4.5:1 against `#0B0D0C` when used as text, and is used as a
2dp bar or dot marker otherwise.

### Typography

Two OFL fonts bundled in `res/font/` (no downloadable-fonts dependency, works
offline):

- **Space Grotesk** — display numerals, `fontFeatureSettings="tnum"` so digits
  do not jitter during animation.
- **Inter** — all text.

| Style | Size | Treatment |
|---|---|---|
| Display amount | 44sp | Space Grotesk Medium, tabular |
| Title | 20sp | Inter Medium |
| Body | 15sp | Inter Regular |
| Label | 11sp | Inter Medium, uppercase, 0.12 letter-spacing |

The uppercase spaced label does most of the "instrument" work and is used for
every section header and metadata row.

### Structure

Hairline dividers replace card chrome. `MaterialCardView` survives only for the
receipt review sheet, where the surface is genuinely liftable. Every other
current card becomes a section separated by a 1dp `senseHairline` rule.

---

## 6. Screens

Bottom navigation: **Home · Scan · Search · Insights**.

### Home
Collapsing month header (display amount + `BudgetMeterView`), a "needs review"
strip listing expenses whose `predictionConfidence` is below
`REVIEW_THRESHOLD`, then the transaction feed. Each row shows merchant, amount,
and a metadata line of `CATEGORY · scanned|typed · NN%`.

### Scan
Camera or gallery, then the reveal sequence and the review sheet. Saving runs
`classifier.learn()` and, if the category was changed from the prediction,
`unlearn(predicted)` first.

### Search
Query field, interpreted-filter chip row, results list. A Find/Ask toggle
switches between the local structured path and the OpenAI conversational path,
with an explicit note about which one sends data off-device.

### Insights
The existing `PieChartFragment`, `BarChartFragment`, and `TrendChartFragment`
in their `ViewPager2`, restyled to the token system: transparent chart
backgrounds, hairline axes, category palette, no chart legend boxes.

### Removed
- Streak and badge card in `activity_main.xml` (lines 255–358) and the
  `gold/silver/bronze/locked/unlocked.png` drawables.
- `CsvPreviewActivity`, `activity_csv_preview.xml`, and its manifest entry.
- `ChatActivity` as a standalone destination; its logic moves into the Search
  screen's Ask mode.

`CategoryExpensesActivity` is retained as the drill-down target from Insights.

---

## 7. Interaction and motion

Three items carry the product story:

1. **Scan reveal.** After OCR resolves, `ReceiptOverlayView` dims the receipt
   and draws the detected `Field.boundingBox` regions. An `AnimatorSet` walks
   them in order — merchant, total, date — highlighting each box while its value
   types into the corresponding form field. Roughly 1.8s total, skippable on
   tap. This shows the parse rather than hiding it behind a spinner.
2. **Confidence ring.** A category below `REVIEW_THRESHOLD` renders with an
   amber ring. Tapping opens the category picker; choosing a value runs the
   `unlearn`/`learn` transaction, plays a ring-resolve animation, and fires a
   `CONFIRM` haptic. The learning loop, visible in one gesture.
3. **Filter chips.** Chips animate in as `QueryParser` resolves the query
   ("Food · Nov 1–30 · over $20"). Each is removable; removing one rebuilds the
   `SearchFilter`, re-runs the query, and animates the result diff. The parse
   stays legible instead of magic.

Supporting motion:

- MotionLayout collapsing header on Home: display amount 44sp → 20sp, docking
  into the toolbar on scroll.
- `BudgetMeterView` sweeps from 0 to its value on load via `ValueAnimator`,
  and shifts from `colorPrimary` toward `colorError` as it crosses 80% and 100%.
- `SparklineView` draws the 30-day trend, animated with `PathMeasure` trim.
- Shared-element transition from a transaction row into its detail, expanding
  the receipt thumbnail.
- `DiffUtil` on every adapter (replacing `notifyDataSetChanged`), plus
  swipe-to-recategorize on the transaction row.
- Haptics on category confirm and on crossing a budget threshold.

Three custom views total: `BudgetMeterView`, `SparklineView`,
`ReceiptOverlayView`. Everything else is standard widgets under the theme.

---

## 8. Package structure

```
com.example.project3_aadhika8_sguragai/
  data/
    Expense, ExpenseCategory, CaptureSource, CategorySource
    Budget, MerchantCategory, TokenCount, CategoryCount
    ExpenseDao, BudgetDao, MerchantCategoryDao, ModelDao, ChatMessageDao
    AppDatabase, Migrations, Converters, ExpenseRepository
  sense/
    capture/   ReceiptParser, ReceiptStore, Field, ReceiptExtraction
    classify/  NaiveBayesClassifier, Prediction, SeedCorpus
    search/    QueryParser, SearchFilter, SearchFilterSqlBuilder,
               LlmFilterTranslator
    OpenAIService
  ui/
    home/      HomeActivity, HomeViewModel, ExpenseAdapter
    scan/      ScanActivity, ScanViewModel
    search/    SearchActivity, SearchViewModel, ChipAdapter, ChatMessageAdapter
    insights/  InsightsActivity, InsightsViewModel, chart fragments,
               CategoryExpensesActivity
    widget/    BudgetMeterView, SparklineView, ReceiptOverlayView
```

`MainActivity` is renamed `HomeActivity`; the launcher intent filter moves with
it. `applicationId` is unchanged. `android:label` becomes "getSense AI".

---

## 9. Testing

Replaces `ExampleUnitTest`.

| Test | Asserts |
|---|---|
| `ReceiptParserTest` | ~10 real receipt texts as fixtures under `src/test/resources/receipts/`. Each asserts extracted total, merchant, and date. Includes at least two where the largest number on the page is not the total, and one with no total keyword at all. |
| `NaiveBayesClassifierTest` | Seeded model predicts known merchants. After `learn("blue bottle", FOOD)` a previously unknown merchant predicts FOOD. After a correction, `unlearn`/`learn` flips the prediction and raises confidence. Confidence stays below threshold for an unseen, evidence-free string. |
| `QueryParserTest` | ~30-row table of query string → expected `SearchFilter`. Explicitly pins `last month` = previous calendar month and `this month` = 1st-to-today, and that "food last week" produces no amount filter. |
| `SearchFilterSqlBuilderTest` | Each filter permutation compiles to valid parameterized SQL with the expected bind arguments. |
| `MigrationTest` | Room 3 → 4 preserves existing rows and populates the new columns with their defaults. Uses `room-testing`. |

---

## 10. Security notes

- `android:usesCleartextTraffic="true"` is removed from the manifest. Nothing in
  the app needs it; all API calls are HTTPS.
- The OpenAI key remains a `BuildConfig` field sourced from
  `local.properties`. This keeps it out of version control but **does** bake it
  into the APK, where it is recoverable by anyone who unpacks the build. That is
  acceptable for coursework and is not acceptable for distribution; a real
  release needs a server-side proxy. Stated here rather than papered over.
- Receipt images live in `filesDir`, which is app-private and excluded from
  backup via the existing `backup_rules.xml`.

---

## 11. Explicitly out of scope

- Light theme.
- Kotlin or Jetpack Compose.
- Bank or card account linking.
- Multi-currency.
- Cloud sync or accounts.
- Recurring-transaction detection.
