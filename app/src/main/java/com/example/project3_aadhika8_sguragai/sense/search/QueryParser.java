package com.example.project3_aadhika8_sguragai.sense.search;

import com.example.project3_aadhika8_sguragai.data.ExpenseCategory;
import com.example.project3_aadhika8_sguragai.sense.classify.SeedCorpus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Compiles a plain-language query into a {@link SearchFilter}.
 *
 * <p>Tokenises once and <em>consumes</em> tokens as each rule claims them. The previous
 * implementation swept the whole string with independent regexes, which is why "last 7 days"
 * also set an amount filter of 7 — the amount rule never knew the date rule had already
 * spoken for that number. Here a bare number can only become an amount if nothing else took
 * it.
 */
public class QueryParser {

    private static final DateTimeFormatter OUT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String[] MONTHS = {
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december"
    };

    private static final String[] MONTH_ABBREV = {
            "jan", "feb", "mar", "apr", "may", "jun",
            "jul", "aug", "sep", "oct", "nov", "dec"
    };

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "the", "me", "my", "show", "find", "get", "all", "any", "of", "for",
            "on", "in", "at", "to", "from", "with", "and", "or", "what", "which", "how",
            "much", "did", "do", "i", "spend", "spent", "was", "were", "is", "are", "that",
            "this", "it", "there", "please", "list", "search", "look", "up", "expenses",
            "expense", "transactions", "transaction", "purchases", "purchase", "cost",
            "costs", "paid", "pay", "s"
    ));

    /** Tokens the query is split into, with a consumed flag per token. */
    private static class Tokens {
        final String[] words;
        final boolean[] used;

        Tokens(String[] words) {
            this.words = words;
            this.used = new boolean[words.length];
        }

        boolean free(int i) {
            return i >= 0 && i < words.length && !used[i];
        }

        String at(int i) {
            return i >= 0 && i < words.length ? words[i] : "";
        }

        void consume(int from, int toInclusive) {
            for (int i = Math.max(0, from); i <= Math.min(used.length - 1, toInclusive); i++) {
                used[i] = true;
            }
        }
    }

    public SearchFilter parse(String query) {
        return parse(query, LocalDate.now());
    }

    public SearchFilter parse(String query, LocalDate today) {
        SearchFilter f = new SearchFilter();
        if (query == null || query.trim().isEmpty()) {
            return f;
        }

        String normalised = query.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9$.<>\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalised.isEmpty()) {
            return f;
        }

        Tokens t = new Tokens(normalised.split(" "));

        // Order matters. Dates claim their numbers first, then limits, then amounts — so by
        // the time the amount rule runs, every number it can still see is genuinely an amount.
        readDates(t, f, today);
        readSortAndLimit(t, f);
        readAmounts(t, f);
        readCategories(t, f);
        readMerchantTerms(t, f);

        return f;
    }

    // -------------------------------------------------------------- Dates

    private void readDates(Tokens t, SearchFilter f, LocalDate today) {
        for (int i = 0; i < t.words.length; i++) {
            if (!t.free(i)) {
                continue;
            }
            String w = t.at(i);
            String next = t.at(i + 1);
            String third = t.at(i + 2);

            if (w.equals("today")) {
                setRange(f, today, today);
                t.consume(i, i);
                return;
            }
            if (w.equals("yesterday")) {
                setRange(f, today.minusDays(1), today.minusDays(1));
                t.consume(i, i);
                return;
            }

            if (w.equals("this") || w.equals("last") || w.equals("past") || w.equals("previous")) {
                boolean current = w.equals("this");

                if (next.equals("week")) {
                    LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    if (current) {
                        setRange(f, monday, today);
                    } else {
                        setRange(f, monday.minusWeeks(1), monday.minusDays(1));
                    }
                    t.consume(i, i + 1);
                    return;
                }
                if (next.equals("month")) {
                    if (current) {
                        setRange(f, today.withDayOfMonth(1), today);
                    } else {
                        // The previous *calendar* month. The old parser returned the 1st of
                        // the current month here, so "last month" quietly meant "this month".
                        LocalDate prev = today.minusMonths(1);
                        setRange(f, prev.withDayOfMonth(1),
                                prev.with(TemporalAdjusters.lastDayOfMonth()));
                    }
                    t.consume(i, i + 1);
                    return;
                }
                if (next.equals("year")) {
                    if (current) {
                        setRange(f, today.withDayOfYear(1), today);
                    } else {
                        LocalDate prev = today.minusYears(1);
                        setRange(f, prev.withDayOfYear(1),
                                prev.with(TemporalAdjusters.lastDayOfYear()));
                    }
                    t.consume(i, i + 1);
                    return;
                }

                // "last 7 days" / "past 3 months" — the number belongs to the date.
                if (isNumber(next)) {
                    int n = (int) Double.parseDouble(next);
                    if (third.startsWith("day")) {
                        setRange(f, today.minusDays(n - 1L), today);
                        t.consume(i, i + 2);
                        return;
                    }
                    if (third.startsWith("week")) {
                        // Inclusive, to match "last 7 days": two weeks is 14 days ending today.
                        setRange(f, today.minusWeeks(n).plusDays(1), today);
                        t.consume(i, i + 2);
                        return;
                    }
                    if (third.startsWith("month")) {
                        setRange(f, today.minusMonths(n), today);
                        t.consume(i, i + 2);
                        return;
                    }
                }
            }

            if (w.equals("since")) {
                int month = monthIndex(next);
                if (month >= 0) {
                    LocalDate start = monthStart(month, today, t, i + 2);
                    setRange(f, start, today);
                    t.consume(i, i + 1);
                    return;
                }
            }

            int month = monthIndex(w);
            if (month >= 0) {
                LocalDate start = monthStart(month, today, t, i + 1);
                setRange(f, start, start.with(TemporalAdjusters.lastDayOfMonth()));
                t.consume(i, i);
                return;
            }

            DayOfWeek dow = dayOfWeek(w);
            if (dow != null) {
                LocalDate d = today.with(TemporalAdjusters.previousOrSame(dow));
                setRange(f, d, d);
                t.consume(i, i);
                return;
            }
        }
    }

    /**
     * Resolve a month name to a concrete month start. An explicit year token consumes itself;
     * otherwise a month that has not happened yet this year means last year.
     */
    private LocalDate monthStart(int monthIndex, LocalDate today, Tokens t, int yearTokenIndex) {
        String maybeYear = t.at(yearTokenIndex);
        if (maybeYear.matches("\\d{4}")) {
            t.consume(yearTokenIndex, yearTokenIndex);
            return LocalDate.of(Integer.parseInt(maybeYear), monthIndex + 1, 1);
        }
        LocalDate candidate = LocalDate.of(today.getYear(), monthIndex + 1, 1);
        if (candidate.isAfter(today)) {
            candidate = candidate.minusYears(1);
        }
        return candidate;
    }

    private void setRange(SearchFilter f, LocalDate start, LocalDate end) {
        f.startDate = start.format(OUT);
        f.endDate = end.format(OUT);
    }

    private int monthIndex(String w) {
        for (int i = 0; i < MONTHS.length; i++) {
            if (MONTHS[i].equals(w) || MONTH_ABBREV[i].equals(w)) {
                return i;
            }
        }
        return -1;
    }

    private DayOfWeek dayOfWeek(String w) {
        switch (w) {
            case "monday": return DayOfWeek.MONDAY;
            case "tuesday": return DayOfWeek.TUESDAY;
            case "wednesday": return DayOfWeek.WEDNESDAY;
            case "thursday": return DayOfWeek.THURSDAY;
            case "friday": return DayOfWeek.FRIDAY;
            case "saturday": return DayOfWeek.SATURDAY;
            case "sunday": return DayOfWeek.SUNDAY;
            default: return null;
        }
    }

    // ----------------------------------------------------- Sort and limit

    private void readSortAndLimit(Tokens t, SearchFilter f) {
        for (int i = 0; i < t.words.length; i++) {
            if (!t.free(i)) {
                continue;
            }
            String w = t.at(i);
            String next = t.at(i + 1);

            // "top 5" — again, the number belongs to the limit, not to an amount.
            if ((w.equals("top") || w.equals("first")) && isNumber(next)) {
                f.limit = (int) Double.parseDouble(next);
                if (f.sort == SearchFilter.Sort.DATE_DESC) {
                    f.sort = SearchFilter.Sort.AMOUNT_DESC;
                }
                t.consume(i, i + 1);
                continue;
            }

            if (w.equals("most") && next.equals("expensive")) {
                f.sort = SearchFilter.Sort.AMOUNT_DESC;
                t.consume(i, i + 1);
                continue;
            }
            if (w.equals("least") && next.equals("expensive")) {
                f.sort = SearchFilter.Sort.AMOUNT_ASC;
                t.consume(i, i + 1);
                continue;
            }
            if (w.equals("biggest") || w.equals("largest") || w.equals("highest")
                    || w.equals("priciest")) {
                f.sort = SearchFilter.Sort.AMOUNT_DESC;
                t.consume(i, i);
                continue;
            }
            if (w.equals("cheapest") || w.equals("smallest") || w.equals("lowest")) {
                f.sort = SearchFilter.Sort.AMOUNT_ASC;
                t.consume(i, i);
            }
        }
    }

    // ------------------------------------------------------------ Amounts

    private void readAmounts(Tokens t, SearchFilter f) {
        for (int i = 0; i < t.words.length; i++) {
            if (!t.free(i)) {
                continue;
            }
            String w = t.at(i);

            // "between 10 and 50"
            if (w.equals("between")) {
                Integer first = nextFreeNumberIndex(t, i + 1);
                if (first != null) {
                    Integer second = nextFreeNumberIndex(t, first + 1);
                    if (second != null) {
                        f.minAmount = amountAt(t, first);
                        f.maxAmount = amountAt(t, second);
                        t.consume(i, i);
                        t.consume(first, first);
                        t.consume(second, second);
                        // the "and" between them
                        for (int k = first + 1; k < second; k++) {
                            if (t.at(k).equals("and")) {
                                t.consume(k, k);
                            }
                        }
                        continue;
                    }
                }
            }

            int operatorEnd = i;
            Boolean isMin = null;

            if (w.equals("over") || w.equals("above") || w.equals(">") || w.equals("exceeding")) {
                isMin = true;
            } else if (w.equals("under") || w.equals("below") || w.equals("<")) {
                isMin = false;
            } else if (w.equals("more") && t.at(i + 1).equals("than")) {
                isMin = true;
                operatorEnd = i + 1;
            } else if (w.equals("less") && t.at(i + 1).equals("than")) {
                isMin = false;
                operatorEnd = i + 1;
            } else if (w.equals("around") || w.equals("about") || w.equals("approximately")
                    || w.equals("roughly")) {
                Integer at = nextFreeNumberIndex(t, i + 1);
                if (at != null) {
                    double v = amountAt(t, at);
                    f.minAmount = round2(v * 0.9);
                    f.maxAmount = round2(v * 1.1);
                    t.consume(i, i);
                    t.consume(at, at);
                }
                continue;
            }

            if (isMin != null) {
                Integer at = nextFreeNumberIndex(t, operatorEnd + 1);
                if (at != null) {
                    if (isMin) {
                        f.minAmount = amountAt(t, at);
                    } else {
                        f.maxAmount = amountAt(t, at);
                    }
                    t.consume(i, operatorEnd);
                    t.consume(at, at);
                }
            }
        }

        // A bare "$40" with no comparator reads as "about that much".
        for (int i = 0; i < t.words.length; i++) {
            if (t.free(i) && t.at(i).startsWith("$") && isNumber(stripCurrency(t.at(i)))) {
                double v = Double.parseDouble(stripCurrency(t.at(i)));
                if (f.minAmount == null && f.maxAmount == null) {
                    f.minAmount = round2(v * 0.9);
                    f.maxAmount = round2(v * 1.1);
                }
                t.consume(i, i);
            }
        }
    }

    private Integer nextFreeNumberIndex(Tokens t, int from) {
        for (int i = from; i < t.words.length; i++) {
            if (!t.free(i)) {
                continue;
            }
            if (isNumber(stripCurrency(t.at(i)))) {
                return i;
            }
            // only look past filler words
            if (!STOP_WORDS.contains(t.at(i)) && !t.at(i).equals("than")) {
                return null;
            }
        }
        return null;
    }

    private double amountAt(Tokens t, int index) {
        return Double.parseDouble(stripCurrency(t.at(index)));
    }

    private String stripCurrency(String w) {
        return w.replace("$", "").replace(",", "");
    }

    private boolean isNumber(String w) {
        return w.matches("\\d+(\\.\\d+)?");
    }

    private double round2(double v) {
        return Math.round(v * 100d) / 100d;
    }

    // --------------------------------------------------------- Categories

    private void readCategories(Tokens t, SearchFilter f) {
        Map<String, ExpenseCategory> words = SeedCorpus.categoryWords();
        for (int i = 0; i < t.words.length; i++) {
            if (!t.free(i)) {
                continue;
            }
            ExpenseCategory c = words.get(t.at(i));
            if (c != null) {
                f.categories.add(c);
                t.consume(i, i);
            }
        }
    }

    // ----------------------------------------------------- Merchant terms

    private void readMerchantTerms(Tokens t, SearchFilter f) {
        for (int i = 0; i < t.words.length; i++) {
            if (!t.free(i)) {
                continue;
            }
            String w = t.at(i);
            if (STOP_WORDS.contains(w) || w.length() < 2) {
                continue;
            }
            if (isNumber(stripCurrency(w))) {
                continue;   // an unclaimed number is noise, not a merchant name
            }
            if (!f.merchantTerms.contains(w)) {
                f.merchantTerms.add(w);
            }
        }
    }

    /** Exposed for tests and for the chip row's "we understood this much" display. */
    public List<String> stopWords() {
        return Arrays.asList(STOP_WORDS.toArray(new String[0]));
    }
}
