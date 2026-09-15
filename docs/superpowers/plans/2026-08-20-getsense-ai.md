# getSense AI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the existing budgeting app into getSense AI — a receipt scanner whose OCR finds the real total, a Naive Bayes classifier that measurably learns from corrections, and a plain-language search that compiles to local SQL — presented in a dark instrument-panel UI.

**Architecture:** Three layers. `data/` owns Room (v4, written migration, no main-thread queries) behind an `ExpenseRepository` with a single background executor. `sense/` holds the three headless engines (`capture`, `classify`, `search`) — pure Java, no Android UI imports, unit-testable on the JVM. `ui/` holds one Activity + one ViewModel per screen; Activities host views and observe LiveData, never touch a DAO.

**Tech Stack:** Java 11, Android SDK 36 (min 30), XML Views (no Kotlin, no Compose), Room 2.8.4, ML Kit text-recognition 16.0.1, MPAndroidChart 3.1.0, OkHttp 4.12.0, Glide 4.16.0, AndroidX Lifecycle (ViewModel + LiveData), room-testing (test-only).

**Spec:** `docs/superpowers/specs/2026-08-14-getsense-ai-design.md`

## Global Constraints

Copied verbatim from the spec. Every task's requirements implicitly include this section.

- **Java + XML Views only.** No Kotlin, no Jetpack Compose. (Spec decision 4)
- **Dark only.** No light theme. `values-night/` is deleted. (Spec decision 3, §5)
- **No raw hex in layout XML.** All layouts reference theme attributes. (Spec §5)
- `applicationId` stays `com.example.project3_aadhika8_sguragai`. (Spec §8)
- `android:label` becomes `"getSense AI"`. (Spec §8)
- `REVIEW_THRESHOLD = 0.6f` — one constant shared by capture and classify. (Spec §1)
- Laplace smoothing `α = 1.0`; word tokens weighted 3×, char trigrams 1×. (Spec §2)
- Room stays at db name `budgetbuddy.db`; version goes 3 → 4 via a **written** `MIGRATION_3_4`. `fallbackToDestructiveMigration()` and `allowMainThreadQueries()` are both removed. (Spec §4)
- `android:usesCleartextTraffic="true"` is removed from the manifest. (Spec §10)
- The OpenAI key stays a `BuildConfig` field from `local.properties`. Known and accepted limitation: it is recoverable from the APK. (Spec §10)
- `LlmFilterTranslator` **never sends transactions** — query string and category enum names only. (Spec §3)
- Out of scope, do not build: light theme, Kotlin/Compose, bank linking, multi-currency, cloud sync, recurring-transaction detection. (Spec §11)

### Token values (single source of truth — Spec §5)

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

Category palette: `food #FF8A5B` · `transport #5BA8FF` · `entertainment #C77DFF` · `groceries #5AE8A0` · `bills #FF6B8A` · `shopping #FFD166` · `other #7A8A80`

### Baseline

`./gradlew assembleDebug` is **BUILD SUCCESSFUL** at plan time (commit `c266052` + uncommitted work). Every task ends with a green build; if a task leaves the build red, it is not done.

---

## File Structure

Target package layout (Spec §8). Files move from the flat root package into these subpackages.

```
com.example.project3_aadhika8_sguragai/
  data/
    Expense, ExpenseCategory, CaptureSource, CategorySource
    Budget, MerchantCategory, TokenCount, CategoryCount, ChatMessage
    ExpenseDao, BudgetDao, MerchantCategoryDao, ModelDao, ChatMessageDao
    AppDatabase, Migrations, Converters, ExpenseRepository
  sense/
    capture/   ReceiptParser, ReceiptStore, Field, ReceiptExtraction
    classify/  NaiveBayesClassifier, Prediction, SeedCorpus
    search/    QueryParser, SearchFilter, SearchFilterSqlBuilder, LlmFilterTranslator
    OpenAIService, Sense (shared constants)
  ui/
    home/      HomeActivity, HomeViewModel, ExpenseAdapter
    scan/      ScanActivity, ScanViewModel
    search/    SearchActivity, SearchViewModel, ChipAdapter, ChatMessageAdapter
    insights/  InsightsActivity, InsightsViewModel, PieChartFragment,
               BarChartFragment, TrendChartFragment, CategoryExpensesActivity
    widget/    BudgetMeterView, SparklineView, ReceiptOverlayView
```

**Deleted outright:** `CategoryPredictor`, `OcrProcessor`, `NaturalLanguageParser`, `CsvPreviewActivity` (+ `activity_csv_preview.xml` + manifest entry), `ChatActivity` as a destination (+ `activity_chat.xml`), `SummaryActivity`, `ChartsActivity`, `values-night/themes.xml`, `ExampleUnitTest`, and the `gold/silver/bronze/locked/unlocked` badge drawables.

---

## Phase 0 — Dependencies and test scaffolding

### Task 0.1: Add lifecycle, room-testing, and Robolectric-free JVM test deps

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: nothing.
- Produces: `androidx.lifecycle.ViewModel`, `androidx.lifecycle.LiveData`, `androidx.room.testing.MigrationTestHelper` available to later tasks.

- [ ] **Step 1: Add version refs**

In `gradle/libs.versions.toml` `[versions]`, append:

```toml
lifecycle = "2.9.4"
roomTesting = "2.8.4"
coreTesting = "2.2.0"
```

In `[libraries]`, append:

```toml
lifecycle-viewmodel = { group = "androidx.lifecycle", name = "lifecycle-viewmodel", version.ref = "lifecycle" }
lifecycle-livedata = { group = "androidx.lifecycle", name = "lifecycle-livedata", version.ref = "lifecycle" }
lifecycle-runtime = { group = "androidx.lifecycle", name = "lifecycle-runtime", version.ref = "lifecycle" }
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "roomTesting" }
core-testing = { group = "androidx.arch.core", name = "core-testing", version.ref = "coreTesting" }
```

- [ ] **Step 2: Wire them into the app module**

In `app/build.gradle.kts` `dependencies`, add:

```kotlin
implementation(libs.lifecycle.viewmodel)
implementation(libs.lifecycle.livedata)
implementation(libs.lifecycle.runtime)
testImplementation(libs.core.testing)
androidTestImplementation(libs.room.testing)
```

- [ ] **Step 3: Turn on schema export (MigrationTest needs it)**

In `app/build.gradle.kts`, inside `android { defaultConfig { ... } }`, add:

```kotlin
javaCompileOptions {
    annotationProcessorOptions {
        arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
    }
}
```

and inside `android { ... }` add:

```kotlin
sourceSets {
    getByName("androidTest").assets.srcDirs("$projectDir/schemas")
}
```

- [ ] **Step 4: Verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add lifecycle, room-testing, schema export"
```

---

## Phase 1 — Data model v4, migration, threading

This phase is the largest structural ripple in the spec (§4). Nothing in Phase 2–4 compiles without it.

### Task 1.1: Enums and Expense columns

**Files:**
- Create: `app/src/main/java/.../data/CaptureSource.java`
- Create: `app/src/main/java/.../data/CategorySource.java`
- Modify: `Expense.java` (add six columns)
- Modify: `Converters.java` (two new converter pairs)

**Interfaces:**
- Produces: `CaptureSource.{SCANNED, MANUAL}`, `CategorySource.{PREDICTED, USER, OVERRIDE}`, and `Expense.{merchant, source, categorySource, predictionConfidence, receiptPath, createdAt}`.

- [ ] **Step 1: Write the enums**

```java
package com.example.project3_aadhika8_sguragai.data;

public enum CaptureSource { SCANNED, MANUAL }
```

```java
package com.example.project3_aadhika8_sguragai.data;

public enum CategorySource { PREDICTED, USER, OVERRIDE }
```

- [ ] **Step 2: Add the six columns to `Expense`**

Append to the `Expense` class body (Spec §4 table):

```java
    /** Raw merchant as captured; distinct from the user-editable title. */
    public String merchant;

    public CaptureSource source;

    public CategorySource categorySource;

    /** Null when the category was chosen by hand. */
    public Float predictionConfidence;

    /** Relative path under filesDir/receipts/, or null. */
    public String receiptPath;

    public long createdAt;
```

- [ ] **Step 3: Add converters**

Append to `Converters`:

```java
    @TypeConverter
    public static String fromCaptureSource(CaptureSource s) {
        return s == null ? null : s.name();
    }

    @TypeConverter
    public static CaptureSource toCaptureSource(String v) {
        return v == null ? null : CaptureSource.valueOf(v);
    }

    @TypeConverter
    public static String fromCategorySource(CategorySource s) {
        return s == null ? null : s.name();
    }

    @TypeConverter
    public static CategorySource toCategorySource(String v) {
        return v == null ? null : CategorySource.valueOf(v);
    }
```

- [ ] **Step 4: Build** — `./gradlew assembleDebug`. Room will now complain the schema changed with no migration; that is Task 1.3. Until then it still compiles because `fallbackToDestructiveMigration()` is present.

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(data): add capture/category source enums and six Expense columns"
```

### Task 1.2: Model tables and `ModelDao`

**Files:**
- Create: `data/TokenCount.java`, `data/CategoryCount.java`, `data/ModelDao.java`

**Interfaces:**
- Produces: `ModelDao.{getCount, upsertToken, incrementToken, decrementToken, getCategoryCount, upsertCategory, getAllCategoryCounts, getTokenCountsFor, vocabularySize, totalDocs}` — consumed by `NaiveBayesClassifier` in Task 3.1.

- [ ] **Step 1: Entities** (Spec §2)

```java
@Entity(tableName = "token_counts", primaryKeys = {"token", "category"})
public class TokenCount {
    @NonNull public String token = "";
    @NonNull public ExpenseCategory category = ExpenseCategory.OTHER;
    public int count;
}
```

```java
@Entity(tableName = "category_counts")
public class CategoryCount {
    @PrimaryKey @NonNull public ExpenseCategory category = ExpenseCategory.OTHER;
    public int docCount;
    public int tokenTotal;   // sum of counts, cached for the denominator
}
```

- [ ] **Step 2: `ModelDao`**

```java
@Dao
public interface ModelDao {

    @Query("SELECT count FROM token_counts WHERE token = :t AND category = :c")
    Integer rawTokenCount(String t, ExpenseCategory c);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertToken(TokenCount tc);

    @Query("SELECT * FROM token_counts WHERE token IN (:tokens)")
    List<TokenCount> tokenCountsFor(List<String> tokens);

    @Query("SELECT COUNT(DISTINCT token) FROM token_counts")
    int vocabularySize();

    @Query("SELECT * FROM category_counts")
    List<CategoryCount> allCategoryCounts();

    @Query("SELECT * FROM category_counts WHERE category = :c")
    CategoryCount categoryCount(ExpenseCategory c);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertCategory(CategoryCount cc);

    @Query("SELECT IFNULL(SUM(docCount), 0) FROM category_counts")
    int totalDocs();

    @Query("SELECT COUNT(*) FROM token_counts")
    int tokenRowCount();
}
```

- [ ] **Step 3: Build** — `./gradlew assembleDebug`. Expected: BUILD SUCCESSFUL (entities are not yet registered, so Room ignores them).

- [ ] **Step 4: Commit**

```bash
git commit -am "feat(data): add token_counts/category_counts tables and ModelDao"
```

### Task 1.3: `Migrations.MIGRATION_3_4` and database v4

**Files:**
- Create: `data/Migrations.java`
- Modify: `data/AppDatabase.java`

**Interfaces:**
- Consumes: Task 1.1 columns, Task 1.2 entities.
- Produces: `AppDatabase.modelDao()`, `Migrations.MIGRATION_3_4`.

- [ ] **Step 1: Write the migration** (Spec §4)

```java
package com.example.project3_aadhika8_sguragai.data;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public final class Migrations {

    private Migrations() {}

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE expenses ADD COLUMN merchant TEXT");
            db.execSQL("UPDATE expenses SET merchant = title WHERE merchant IS NULL");
            db.execSQL("ALTER TABLE expenses ADD COLUMN source TEXT DEFAULT 'MANUAL'");
            db.execSQL("UPDATE expenses SET source = 'MANUAL' WHERE source IS NULL");
            db.execSQL("ALTER TABLE expenses ADD COLUMN categorySource TEXT DEFAULT 'USER'");
            db.execSQL("UPDATE expenses SET categorySource = 'USER' WHERE categorySource IS NULL");
            db.execSQL("ALTER TABLE expenses ADD COLUMN predictionConfidence REAL");
            db.execSQL("ALTER TABLE expenses ADD COLUMN receiptPath TEXT");
            db.execSQL("ALTER TABLE expenses ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0");

            db.execSQL("CREATE TABLE IF NOT EXISTS token_counts ("
                    + "token TEXT NOT NULL, "
                    + "category TEXT NOT NULL, "
                    + "count INTEGER NOT NULL, "
                    + "PRIMARY KEY(token, category))");

            db.execSQL("CREATE TABLE IF NOT EXISTS category_counts ("
                    + "category TEXT NOT NULL, "
                    + "docCount INTEGER NOT NULL, "
                    + "tokenTotal INTEGER NOT NULL, "
                    + "PRIMARY KEY(category))");
        }
    };
}
```

**Note for the implementer:** Room's generated schema hash must match these `CREATE TABLE` statements exactly (column order, types, `NOT NULL`). If `MigrationTest` fails with a schema mismatch, read the expected DDL out of `app/schemas/…/4.json` and copy it rather than guessing.

- [ ] **Step 2: Bump the database**

In `AppDatabase`: `version = 4`, add `TokenCount.class, CategoryCount.class` to `entities`, set `exportSchema = true`, add `public abstract ModelDao modelDao();`, and in the builder replace `.allowMainThreadQueries().fallbackToDestructiveMigration()` with `.addMigrations(Migrations.MIGRATION_3_4)`.

- [ ] **Step 3: Build** — expected to FAIL to compile everywhere a DAO is called from the main thread. That is the point; Task 1.4 fixes it.

- [ ] **Step 4: Commit after 1.4 passes.** (Do not commit a red build.)

### Task 1.4: `ExpenseRepository` and the executor

**Files:**
- Create: `data/ExpenseRepository.java`

**Interfaces:**
- Produces:
  - `static ExpenseRepository get(Context)`
  - `void io(Runnable)` — run on the single background thread
  - `<T> void query(Callable<T> work, Consumer<T> onResult)` — background read, main-thread callback
  - `LiveData<List<Expense>> observeAll()`, `observeRange(String start, String end)`
  - `void insert(Expense e, Consumer<Long> onId)`, `void update(Expense e, Runnable done)`, `void delete(Expense e, Runnable done)`
  - `LiveData<Double> observeBudget()`, `void setBudget(double amount)`
  - accessors `expenseDao()`, `modelDao()`, `merchantCategoryDao()`, `budgetDao()`, `chatMessageDao()`

- [ ] **Step 1: Convert the read DAO methods to LiveData**

In `ExpenseDao`, add LiveData variants alongside the existing synchronous ones (keep the synchronous ones — the repository calls them on its own thread):

```java
@Query("SELECT * FROM expenses ORDER BY date DESC, id DESC")
LiveData<List<Expense>> observeAllExpenses();

@Query("SELECT * FROM expenses WHERE date BETWEEN :start AND :end ORDER BY date DESC, id DESC")
LiveData<List<Expense>> observeExpensesInRange(String start, String end);

@Query("SELECT * FROM expenses WHERE predictionConfidence IS NOT NULL AND predictionConfidence < :threshold ORDER BY date DESC")
LiveData<List<Expense>> observeNeedsReview(float threshold);
```

In `BudgetDao`, add `@Query("SELECT amount FROM budget WHERE id = 1 LIMIT 1") LiveData<Double> observeBudget();`

- [ ] **Step 2: Write the repository**

```java
public class ExpenseRepository {

    private static volatile ExpenseRepository instance;

    private final AppDatabase db;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private ExpenseRepository(Context ctx) {
        this.db = AppDatabase.getInstance(ctx.getApplicationContext());
    }

    public static ExpenseRepository get(Context ctx) {
        if (instance == null) {
            synchronized (ExpenseRepository.class) {
                if (instance == null) instance = new ExpenseRepository(ctx);
            }
        }
        return instance;
    }

    public void io(Runnable work) { io.execute(work); }

    public <T> void query(Callable<T> work, Consumer<T> onResult) {
        io.execute(() -> {
            try {
                T result = work.call();
                main.post(() -> onResult.accept(result));
            } catch (Exception e) {
                Log.e("ExpenseRepository", "query failed", e);
                main.post(() -> onResult.accept(null));
            }
        });
    }
    // ... plus the LiveData pass-throughs and the write helpers listed in Interfaces
}
```

- [ ] **Step 3: Migrate every existing main-thread DAO call**

Every call site that currently does `AppDatabase.getInstance(this).expenseDao().…` inline must move behind `repo.query(...)` or `repo.io(...)`. Find them with:

```bash
grep -rn "getInstance(" app/src/main/java --include=*.java
```

Fix all of them. Until this is done the app will throw `IllegalStateException: Cannot access database on the main thread`.

- [ ] **Step 4: Build and smoke-run** — `./gradlew assembleDebug`, install, open Home. Expected: no main-thread DB crash, seeded expenses render.

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(data): db v4 with written migration, repository, off-main-thread access"
```

### Task 1.5: `MigrationTest`

**Files:**
- Create: `app/src/androidTest/java/.../MigrationTest.java`

- [ ] **Step 1: Write the failing test** (Spec §9)

```java
@RunWith(AndroidJUnit4.class)
public class MigrationTest {

    private static final String DB = "migration-test";

    @Rule
    public MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase.class);

    @Test
    public void migrate3To4_preservesRowsAndDefaultsNewColumns() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(DB, 3);
        db.execSQL("INSERT INTO expenses (amount, category, title, date, note) "
                + "VALUES (12.50, 'FOOD', 'Chipotle Lunch', '2025-11-25', 'Bowl')");
        db.close();

        SupportSQLiteDatabase migrated =
                helper.runMigrationsAndValidate(DB, 4, true, Migrations.MIGRATION_3_4);

        Cursor c = migrated.query(
                "SELECT title, merchant, source, categorySource, createdAt FROM expenses");
        assertTrue(c.moveToFirst());
        assertEquals("Chipotle Lunch", c.getString(0));
        assertEquals("Chipotle Lunch", c.getString(1));   // merchant defaults to title
        assertEquals("MANUAL", c.getString(2));
        assertEquals("USER", c.getString(3));
        assertEquals(0L, c.getLong(4));
        c.close();
    }
}
```

- [ ] **Step 2: Run it** — `./gradlew connectedDebugAndroidTest --tests "*MigrationTest*"` (needs a booted emulator). Expected: FAIL first if the DDL hash mismatches; fix `Migrations` until green.

- [ ] **Step 3: Commit**

```bash
git commit -am "test(data): assert 3→4 migration preserves rows and defaults new columns"
```

---

## Phase 2 — `sense/capture`: reading receipts

Replaces `OcrProcessor` entirely (Spec §1).

### Task 2.1: `Field`, `ReceiptExtraction`, `Sense.REVIEW_THRESHOLD`

**Files:**
- Create: `sense/Sense.java`, `sense/capture/Field.java`, `sense/capture/ReceiptExtraction.java`

**Interfaces:**
- Produces: `Sense.REVIEW_THRESHOLD` (float `0.6f`), `Field<T>{value, confidence, sourceLine, boundingBox, needsReview()}`, `ReceiptExtraction{merchant, total, date, rawText}`.

- [ ] **Step 1: Write them** (Spec §1 field model)

```java
public final class Sense {
    /** Any field below this is rendered in the review state. Shared by capture and classify. */
    public static final float REVIEW_THRESHOLD = 0.6f;
    private Sense() {}
}
```

```java
public class Field<T> {
    public final T value;              // null if not found
    public final float confidence;     // 0.0 – 1.0
    public final String sourceLine;    // the OCR line it came from
    public final Rect boundingBox;     // ML Kit line bounds, for the overlay

    public Field(T value, float confidence, String sourceLine, Rect boundingBox) { ... }

    public static <T> Field<T> missing() { return new Field<>(null, 0f, null, null); }

    public boolean needsReview() { return value == null || confidence < Sense.REVIEW_THRESHOLD; }
}
```

`ReceiptExtraction` is the four public fields from the spec, no logic.

- [ ] **Step 2: Build.** `./gradlew assembleDebug`.
- [ ] **Step 3: Commit** — `git commit -am "feat(capture): Field/ReceiptExtraction model and shared review threshold"`

### Task 2.2: `ReceiptParser` — total extraction

The behavior change that matters most: "biggest number wins" is replaced by keyword-anchored, bottom-up extraction.

**Files:**
- Create: `sense/capture/ReceiptParser.java`
- Create: `app/src/test/java/.../ReceiptParserTest.java`
- Create: `app/src/test/resources/receipts/*.txt` (≥10 fixtures)

**Interfaces:**
- Consumes: `Field`, `ReceiptExtraction`, `Sense`.
- Produces:
  - `ReceiptExtraction parseText(List<ParsedLine> lines)` — the pure, JVM-testable entry point.
  - `void parse(Bitmap bmp, Callback cb)` — the ML Kit entry point; converts `Text` into `List<ParsedLine>` then delegates to `parseText`.
  - `public static class ParsedLine { public final String text; public final Rect box; }`

  Splitting the pure path from the ML Kit path is what makes `ReceiptParserTest` a plain JUnit test with no emulator.

- [ ] **Step 1: Write the failing tests first**

Fixtures live in `app/src/test/resources/receipts/`. At least two must be receipts where the largest number on the page is **not** the total, and one must have no total keyword at all (Spec §9).

```java
public class ReceiptParserTest {

    private ReceiptExtraction parseFixture(String name) throws Exception {
        List<ReceiptParser.ParsedLine> lines = new ArrayList<>();
        int y = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/receipts/" + name)))) {
            String l;
            while ((l = r.readLine()) != null) {
                lines.add(new ReceiptParser.ParsedLine(l, new Rect(0, y, 400, y + 20)));
                y += 24;
            }
        }
        return new ReceiptParser().parseText(lines);
    }

    @Test
    public void picksTotalNotLargestNumber_whenCashTenderedIsBigger() throws Exception {
        // fixture: TOTAL 23.41, CASH 50.00, CHANGE 26.59
        ReceiptExtraction r = parseFixture("cash_tendered_larger.txt");
        assertEquals(23.41, r.total.value, 0.001);
        assertTrue(r.total.confidence >= 0.95f);
    }

    @Test
    public void picksTotalNotLargestNumber_whenLoyaltyNumberIsBigger() throws Exception {
        ReceiptExtraction r = parseFixture("loyalty_number_larger.txt");
        assertEquals(8.75, r.total.value, 0.001);
    }

    @Test
    public void derivesTotalFromSubtotalPlusTax_whenNoTotalKeyword() throws Exception {
        // fixture: SUBTOTAL 10.00, TAX 0.85, no TOTAL line
        ReceiptExtraction r = parseFixture("subtotal_only.txt");
        assertEquals(10.85, r.total.value, 0.001);
        assertEquals(0.55f, r.total.confidence, 0.001f);
    }

    @Test
    public void prefersLastTotalOccurrence() throws Exception {
        ReceiptExtraction r = parseFixture("duplicate_total.txt");
        assertEquals(41.20, r.total.value, 0.001);
    }

    @Test
    public void merchantIsStoreName_notAddressOrPhone() throws Exception {
        ReceiptExtraction r = parseFixture("cash_tendered_larger.txt");
        assertEquals("Trader Joe's", r.merchant.value);
    }

    @Test
    public void rejectsFutureDate_andFallsBackFlaggedForReview() throws Exception {
        ReceiptExtraction r = parseFixture("future_date.txt");
        assertTrue(r.date.confidence <= 0.2f);
        assertTrue(r.date.needsReview());
    }

    @Test
    public void noTotalAnywhere_fallsBackToLargestAtLowConfidence() throws Exception {
        ReceiptExtraction r = parseFixture("no_total_keyword.txt");
        assertEquals(0.30f, r.total.confidence, 0.001f);
        assertTrue(r.total.needsReview());
    }
}
```

- [ ] **Step 2: Run to verify failure** — `./gradlew testDebugUnitTest --tests "*ReceiptParserTest*"`. Expected: FAIL, `ReceiptParser` does not exist.

- [ ] **Step 3: Implement total extraction** — exactly the six-step algorithm in Spec §1:

1. Walk lines bottom-to-top; match total keywords in priority order `grand total` > `total` > `amount due` > `balance due` > `subtotal`.
2. Take the currency amount on the matched line; if absent, take the first amount on the next line.
3. Reject a candidate whose line also matches `change|cash|tender|card|visa|mastercard|debit|credit|tip|cash back`.
4. Prefer the **last** matching occurrence in the document.
5. If only `subtotal` matched and a tax line exists, `total = subtotal + tax`.
6. Confidence: `0.95` explicit `total`, `0.75` `amount/balance due`, `0.55` subtotal-derived, `0.30` fallback (largest amount).

- [ ] **Step 4: Implement merchant extraction** — Spec §1 scoring, verbatim:

Consider only lines whose `box.top` is within the top 25% of the receipt height. Score: `+2` glyph height above page median (use `box.height()`), `−3` matching a 5-digit zip / phone pattern / street suffix (`st|street|ave|avenue|rd|road|blvd|suite|ste|#\d`), `−2` majority-digits, `−2` matching `receipt|invoice|order|welcome|thank you`. Highest wins; title-case; strip `#*`. Confidence `0.9` if winner's glyph height is the page max, else `0.6`, else `0.3` for the first-substantial-line fallback.

- [ ] **Step 5: Implement date extraction** — keep the existing patterns from `OcrProcessor` and add the four spec rules: reject future dates, reject >24 months old, prefer top/bottom 20% over the middle, fall back to today at confidence `0.2` flagged for review.

- [ ] **Step 6: Run tests** — `./gradlew testDebugUnitTest --tests "*ReceiptParserTest*"`. Expected: PASS, all 7.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/**/capture app/src/test
git commit -m "feat(capture): keyword-anchored receipt parser with confidence and bounding boxes"
```

### Task 2.3: `ReceiptStore`

**Files:**
- Create: `sense/capture/ReceiptStore.java`

**Interfaces:**
- Produces: `String save(Bitmap bmp)` returning a path relative to `filesDir`, and `boolean delete(String relativePath)`.

- [ ] **Step 1: Implement** (Spec §1) — write to `filesDir/receipts/<uuid>.jpg`, JPEG quality 85, longest edge capped at 2048px, return the relative path.
- [ ] **Step 2: Wire deletion** — deleting an expense deletes its receipt file. Add the call in `ExpenseRepository.delete`.
- [ ] **Step 3: Build.** `./gradlew assembleDebug`.
- [ ] **Step 4: Commit** — `git commit -am "feat(capture): receipt image store with lifecycle-tied deletion"`

### Task 2.4: Delete `OcrProcessor`

- [ ] **Step 1:** `git rm app/src/main/java/.../OcrProcessor.java`, fix the call sites in `ReceiptScanActivity` to use `ReceiptParser`.
- [ ] **Step 2: Build.** `./gradlew assembleDebug`.
- [ ] **Step 3: Commit** — `git commit -am "refactor(capture): delete OcrProcessor in favour of ReceiptParser"`

---

## Phase 3 — `sense/classify`: the learning classifier

This is the pillar that is currently dead code — `CategoryPredictor` is constructed nowhere. (Spec §2)

### Task 3.1: `NaiveBayesClassifier` + `Prediction`

**Files:**
- Create: `sense/classify/Prediction.java`, `sense/classify/NaiveBayesClassifier.java`
- Create: `app/src/test/java/.../NaiveBayesClassifierTest.java`

**Interfaces:**
- Consumes: `ModelDao`, `ExpenseCategory`, `Sense.REVIEW_THRESHOLD`.
- Produces:
  - `Prediction { ExpenseCategory category; float confidence; List<String> evidenceTokens; }`
  - `Prediction predict(String merchant)`
  - `void learn(String merchant, ExpenseCategory c)`
  - `void unlearn(String merchant, ExpenseCategory c)`
  - `void correct(String merchant, ExpenseCategory from, ExpenseCategory to)` — `unlearn(from)` then `learn(to)` in one transaction
  - `static List<String> tokenize(String merchant)` and `static int weightOf(String token)` — package-visible for tests

  The classifier takes a `ModelDao` in its constructor, so the test can pass an in-memory fake and stay on the JVM.

- [ ] **Step 1: Write the failing tests** (Spec §9)

```java
public class NaiveBayesClassifierTest {

    private NaiveBayesClassifier classifier;

    @Before
    public void setUp() {
        classifier = new NaiveBayesClassifier(new FakeModelDao());
        SeedCorpus.seed(classifier);
    }

    @Test
    public void seededModelPredictsKnownMerchant() {
        Prediction p = classifier.predict("STARBUCKS #4021");
        assertEquals(ExpenseCategory.FOOD, p.category);
        assertTrue(p.confidence > Sense.REVIEW_THRESHOLD);
    }

    @Test
    public void learningAnUnknownMerchantMakesItPredictable() {
        assertNotEquals(ExpenseCategory.FOOD, classifier.predict("blue bottle").category);
        for (int i = 0; i < 3; i++) classifier.learn("blue bottle", ExpenseCategory.FOOD);
        assertEquals(ExpenseCategory.FOOD, classifier.predict("blue bottle").category);
    }

    @Test
    public void correctionFlipsPredictionAndRaisesConfidence() {
        classifier.learn("acme corp", ExpenseCategory.SHOPPING);
        Prediction before = classifier.predict("acme corp");
        assertEquals(ExpenseCategory.SHOPPING, before.category);

        classifier.correct("acme corp", ExpenseCategory.SHOPPING, ExpenseCategory.BILLS);
        Prediction after = classifier.predict("acme corp");
        assertEquals(ExpenseCategory.BILLS, after.category);
        assertTrue("unlearn must strip the old class's counts",
                after.confidence > before.confidence);
    }

    @Test
    public void unlearnFloorsAtZero() {
        classifier.learn("zzz", ExpenseCategory.FOOD);
        classifier.unlearn("zzz", ExpenseCategory.FOOD);
        classifier.unlearn("zzz", ExpenseCategory.FOOD);   // second unlearn must not go negative
        assertTrue(classifier.predict("zzz").confidence < Sense.REVIEW_THRESHOLD);
    }

    @Test
    public void unseenEvidenceFreeStringStaysBelowThreshold() {
        Prediction p = classifier.predict("qqqq wxyz");
        assertTrue(p.confidence < Sense.REVIEW_THRESHOLD);
    }

    @Test
    public void trigramsRescueTruncatedMerchant() {
        Prediction p = classifier.predict("MCDONALD");   // seeded as "mcdonald"
        assertEquals(ExpenseCategory.FOOD, p.category);
    }

    @Test
    public void evidenceTokensExplainThePrediction() {
        Prediction p = classifier.predict("STARBUCKS");
        assertTrue(p.evidenceTokens.contains("starbucks"));
        assertTrue(p.evidenceTokens.size() <= 3);
    }
}
```

- [ ] **Step 2: Run to verify failure** — `./gradlew testDebugUnitTest --tests "*NaiveBayesClassifierTest*"`. Expected: FAIL, class does not exist.

- [ ] **Step 3: Implement tokenization** (Spec §2) — lowercase word tokens after stripping non-alphanumerics, plus character trigrams of each word token. Word tokens weight **3×**, trigrams **1×**.

- [ ] **Step 4: Implement scoring** — log-space, exactly the spec formula with `α = 1.0`:

```
score(c) = log(docCount[c] + α) − log(totalDocs + α·|C|)
         + Σ_t weight(t) · [ log(count[t][c] + α) − log(tokenTotal[c] + α·V) ]
```

`confidence` is the softmax-normalized posterior of the winning category over all categories. `evidenceTokens` is the top 3 tokens by per-token log-ratio.

- [ ] **Step 5: Implement learn/unlearn** — `learn` increments token counts, `docCount`, and `tokenTotal`. `unlearn` decrements, **floored at zero** on every counter. `correct` runs both inside one `runInTransaction`.

- [ ] **Step 6: Run tests** — expected PASS, all 7.

- [ ] **Step 7: Commit** — `git commit -am "feat(classify): Naive Bayes classifier with learn/unlearn and evidence tokens"`

### Task 3.2: `SeedCorpus` — one table replacing three

**Files:**
- Create: `sense/classify/SeedCorpus.java`

**Interfaces:**
- Produces: `static Map<String, ExpenseCategory> keywords()` and `static void seed(NaiveBayesClassifier c)`.

The keyword sets are carried over **verbatim** from `CategoryPredictor.initBuiltInMappings`, `CategoryPredictor.predictFromKeywords`, and `OcrProcessor.suggestCategory`, deduplicated (Spec §2). They are reproduced here so the implementer does not have to reconstruct them from three deleted files:

**FOOD:** starbucks, mcdonald, burger king, wendys, subway, chipotle, taco bell, pizza hut, dominos, dunkin, panera, chick-fil-a, popeyes, kfc, arbys, sonic, five guys, ihop, denny, applebees, olive garden, red lobster, cheesecake factory, buffalo wild wings, outback, coffee, cafe, restaurant, diner, food, pizza, burger, sushi, thai, chinese, mexican, italian, lunch, dinner, breakfast, eat, taco

**TRANSPORT:** shell, exxon, chevron, mobil, bp, texaco, speedway, circle k, wawa, uber, lyft, taxi, parking, metro, transit, amtrak, greyhound, gas, fuel, bus, train, flight, airline

**GROCERIES:** walmart, target, costco, kroger, safeway, whole foods, trader joe, aldi, publix, heb, wegmans, food lion, giant, stop shop, piggly wiggly, sprouts, grocery, market, supermarket, produce, meat

**ENTERTAINMENT:** netflix, spotify, hulu, disney, hbo, amc, regal, cinemark, playstation, xbox, nintendo, steam, ticketmaster, stubhub, live nation, movie, theater, concert, game, streaming, entertainment, ticket, show, cinema, amusement

**SHOPPING:** amazon, ebay, best buy, apple store, nike, adidas, nordstrom, macys, jcpenney, kohls, ross, tj maxx, marshalls, home depot, lowes, ikea, wayfair, shop, store, mall, clothing, clothes, shoes, electronics, furniture, apparel, fashion

**BILLS:** verizon, att, tmobile, sprint, comcast, xfinity, spectrum, cox, electric, water, gas company, insurance, geico, progressive, state farm, allstate, bill, phone, internet, rent, mortgage, utility, cable

- [ ] **Step 1: Implement** — each keyword is inserted on first run as a pseudo-document for its category with **weight 5**.
- [ ] **Step 2: Guard the seeding** — runs once, guarded by a `SharedPreferences` flag, inside the same background executor as the rest of the data layer.
- [ ] **Step 3: Run `NaiveBayesClassifierTest`** — the seeded tests from Task 3.1 now pass.
- [ ] **Step 4: Commit** — `git commit -am "feat(classify): single seed corpus replacing three duplicate keyword tables"`

### Task 3.3: Repurpose `MerchantCategory` as an explicit pin; delete `CategoryPredictor`

**Files:**
- Modify: `data/MerchantCategory.java` (docs + semantics)
- Delete: `CategoryPredictor.java`

- [ ] **Step 1:** `MerchantCategory` now means an explicit user pin ("Trader Joe's is always GROCERIES") that short-circuits the model and returns confidence `1.0`. Explicit beats learned. It is written **only** when the user chooses "always categorize this merchant as…", never implicitly (Spec §2).
- [ ] **Step 2:** Add the pin check at the top of `NaiveBayesClassifier.predict` — if a pin matches, return `new Prediction(pinned, 1.0f, List.of("pinned"))`.
- [ ] **Step 3:** `git rm app/src/main/java/.../CategoryPredictor.java`; fix any references.
- [ ] **Step 4: Build.** `./gradlew assembleDebug` and `./gradlew testDebugUnitTest`.
- [ ] **Step 5: Commit** — `git commit -am "refactor(classify): MerchantCategory becomes an explicit pin; delete CategoryPredictor"`

---

## Phase 4 — `sense/search`: plain-language queries

Replaces `NaturalLanguageParser` and the prose-answer path in `OpenAIService` (Spec §3).

### Task 4.1: `SearchFilter`

**Files:**
- Create: `sense/search/SearchFilter.java`

**Interfaces:**
- Produces: the class from Spec §3 verbatim —

```java
public class SearchFilter {
    public Set<ExpenseCategory> categories = new LinkedHashSet<>();   // empty = any
    public String startDate, endDate;         // yyyy-MM-dd, nullable
    public Double minAmount, maxAmount;
    public List<String> merchantTerms = new ArrayList<>();
    public Sort sort = Sort.DATE_DESC;
    public Integer limit;                     // "top 5"

    public enum Sort { DATE_DESC, AMOUNT_DESC, AMOUNT_ASC }

    public boolean isEmpty();
    public List<Chip> toChips();              // one removable chip per constraint

    public static class Chip {
        public final String label;
        public final Kind kind;               // CATEGORY, DATE, MIN_AMOUNT, MAX_AMOUNT, MERCHANT, SORT, LIMIT
        public final Object payload;          // what to remove when the chip is dismissed
    }
}
```

`isEmpty()` returns true only when every constraint is unset — this is the exact condition that gates the LLM fallback in Task 4.4.

- [ ] **Step 1: Implement.**
- [ ] **Step 2: Build.**
- [ ] **Step 3: Commit** — `git commit -am "feat(search): typed SearchFilter with removable chips"`

### Task 4.2: `QueryParser`

The current parser sweeps the whole string with independent regexes, which is why the amount fallback misfires on date digits. This one **tokenizes once and consumes tokens** (Spec §3).

**Files:**
- Create: `sense/search/QueryParser.java`
- Create: `app/src/test/java/.../QueryParserTest.java`

**Interfaces:**
- Consumes: `SearchFilter`, `SeedCorpus.keywords()`.
- Produces: `SearchFilter parse(String query)` and `SearchFilter parse(String query, LocalDate today)` — the second overload exists so tests pin a fixed "today" instead of depending on the wall clock.

- [ ] **Step 1: Write the failing table test** (~30 rows, Spec §9). The three rows the spec calls out explicitly:

```java
public class QueryParserTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 11, 25);

    private SearchFilter parse(String q) {
        return new QueryParser().parse(q, TODAY);
    }

    @Test
    public void lastMonth_isPreviousCalendarMonth() {
        SearchFilter f = parse("food last month");
        assertEquals("2025-10-01", f.startDate);
        assertEquals("2025-10-31", f.endDate);
    }

    @Test
    public void thisMonth_isFirstOfMonthThroughToday() {
        SearchFilter f = parse("this month");
        assertEquals("2025-11-01", f.startDate);
        assertEquals("2025-11-25", f.endDate);
    }

    @Test
    public void dateDigitsDoNotBecomeAnAmountFilter() {
        SearchFilter f = parse("food last week");
        assertNull(f.minAmount);
        assertNull(f.maxAmount);
        assertTrue(f.categories.contains(ExpenseCategory.FOOD));
    }

    @Test
    public void multipleCategories() {
        SearchFilter f = parse("food and groceries");
        assertEquals(2, f.categories.size());
    }

    @Test
    public void overAmount() {
        assertEquals(20.0, parse("over $20").minAmount, 0.001);
    }

    @Test
    public void betweenAmounts() {
        SearchFilter f = parse("between 10 and 50");
        assertEquals(10.0, f.minAmount, 0.001);
        assertEquals(50.0, f.maxAmount, 0.001);
    }

    @Test
    public void aroundIsTenPercentBand() {
        SearchFilter f = parse("around $50");
        assertEquals(45.0, f.minAmount, 0.001);
        assertEquals(55.0, f.maxAmount, 0.001);
    }

    @Test
    public void topNSetsLimitAndAmountSort() {
        SearchFilter f = parse("top 5 most expensive");
        assertEquals(Integer.valueOf(5), f.limit);
        assertEquals(SearchFilter.Sort.AMOUNT_DESC, f.sort);
    }

    @Test
    public void leftoverTokensBecomeMerchantTerms() {
        SearchFilter f = parse("starbucks last week");
        assertEquals(Collections.singletonList("starbucks"), f.merchantTerms);
    }

    @Test
    public void gibberishIsEmptySoTheLlmFallbackCanFire() {
        assertTrue(parse("blorp zzz").isEmpty()
                || !parse("blorp zzz").merchantTerms.isEmpty());
    }
}
```

Expand to ~30 rows covering the full grammar in Spec §3: `today`, `yesterday`, `this/last week`, `this/last month`, `this/last year`, `last N days|weeks|months`, `since <month|date>`, weekday names, explicit months (`november`, `nov 2025`), `over|above|more than|>`, `under|below|less than|<`, `most|least expensive`, `biggest`, `cheapest`.

- [ ] **Step 2: Run to verify failure** — `./gradlew testDebugUnitTest --tests "*QueryParserTest*"`. Expected: FAIL.

- [ ] **Step 3: Implement the tokenizer-consumer.** Key rule: **a bare number is treated as an amount only if no date token consumed it.** Categories come from the `SeedCorpus` keyword table (reused, not duplicated). Remaining tokens become `merchantTerms` after stop-word removal.

- [ ] **Step 4: Run tests** — expected PASS.
- [ ] **Step 5: Commit** — `git commit -am "feat(search): token-consuming query parser; last month is previous calendar month"`

### Task 4.3: `SearchFilterSqlBuilder` + `@RawQuery`

**Files:**
- Create: `sense/search/SearchFilterSqlBuilder.java`
- Modify: `data/ExpenseDao.java`
- Create: `app/src/test/java/.../SearchFilterSqlBuilderTest.java`

**Interfaces:**
- Produces: `static SupportSQLiteQuery build(SearchFilter f)` and, for the test, `static Compiled compile(SearchFilter f)` exposing `{String sql; Object[] args;}`.
- Adds to `ExpenseDao`: `@RawQuery List<Expense> search(SupportSQLiteQuery query);`

- [ ] **Step 1: Write the failing test** — each filter permutation compiles to valid parameterized SQL with the expected bind arguments (Spec §9). Assert bind args explicitly, e.g.:

```java
@Test
public void merchantTermsAreOredAcrossColumnsAndAndedAcrossTerms() {
    SearchFilter f = new SearchFilter();
    f.merchantTerms = Arrays.asList("starbucks", "latte");
    SearchFilterSqlBuilder.Compiled c = SearchFilterSqlBuilder.compile(f);
    assertEquals(6, c.args.length);           // 2 terms × 3 columns
    assertTrue(c.sql.contains("merchant LIKE ?"));
    assertTrue(c.sql.contains("title LIKE ?"));
    assertTrue(c.sql.contains("note LIKE ?"));
}

@Test
public void emptyFilterSelectsEverythingDateDesc() {
    SearchFilterSqlBuilder.Compiled c = SearchFilterSqlBuilder.compile(new SearchFilter());
    assertEquals(0, c.args.length);
    assertTrue(c.sql.contains("ORDER BY date DESC"));
}
```

- [ ] **Step 2: Run to verify failure.**
- [ ] **Step 3: Implement.** Merchant terms match `merchant LIKE %term%` OR `title LIKE %term%` OR `note LIKE %term%`, ANDed across terms (Spec §3). Everything is a bind argument — no string concatenation of user input into SQL.
- [ ] **Step 4: Run tests** — expected PASS.
- [ ] **Step 5: Commit** — `git commit -am "feat(search): compile SearchFilter to parameterized SQL via @RawQuery"`

### Task 4.4: `LlmFilterTranslator`; delete the prose-answer path

**Files:**
- Create: `sense/search/LlmFilterTranslator.java`
- Modify: `sense/OpenAIService.java` — delete `searchExpenses` and `buildSearchSystemPrompt`
- Delete: `NaturalLanguageParser.java`

**Interfaces:**
- Produces: `void translate(String query, Callback cb)` where `Callback` is `onFilter(SearchFilter)` / `onUnparseable()`.

- [ ] **Step 1: Implement the gate.** Runs only when **all** hold: the local parser returned `isEmpty()`, an API key is configured, and the network is available (Spec §3).
- [ ] **Step 2: Implement the request.** Sends the query string and the category enum names, requests a JSON object matching `SearchFilter`. **It never sends transactions.**
- [ ] **Step 3: Validate field-by-field before use.** Anything unparseable is discarded and the UI reports that the query was not understood. There is no prose-answer path.
- [ ] **Step 4: Delete** `OpenAIService.searchExpenses`, `OpenAIService.buildSearchSystemPrompt`, and `NaturalLanguageParser.java`. Keep `OpenAIService.sendMessage` and the chat prompt — Ask mode (Task 6.4) still uses them.
- [ ] **Step 5: Build + full unit suite.** `./gradlew testDebugUnitTest assembleDebug`.
- [ ] **Step 6: Commit** — `git commit -am "feat(search): LLM translates to a filter only, never sees transactions"`

---

## Phase 5 — Visual system

### Task 5.1: Theme, tokens, category palette

**Files:**
- Modify: `res/values/colors.xml`, `res/values/themes.xml`, `res/values/strings.xml`
- Create: `res/values/attrs.xml` (custom theme attributes)
- Delete: `res/values-night/themes.xml` (and the `values-night/` directory)
- Modify: `AndroidManifest.xml`

- [ ] **Step 1: Declare the custom attrs**

```xml
<resources>
    <attr name="senseHairline" format="color" />
    <attr name="senseTextTertiary" format="color" />
    <attr name="sensePositive" format="color" />
</resources>
```

- [ ] **Step 2: Write `colors.xml`** with the eleven token values and the seven category colors from Global Constraints. Each category color must clear 4.5:1 against `#0B0D0C` when used as text (Spec §5); verify rather than assume.

- [ ] **Step 3: Write `Theme.GetSense`**

Parent is `Theme.Material3.Dark.NoActionBar` — a **fixed dark parent, not `DayNight`**, so the system setting cannot flip it. Also set `android:forceDarkAllowed="false"`, which prevents the platform's auto-dark pass from re-tinting anything; note it does not by itself force dark mode and is not what makes this theme dark (Spec §5).

- [ ] **Step 4: Delete `values-night/`.** A single palette leaves nothing for it to override.

- [ ] **Step 5: Point the manifest at it** — `android:theme="@style/Theme.GetSense"`, `android:label="getSense AI"`, and **remove** `android:usesCleartextTraffic="true"` (Spec §10).

- [ ] **Step 6: Build and eyeball.** `./gradlew assembleDebug`, install, confirm the app is dark and the accent renders.

- [ ] **Step 7: Commit** — `git commit -am "feat(ui): dark instrument-panel theme, token attrs, drop night qualifier"`

### Task 5.2: Typography

**Files:**
- Create: `res/font/` with Space Grotesk and Inter (OFL), `res/font/space_grotesk.xml`, `res/font/inter.xml`
- Modify: `res/values/themes.xml` (text appearances)

**Blocked pending user action:** the two OFL font families must be added to `res/font/`. Downloading them is a file download and needs explicit approval; see the Open Questions section. Until they land, the text appearances below use `sans-serif` / `sans-serif-medium` so the build stays green, and swapping in the real families is a one-line change per appearance.

- [ ] **Step 1: Add the font resources** (once approved) — Space Grotesk for display numerals with `fontFeatureSettings="tnum"` so digits do not jitter during animation; Inter for all text.
- [ ] **Step 2: Define the four text appearances** (Spec §5):

| Style | Size | Treatment |
|---|---|---|
| `TextAppearance.GetSense.DisplayAmount` | 44sp | Space Grotesk Medium, tabular |
| `TextAppearance.GetSense.Title` | 20sp | Inter Medium |
| `TextAppearance.GetSense.Body` | 15sp | Inter Regular |
| `TextAppearance.GetSense.Label` | 11sp | Inter Medium, uppercase, `0.12` letter-spacing |

The uppercase spaced label does most of the "instrument" work and is used for **every** section header and metadata row.

- [ ] **Step 3: Build.** `./gradlew assembleDebug`.
- [ ] **Step 4: Commit** — `git commit -am "feat(ui): getSense type scale"`

### Task 5.3: The three custom views

**Files:**
- Create: `ui/widget/BudgetMeterView.java`, `ui/widget/SparklineView.java`, `ui/widget/ReceiptOverlayView.java`

**Interfaces:**
- `BudgetMeterView`: `void setValue(double spent, double budget)` — sweeps 0→value on load via `ValueAnimator`, shifts from `colorPrimary` toward `colorError` as it crosses 80% and 100% (Spec §7).
- `SparklineView`: `void setPoints(List<Double> last30Days)` — animated with `PathMeasure` trim.
- `ReceiptOverlayView`: `void reveal(ReceiptExtraction e, Runnable onDone)` and `void skip()` — dims the receipt, draws the detected `Field.boundingBox` regions, walks merchant → total → date with an `AnimatorSet`, ~1.8s total, skippable on tap.

All three read their colors from theme attributes, never hex.

- [ ] **Step 1: Implement `BudgetMeterView`.**
- [ ] **Step 2: Implement `SparklineView`.**
- [ ] **Step 3: Implement `ReceiptOverlayView`.**
- [ ] **Step 4: Build.** `./gradlew assembleDebug`.
- [ ] **Step 5: Commit** — `git commit -am "feat(ui): budget meter, sparkline, receipt overlay custom views"`

---

## Phase 6 — Screens, navigation, motion

Bottom navigation: **Home · Scan · Search · Insights** (Spec §6).

### Task 6.1: Package restructure

- [ ] **Step 1:** Move every file into the `data/`, `sense/`, `ui/` packages per the File Structure section. Rename `MainActivity` → `ui/home/HomeActivity`; the launcher intent filter moves with it.
- [ ] **Step 2:** Update every `package` and `import`.
- [ ] **Step 3: Build.** `./gradlew assembleDebug testDebugUnitTest`.
- [ ] **Step 4: Commit** — `git commit -am "refactor: data/sense/ui package structure"`

### Task 6.2: Home

**Files:** `ui/home/HomeActivity.java`, `ui/home/HomeViewModel.java`, `ui/home/ExpenseAdapter.java`, `res/layout/activity_home.xml`, `res/layout/item_expense.xml`

- [ ] **Step 1:** MotionLayout collapsing month header — display amount 44sp → 20sp, docking into the toolbar on scroll — with `BudgetMeterView` beside it.
- [ ] **Step 2:** "Needs review" strip listing expenses whose `predictionConfidence` is below `Sense.REVIEW_THRESHOLD` (uses `observeNeedsReview` from Task 1.4).
- [ ] **Step 3:** Transaction feed. Each row shows merchant, amount, and a metadata line of `CATEGORY · scanned|typed · NN%` in the Label appearance.
- [ ] **Step 4:** `DiffUtil` on the adapter (replacing `notifyDataSetChanged`), plus swipe-to-recategorize on the row.
- [ ] **Step 5:** Hairline dividers replace card chrome — every current card becomes a section separated by a 1dp `senseHairline` rule.
- [ ] **Step 6: Build and run.** Confirm the feed renders and the meter animates.
- [ ] **Step 7: Commit** — `git commit -am "feat(ui): Home with collapsing header, review strip, diffed feed"`

### Task 6.3: Scan

**Files:** `ui/scan/ScanActivity.java`, `ui/scan/ScanViewModel.java`, `res/layout/activity_scan.xml`, `res/layout/sheet_receipt_review.xml`

- [ ] **Step 1:** Camera or gallery capture, then `ReceiptParser.parse`.
- [ ] **Step 2:** The reveal sequence via `ReceiptOverlayView` — each box highlights while its value types into the corresponding form field.
- [ ] **Step 3:** The review sheet. `MaterialCardView` survives **only** here, where the surface is genuinely liftable (Spec §5).
- [ ] **Step 4:** Confidence ring — a category below `Sense.REVIEW_THRESHOLD` renders with an amber ring. Tapping opens the category picker; choosing a value runs the `unlearn`/`learn` transaction, plays a ring-resolve animation, and fires a `CONFIRM` haptic.
- [ ] **Step 5:** On save, run `classifier.learn()`; if the category was changed from the prediction, `unlearn(predicted)` first. `learn` is called on **every** expense save, scanned or manual (Spec §2).
- [ ] **Step 6: Build and run** a real scan end to end.
- [ ] **Step 7: Commit** — `git commit -am "feat(ui): Scan with reveal animation and visible learning loop"`

### Task 6.4: Search

**Files:** `ui/search/SearchActivity.java`, `ui/search/SearchViewModel.java`, `ui/search/ChipAdapter.java`, `ui/search/ChatMessageAdapter.java`, `res/layout/activity_search.xml`

- [ ] **Step 1:** Query field, interpreted-filter chip row, results list.
- [ ] **Step 2:** Chips animate in as `QueryParser` resolves the query ("Food · Nov 1–30 · over $20"). Each is removable; removing one rebuilds the `SearchFilter`, re-runs the query, and animates the result diff.
- [ ] **Step 3:** Find/Ask toggle. Find = local structured path. Ask = the OpenAI conversational path, folding in `ChatActivity`'s logic.
- [ ] **Step 4:** State plainly on screen that **Ask mode sends data to OpenAI while Find mode does not** (Spec §3, §6).
- [ ] **Step 5: Build and run.** Try "food last month over $20" and confirm the chips and results match.
- [ ] **Step 6: Commit** — `git commit -am "feat(ui): Search with legible parse chips and an explicit Ask-mode disclosure"`

### Task 6.5: Insights

**Files:** `ui/insights/InsightsActivity.java`, `ui/insights/InsightsViewModel.java`, the three chart fragments, `CategoryExpensesActivity`

- [ ] **Step 1:** Keep the existing `PieChartFragment`, `BarChartFragment`, `TrendChartFragment` in their `ViewPager2`.
- [ ] **Step 2:** Restyle to the token system — transparent chart backgrounds, hairline axes, category palette, **no chart legend boxes**.
- [ ] **Step 3:** `CategoryExpensesActivity` is retained as the drill-down target.
- [ ] **Step 4:** Shared-element transition from a transaction row into its detail, expanding the receipt thumbnail.
- [ ] **Step 5: Build and run.**
- [ ] **Step 6: Commit** — `git commit -am "feat(ui): Insights restyled to the token system"`

---

## Phase 7 — Removals, security, final verification

### Task 7.1: Delete what the spec cuts

- [ ] **Step 1:** Remove the streak and badge card from the old `activity_main.xml` (lines 255–358) and delete the `gold/silver/bronze/locked/unlocked.png` drawables.
- [ ] **Step 2:** `git rm` `CsvPreviewActivity.java`, `activity_csv_preview.xml`, and its manifest entry.
- [ ] **Step 3:** `git rm` `ChatActivity.java` and `activity_chat.xml` — its logic now lives in Search's Ask mode. Remove its manifest entry.
- [ ] **Step 4:** `git rm` `SummaryActivity.java`, `ChartsActivity.java`, `activity_summary.xml`, `activity_charts.xml` and their manifest entries.
- [ ] **Step 5:** `git rm` `ExampleUnitTest.java`.
- [ ] **Step 6: Build.** `./gradlew assembleDebug`.
- [ ] **Step 7: Commit** — `git commit -am "chore: cut streak/badges, CSV preview, standalone chat and summary screens"`

### Task 7.2: Security pass

- [ ] **Step 1:** Confirm `android:usesCleartextTraffic="true"` is gone from the manifest and nothing regressed it.
- [ ] **Step 2:** Confirm receipt images live in `filesDir` (app-private) and are excluded from backup via the existing `backup_rules.xml`.
- [ ] **Step 3:** Add a comment above the `buildConfigField` in `app/build.gradle.kts` recording the accepted limitation: the key is kept out of version control but **is** baked into the APK and recoverable by anyone who unpacks the build. Acceptable for coursework, not for distribution; a real release needs a server-side proxy (Spec §10).
- [ ] **Step 4: Commit** — `git commit -am "chore(security): drop cleartext traffic, document the API-key limitation"`

### Task 7.3: Full verification

- [ ] **Step 1:** `./gradlew clean assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 2:** `./gradlew testDebugUnitTest` → all of `ReceiptParserTest`, `NaiveBayesClassifierTest`, `QueryParserTest`, `SearchFilterSqlBuilderTest` green. Paste the actual summary line.
- [ ] **Step 3:** `./gradlew connectedDebugAndroidTest` → `MigrationTest` green (needs an emulator).
- [ ] **Step 4:** Install and walk all four screens: Home renders seeded data, Scan reads a receipt, Search resolves "food last month" into chips, Insights charts render.
- [ ] **Step 5: Commit** — `git commit -am "chore: verified build and full test suite"`

---

## Open Questions

Two items need a decision before the tasks that depend on them can complete. Everything else in this plan is unblocked.

1. **Font files (Task 5.2).** Space Grotesk and Inter are OFL and must be bundled in `res/font/` — the spec is explicit that there is no downloadable-fonts dependency, so the app works offline. Fetching two font families is a file download and needs approval. Until then Task 5.2 ships with `sans-serif` and the swap is one line per text appearance.

2. **Receipt fixtures (Task 2.2).** `ReceiptParserTest` needs ~10 real receipt OCR texts as fixtures, including two where the largest number is not the total. These can be hand-written to match real receipt layouts, but hand-written fixtures test the algorithm against my assumptions rather than against reality. Real OCR dumps from actual receipts would make this suite meaningfully stronger.

---

## Self-Review

**Spec coverage.** §1 capture → Tasks 2.1–2.4. §2 classify → 3.1–3.3. §3 search → 4.1–4.4. §4 data model → 1.1–1.5. §5 visual system → 5.1–5.3. §6 screens → 6.2–6.5, removals in 7.1. §7 motion → the three story items land in 5.3/6.2/6.3/6.4; supporting motion in 6.2 (MotionLayout, DiffUtil, swipe), 6.5 (shared element). §8 package structure → 6.1. §9 testing → 1.5, 2.2, 3.1, 4.2, 4.3. §10 security → 7.2. §11 out of scope → Global Constraints.

**Type consistency.** `Sense.REVIEW_THRESHOLD` is the single threshold name throughout (not `REVIEW_THRESHOLD` bare, not `CONFIDENCE_THRESHOLD`). `Prediction.evidenceTokens` is `List<String>` everywhere. `SearchFilter.Sort` is nested, referenced as `SearchFilter.Sort.AMOUNT_DESC`. `ReceiptParser.ParsedLine` is the pure-path input type in both the implementation and the test.

**Known plan gaps, stated rather than papered over.** Phases 5 and 6 specify files, interfaces, exact token values, and per-step behavior, but do not inline full XML layouts or full custom-view drawing code the way Phases 1–4 inline Java. Those phases are visual work whose correctness is judged by eye against §5–§7, not by an assertion, so the plan pins the values and leaves the drawing. An implementer following Phase 6 will be writing original layout XML, not transcribing it.
