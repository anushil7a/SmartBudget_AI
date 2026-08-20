package com.example.project3_aadhika8_sguragai.sense.search;

import com.example.project3_aadhika8_sguragai.data.ExpenseCategory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What a plain-language query actually asked for.
 *
 * <p>Queries compile to this and this compiles to SQL, run locally. The point of making the
 * intermediate form a typed object rather than a prose round-trip is that it can be shown to
 * the user as chips, argued with, and corrected — and that nothing leaves the device.
 */
public class SearchFilter {

    public enum Sort {
        DATE_DESC,
        AMOUNT_DESC,
        AMOUNT_ASC
    }

    public enum ChipKind {
        CATEGORY,
        DATE,
        MIN_AMOUNT,
        MAX_AMOUNT,
        MERCHANT,
        SORT,
        LIMIT
    }

    /** Empty means "any category". */
    public Set<ExpenseCategory> categories = new LinkedHashSet<>();

    /** yyyy-MM-dd, nullable. */
    public String startDate;
    public String endDate;

    public Double minAmount;
    public Double maxAmount;

    public List<String> merchantTerms = new ArrayList<>();

    public Sort sort = Sort.DATE_DESC;

    /** "top 5" */
    public Integer limit;

    /**
     * True only when nothing at all was understood. This is the exact condition that lets the
     * LLM fallback fire, so it deliberately ignores {@link #sort}, which always has a value.
     */
    public boolean isEmpty() {
        return categories.isEmpty()
                && startDate == null
                && endDate == null
                && minAmount == null
                && maxAmount == null
                && merchantTerms.isEmpty()
                && limit == null;
    }

    /** One removable chip per constraint, in the order a person would read them. */
    public List<Chip> toChips() {
        List<Chip> chips = new ArrayList<>();
        for (ExpenseCategory c : categories) {
            chips.add(new Chip(prettyCategory(c), ChipKind.CATEGORY, c));
        }
        if (startDate != null || endDate != null) {
            chips.add(new Chip(prettyDateRange(), ChipKind.DATE, null));
        }
        if (minAmount != null) {
            chips.add(new Chip(String.format(Locale.US, "over $%.0f", minAmount),
                    ChipKind.MIN_AMOUNT, minAmount));
        }
        if (maxAmount != null) {
            chips.add(new Chip(String.format(Locale.US, "under $%.0f", maxAmount),
                    ChipKind.MAX_AMOUNT, maxAmount));
        }
        for (String term : merchantTerms) {
            chips.add(new Chip("“" + term + "”", ChipKind.MERCHANT, term));
        }
        if (sort != Sort.DATE_DESC) {
            chips.add(new Chip(sort == Sort.AMOUNT_DESC ? "largest first" : "smallest first",
                    ChipKind.SORT, sort));
        }
        if (limit != null) {
            chips.add(new Chip("top " + limit, ChipKind.LIMIT, limit));
        }
        return chips;
    }

    /** Drop one constraint; the caller then re-runs the query. */
    public void remove(Chip chip) {
        switch (chip.kind) {
            case CATEGORY:
                categories.remove((ExpenseCategory) chip.payload);
                break;
            case DATE:
                startDate = null;
                endDate = null;
                break;
            case MIN_AMOUNT:
                minAmount = null;
                break;
            case MAX_AMOUNT:
                maxAmount = null;
                break;
            case MERCHANT:
                merchantTerms.remove((String) chip.payload);
                break;
            case SORT:
                sort = Sort.DATE_DESC;
                break;
            case LIMIT:
                limit = null;
                break;
        }
    }

    private String prettyCategory(ExpenseCategory c) {
        String n = c.name().toLowerCase(Locale.US);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private String prettyDateRange() {
        if (startDate != null && endDate != null) {
            return startDate + " – " + endDate;
        }
        return startDate != null ? "since " + startDate : "until " + endDate;
    }

    public static class Chip {
        public final String label;
        public final ChipKind kind;
        /** What to remove when the chip is dismissed. */
        public final Object payload;

        public Chip(String label, ChipKind kind, Object payload) {
            this.label = label;
            this.kind = kind;
            this.payload = payload;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
