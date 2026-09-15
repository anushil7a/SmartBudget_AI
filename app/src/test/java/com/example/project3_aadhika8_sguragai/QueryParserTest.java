package com.example.project3_aadhika8_sguragai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.project3_aadhika8_sguragai.data.ExpenseCategory;
import com.example.project3_aadhika8_sguragai.sense.search.QueryParser;
import com.example.project3_aadhika8_sguragai.sense.search.SearchFilter;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Collections;

/**
 * The old parser swept the whole query with independent regexes, so the amount rule fired on
 * digits that belonged to a date, and "last month" resolved to the first of the <em>current</em>
 * month. Both are pinned here.
 */
public class QueryParserTest {

    /** A Tuesday, deliberately mid-month and mid-week. */
    private static final LocalDate TODAY = LocalDate.of(2025, 11, 25);

    private SearchFilter parse(String q) {
        return new QueryParser().parse(q, TODAY);
    }

    // ---- Relative dates ----

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
    public void today() {
        SearchFilter f = parse("today");
        assertEquals("2025-11-25", f.startDate);
        assertEquals("2025-11-25", f.endDate);
    }

    @Test
    public void yesterday() {
        SearchFilter f = parse("yesterday");
        assertEquals("2025-11-24", f.startDate);
        assertEquals("2025-11-24", f.endDate);
    }

    @Test
    public void thisWeek_startsMonday() {
        SearchFilter f = parse("this week");
        assertEquals("2025-11-24", f.startDate);
        assertEquals("2025-11-25", f.endDate);
    }

    @Test
    public void lastWeek_isThePreviousMondayToSunday() {
        SearchFilter f = parse("last week");
        assertEquals("2025-11-17", f.startDate);
        assertEquals("2025-11-23", f.endDate);
    }

    @Test
    public void thisYear() {
        SearchFilter f = parse("this year");
        assertEquals("2025-01-01", f.startDate);
        assertEquals("2025-11-25", f.endDate);
    }

    @Test
    public void lastYear() {
        SearchFilter f = parse("last year");
        assertEquals("2024-01-01", f.startDate);
        assertEquals("2024-12-31", f.endDate);
    }

    @Test
    public void lastNDays() {
        SearchFilter f = parse("last 7 days");
        assertEquals("2025-11-19", f.startDate);
        assertEquals("2025-11-25", f.endDate);
    }

    @Test
    public void lastNWeeks() {
        SearchFilter f = parse("last 2 weeks");
        assertEquals("2025-11-12", f.startDate);
        assertEquals("2025-11-25", f.endDate);
    }

    @Test
    public void lastNMonths() {
        SearchFilter f = parse("past 3 months");
        assertEquals("2025-08-25", f.startDate);
        assertEquals("2025-11-25", f.endDate);
    }

    @Test
    public void explicitMonthName_resolvesToThatWholeMonth() {
        SearchFilter f = parse("november");
        assertEquals("2025-11-01", f.startDate);
        assertEquals("2025-11-30", f.endDate);
    }

    @Test
    public void explicitMonthAndYear() {
        SearchFilter f = parse("nov 2024");
        assertEquals("2024-11-01", f.startDate);
        assertEquals("2024-11-30", f.endDate);
    }

    @Test
    public void monthNameInTheFuture_rollsBackToLastYear() {
        SearchFilter f = parse("december");
        assertEquals("2024-12-01", f.startDate);
        assertEquals("2024-12-31", f.endDate);
    }

    @Test
    public void sinceAMonth() {
        SearchFilter f = parse("since october");
        assertEquals("2025-10-01", f.startDate);
        assertEquals("2025-11-25", f.endDate);
    }

    // ---- Categories ----

    @Test
    public void singleCategory() {
        SearchFilter f = parse("food");
        assertEquals(Collections.singleton(ExpenseCategory.FOOD), f.categories);
    }

    @Test
    public void multipleCategories() {
        SearchFilter f = parse("food and groceries");
        assertEquals(2, f.categories.size());
        assertTrue(f.categories.contains(ExpenseCategory.FOOD));
        assertTrue(f.categories.contains(ExpenseCategory.GROCERIES));
    }

    @Test
    public void categorySynonym() {
        assertTrue(parse("dining").categories.contains(ExpenseCategory.FOOD));
        assertTrue(parse("utilities").categories.contains(ExpenseCategory.BILLS));
    }

    @Test
    public void brandNameIsAMerchantTermNotACategory() {
        // "starbucks" is in the classifier's seed vocabulary, but searching for it should
        // find Starbucks rows, not every FOOD row.
        SearchFilter f = parse("starbucks");
        assertTrue(f.categories.isEmpty());
        assertEquals(Collections.singletonList("starbucks"), f.merchantTerms);
    }

    // ---- Amounts ----

    @Test
    public void overAmount() {
        assertEquals(20.0, parse("over $20").minAmount, 0.001);
    }

    @Test
    public void aboveAndMoreThanAreSynonyms() {
        assertEquals(20.0, parse("above 20").minAmount, 0.001);
        assertEquals(20.0, parse("more than 20").minAmount, 0.001);
    }

    @Test
    public void underAmount() {
        assertEquals(15.0, parse("under $15").maxAmount, 0.001);
        assertEquals(15.0, parse("less than 15").maxAmount, 0.001);
    }

    @Test
    public void betweenAmounts() {
        SearchFilter f = parse("between 10 and 50");
        assertEquals(10.0, f.minAmount, 0.001);
        assertEquals(50.0, f.maxAmount, 0.001);
    }

    @Test
    public void aroundIsATenPercentBand() {
        SearchFilter f = parse("around $50");
        assertEquals(45.0, f.minAmount, 0.001);
        assertEquals(55.0, f.maxAmount, 0.001);
    }

    // ---- The bug that motivated the rewrite ----

    @Test
    public void dateDigitsDoNotBecomeAnAmountFilter() {
        SearchFilter f = parse("food last week");
        assertNull("no number was spoken as an amount", f.minAmount);
        assertNull(f.maxAmount);
        assertTrue(f.categories.contains(ExpenseCategory.FOOD));
    }

    @Test
    public void lastSevenDaysDoesNotSetAnAmountOfSeven() {
        SearchFilter f = parse("last 7 days");
        assertNull(f.minAmount);
        assertNull(f.maxAmount);
    }

    @Test
    public void topFiveDoesNotSetAnAmountOfFive() {
        SearchFilter f = parse("top 5 most expensive");
        assertNull(f.minAmount);
        assertNull(f.maxAmount);
    }

    // ---- Sort and limit ----

    @Test
    public void topNSetsLimitAndAmountSort() {
        SearchFilter f = parse("top 5 most expensive");
        assertEquals(Integer.valueOf(5), f.limit);
        assertEquals(SearchFilter.Sort.AMOUNT_DESC, f.sort);
    }

    @Test
    public void biggestSortsByAmountDescending() {
        assertEquals(SearchFilter.Sort.AMOUNT_DESC, parse("biggest expenses").sort);
    }

    @Test
    public void cheapestSortsByAmountAscending() {
        assertEquals(SearchFilter.Sort.AMOUNT_ASC, parse("cheapest").sort);
        assertEquals(SearchFilter.Sort.AMOUNT_ASC, parse("least expensive").sort);
    }

    @Test
    public void defaultSortIsNewestFirst() {
        assertEquals(SearchFilter.Sort.DATE_DESC, parse("food").sort);
    }

    // ---- Merchant terms and leftovers ----

    @Test
    public void leftoverTokensBecomeMerchantTerms() {
        SearchFilter f = parse("starbucks last week");
        assertEquals(Collections.singletonList("starbucks"), f.merchantTerms);
        assertEquals("2025-11-17", f.startDate);
    }

    @Test
    public void stopWordsAreDropped() {
        SearchFilter f = parse("show me the starbucks");
        assertEquals(Collections.singletonList("starbucks"), f.merchantTerms);
    }

    // ---- Composition and emptiness ----

    @Test
    public void fullQueryCompiles() {
        SearchFilter f = parse("food last month over $20");
        assertTrue(f.categories.contains(ExpenseCategory.FOOD));
        assertEquals("2025-10-01", f.startDate);
        assertEquals("2025-10-31", f.endDate);
        assertEquals(20.0, f.minAmount, 0.001);
        assertFalse(f.isEmpty());
    }

    @Test
    public void emptyQueryIsEmpty() {
        assertTrue(parse("").isEmpty());
        assertTrue(parse("   ").isEmpty());
    }

    @Test
    public void pureStopWordsAreEmptySoTheLlmFallbackCanFire() {
        assertTrue(parse("show me the").isEmpty());
    }

    @Test
    public void chipsDescribeEveryConstraint() {
        SearchFilter f = parse("food last month over $20");
        assertEquals(3, f.toChips().size());   // category, date range, min amount
    }

    @Test
    public void removingAChipDropsThatConstraintOnly() {
        SearchFilter f = parse("food last month over $20");
        SearchFilter.Chip amountChip = f.toChips().stream()
                .filter(c -> c.kind == SearchFilter.ChipKind.MIN_AMOUNT)
                .findFirst().orElseThrow(AssertionError::new);
        f.remove(amountChip);
        assertNull(f.minAmount);
        assertTrue(f.categories.contains(ExpenseCategory.FOOD));
        assertEquals("2025-10-01", f.startDate);
    }
}
