package com.example.project3_aadhika8_sguragai;

import com.example.project3_aadhika8_sguragai.data.*;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses natural language search queries into structured filters.
 * Examples:
 * - "food last week" -> category=FOOD, dateRange=last7days
 * - "over $50" -> minAmount=50
 * - "groceries in November" -> category=GROCERIES, dateRange=november
 * - "spending under $20" -> maxAmount=20
 */
public class NaturalLanguageParser {

    public static class SearchQuery {
        public ExpenseCategory category;
        public String startDate;
        public String endDate;
        public Double minAmount;
        public Double maxAmount;
        public String keyword;

        public boolean isEmpty() {
            return category == null && startDate == null && endDate == null
                    && minAmount == null && maxAmount == null
                    && (keyword == null || keyword.isEmpty());
        }
    }

    /**
     * Parses a natural language query into a SearchQuery object.
     */
    public SearchQuery parse(String input) {
        SearchQuery query = new SearchQuery();
        
        if (input == null || input.isEmpty()) {
            return query;
        }

        String text = input.toLowerCase(Locale.getDefault()).trim();

        // Extract category
        query.category = extractCategory(text);

        // Extract date range
        extractDateRange(text, query);

        // Extract amount filters
        extractAmountFilters(text, query);

        // Remaining text becomes keyword search
        query.keyword = extractRemainingKeyword(text);

        return query;
    }

    private ExpenseCategory extractCategory(String text) {
        // Check for category keywords
        if (containsAny(text, "food", "restaurant", "eat", "dining", "lunch", "dinner", "breakfast", "coffee")) {
            return ExpenseCategory.FOOD;
        }
        if (containsAny(text, "transport", "gas", "uber", "lyft", "taxi", "parking", "fuel", "car")) {
            return ExpenseCategory.TRANSPORT;
        }
        if (containsAny(text, "entertainment", "movie", "netflix", "spotify", "concert", "show", "game")) {
            return ExpenseCategory.ENTERTAINMENT;
        }
        if (containsAny(text, "groceries", "grocery", "supermarket", "market", "produce")) {
            return ExpenseCategory.GROCERIES;
        }
        if (containsAny(text, "bills", "bill", "electric", "water", "internet", "phone", "rent", "utility")) {
            return ExpenseCategory.BILLS;
        }
        if (containsAny(text, "shopping", "clothes", "amazon", "store", "mall", "online")) {
            return ExpenseCategory.SHOPPING;
        }
        if (text.contains("other")) {
            return ExpenseCategory.OTHER;
        }
        return null;
    }

    private void extractDateRange(String text, SearchQuery query) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar cal = Calendar.getInstance();

        // "today"
        if (text.contains("today")) {
            query.startDate = sdf.format(cal.getTime());
            query.endDate = sdf.format(cal.getTime());
            return;
        }

        // "yesterday"
        if (text.contains("yesterday")) {
            cal.add(Calendar.DAY_OF_MONTH, -1);
            query.startDate = sdf.format(cal.getTime());
            query.endDate = sdf.format(cal.getTime());
            return;
        }

        // "last week" or "this week" or "past week"
        if (containsAny(text, "last week", "this week", "past week")) {
            query.endDate = sdf.format(cal.getTime());
            cal.add(Calendar.DAY_OF_MONTH, -6);
            query.startDate = sdf.format(cal.getTime());
            return;
        }

        // "last month" or "this month" or "past month"
        if (containsAny(text, "last month", "this month", "past month")) {
            query.endDate = sdf.format(cal.getTime());
            cal.set(Calendar.DAY_OF_MONTH, 1);
            query.startDate = sdf.format(cal.getTime());
            return;
        }

        // "last X days"
        Pattern daysPattern = Pattern.compile("(?:last|past)\\s+(\\d+)\\s+days?");
        Matcher daysMatcher = daysPattern.matcher(text);
        if (daysMatcher.find()) {
            int days = Integer.parseInt(daysMatcher.group(1));
            query.endDate = sdf.format(cal.getTime());
            cal.add(Calendar.DAY_OF_MONTH, -(days - 1));
            query.startDate = sdf.format(cal.getTime());
            return;
        }

        // Specific month names
        String[] months = {"january", "february", "march", "april", "may", "june",
                "july", "august", "september", "october", "november", "december"};
        for (int i = 0; i < months.length; i++) {
            if (text.contains(months[i]) || text.contains(months[i].substring(0, 3))) {
                cal = Calendar.getInstance();
                cal.set(Calendar.MONTH, i);
                cal.set(Calendar.DAY_OF_MONTH, 1);
                query.startDate = sdf.format(cal.getTime());
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
                query.endDate = sdf.format(cal.getTime());
                return;
            }
        }
    }

    private void extractAmountFilters(String text, SearchQuery query) {
        // "over $X" or "more than $X" or "above $X" or "> $X"
        Pattern overPattern = Pattern.compile("(?:over|more than|above|greater than|>)\\s*\\$?(\\d+(?:\\.\\d{2})?)");
        Matcher overMatcher = overPattern.matcher(text);
        if (overMatcher.find()) {
            query.minAmount = Double.parseDouble(overMatcher.group(1));
        }

        // "under $X" or "less than $X" or "below $X" or "< $X"
        Pattern underPattern = Pattern.compile("(?:under|less than|below|<)\\s*\\$?(\\d+(?:\\.\\d{2})?)");
        Matcher underMatcher = underPattern.matcher(text);
        if (underMatcher.find()) {
            query.maxAmount = Double.parseDouble(underMatcher.group(1));
        }

        // "between $X and $Y"
        Pattern betweenPattern = Pattern.compile("between\\s*\\$?(\\d+(?:\\.\\d{2})?)\\s*(?:and|-)\\s*\\$?(\\d+(?:\\.\\d{2})?)");
        Matcher betweenMatcher = betweenPattern.matcher(text);
        if (betweenMatcher.find()) {
            query.minAmount = Double.parseDouble(betweenMatcher.group(1));
            query.maxAmount = Double.parseDouble(betweenMatcher.group(2));
        }

        // "exactly $X" or just "$X" without comparison
        if (query.minAmount == null && query.maxAmount == null) {
            Pattern exactPattern = Pattern.compile("(?:exactly\\s+)?\\$?(\\d+(?:\\.\\d{2})?)(?!\\s*(?:and|days?|week|month))");
            Matcher exactMatcher = exactPattern.matcher(text);
            if (exactMatcher.find() && !text.contains("over") && !text.contains("under")) {
                // For simplicity, treat exact amount as a range ±5%
                double amount = Double.parseDouble(exactMatcher.group(1));
                query.minAmount = amount * 0.95;
                query.maxAmount = amount * 1.05;
            }
        }
    }

    private String extractRemainingKeyword(String text) {
        // Remove common words and filter terms to get the keyword
        String keyword = text
                .replaceAll("(?i)(last|this|past|week|month|year|today|yesterday)", "")
                .replaceAll("(?i)(over|under|more|less|than|above|below|between|and|exactly)", "")
                .replaceAll("(?i)(food|transport|entertainment|groceries|bills|shopping|other)", "")
                .replaceAll("(?i)(january|february|march|april|may|june|july|august|september|october|november|december)", "")
                .replaceAll("(?i)(jan|feb|mar|apr|jun|jul|aug|sep|oct|nov|dec)", "")
                .replaceAll("\\$\\d+(?:\\.\\d{2})?", "")
                .replaceAll("\\d+\\s*days?", "")
                .replaceAll("[^a-zA-Z\\s]", "")
                .trim();

        // Return null if mostly empty
        if (keyword.length() < 2) {
            return null;
        }

        return keyword;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
