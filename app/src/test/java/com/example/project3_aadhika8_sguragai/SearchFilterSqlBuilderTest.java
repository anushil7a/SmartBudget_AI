package com.example.project3_aadhika8_sguragai;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.project3_aadhika8_sguragai.data.ExpenseCategory;
import com.example.project3_aadhika8_sguragai.sense.search.QueryParser;
import com.example.project3_aadhika8_sguragai.sense.search.SearchFilter;
import com.example.project3_aadhika8_sguragai.sense.search.SearchFilterSqlBuilder;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;

/**
 * Every user-supplied value must arrive as a bind argument, and each filter permutation must
 * produce a statement that says what the filter meant.
 */
public class SearchFilterSqlBuilderTest {

    private SearchFilterSqlBuilder.Compiled compile(SearchFilter f) {
        return SearchFilterSqlBuilder.compile(f);
    }

    @Test
    public void emptyFilterSelectsEverythingNewestFirst() {
        SearchFilterSqlBuilder.Compiled c = compile(new SearchFilter());
        assertEquals(0, c.args.length);
        assertFalse("nothing to filter on, so no WHERE", c.sql.contains("WHERE"));
        assertTrue(c.sql.contains("ORDER BY date DESC, id DESC"));
    }

    @Test
    public void singleCategoryBindsItsName() {
        SearchFilter f = new SearchFilter();
        f.categories.add(ExpenseCategory.FOOD);
        SearchFilterSqlBuilder.Compiled c = compile(f);
        assertTrue(c.sql.contains("category IN (?)"));
        assertArrayEquals(new Object[]{"FOOD"}, c.args);
    }

    @Test
    public void multipleCategoriesBindOnePlaceholderEach() {
        SearchFilter f = new SearchFilter();
        f.categories.add(ExpenseCategory.FOOD);
        f.categories.add(ExpenseCategory.GROCERIES);
        SearchFilterSqlBuilder.Compiled c = compile(f);
        assertTrue(c.sql.contains("category IN (?, ?)"));
        assertEquals(2, c.args.length);
    }

    @Test
    public void dateRangeBindsBothEnds() {
        SearchFilter f = new SearchFilter();
        f.startDate = "2025-10-01";
        f.endDate = "2025-10-31";
        SearchFilterSqlBuilder.Compiled c = compile(f);
        assertTrue(c.sql.contains("date >= ?"));
        assertTrue(c.sql.contains("date <= ?"));
        assertArrayEquals(new Object[]{"2025-10-01", "2025-10-31"}, c.args);
    }

    @Test
    public void openEndedDateRangeOnlyBindsTheEndItHas() {
        SearchFilter f = new SearchFilter();
        f.startDate = "2025-10-01";
        SearchFilterSqlBuilder.Compiled c = compile(f);
        assertTrue(c.sql.contains("date >= ?"));
        assertFalse(c.sql.contains("date <= ?"));
        assertEquals(1, c.args.length);
    }

    @Test
    public void amountBoundsBind() {
        SearchFilter f = new SearchFilter();
        f.minAmount = 10.0;
        f.maxAmount = 50.0;
        SearchFilterSqlBuilder.Compiled c = compile(f);
        assertTrue(c.sql.contains("amount >= ?"));
        assertTrue(c.sql.contains("amount <= ?"));
        assertArrayEquals(new Object[]{10.0, 50.0}, c.args);
    }

    @Test
    public void merchantTermsAreOredAcrossColumnsAndAndedAcrossTerms() {
        SearchFilter f = new SearchFilter();
        f.merchantTerms = Arrays.asList("starbucks", "latte");
        SearchFilterSqlBuilder.Compiled c = compile(f);

        assertEquals("two terms across three columns", 6, c.args.length);
        assertTrue(c.sql.contains("(merchant LIKE ? OR title LIKE ? OR note LIKE ?)"));
        assertEquals("%starbucks%", c.args[0]);
        assertEquals("%latte%", c.args[3]);
        assertTrue("terms are ANDed", c.sql.contains(") AND ("));
    }

    @Test
    public void merchantTermIsBoundNotInterpolated() {
        SearchFilter f = new SearchFilter();
        f.merchantTerms = Arrays.asList("o'brien'; DROP TABLE expenses;--");
        SearchFilterSqlBuilder.Compiled c = compile(f);
        assertFalse("user text must never reach the statement",
                c.sql.contains("DROP TABLE"));
        assertEquals(3, c.args.length);
    }

    @Test
    public void sortByAmountDescending() {
        SearchFilter f = new SearchFilter();
        f.sort = SearchFilter.Sort.AMOUNT_DESC;
        assertTrue(compile(f).sql.contains("ORDER BY amount DESC"));
    }

    @Test
    public void sortByAmountAscending() {
        SearchFilter f = new SearchFilter();
        f.sort = SearchFilter.Sort.AMOUNT_ASC;
        assertTrue(compile(f).sql.contains("ORDER BY amount ASC"));
    }

    @Test
    public void limitBinds() {
        SearchFilter f = new SearchFilter();
        f.limit = 5;
        SearchFilterSqlBuilder.Compiled c = compile(f);
        assertTrue(c.sql.contains("LIMIT ?"));
        assertEquals(5, c.args[c.args.length - 1]);
    }

    @Test
    public void zeroLimitIsIgnored() {
        SearchFilter f = new SearchFilter();
        f.limit = 0;
        assertFalse(compile(f).sql.contains("LIMIT"));
    }

    @Test
    public void everyConstraintTogetherProducesOneCoherentStatement() {
        SearchFilter f = new QueryParser()
                .parse("food last month over $20", LocalDate.of(2025, 11, 25));
        SearchFilterSqlBuilder.Compiled c = compile(f);

        assertTrue(c.sql.startsWith("SELECT * FROM expenses WHERE "));
        assertTrue(c.sql.contains("category IN (?)"));
        assertTrue(c.sql.contains("date >= ?"));
        assertTrue(c.sql.contains("date <= ?"));
        assertTrue(c.sql.contains("amount >= ?"));
        assertArrayEquals(
                new Object[]{"FOOD", "2025-10-01", "2025-10-31", 20.0},
                c.args);
    }

    @Test
    public void placeholderCountMatchesArgumentCount() {
        SearchFilter f = new QueryParser()
                .parse("starbucks food last month between 10 and 50 top 5",
                        LocalDate.of(2025, 11, 25));
        SearchFilterSqlBuilder.Compiled c = compile(f);
        long placeholders = c.sql.chars().filter(ch -> ch == '?').count();
        assertEquals("a mismatch here is a runtime bind error",
                placeholders, c.args.length);
    }
}
