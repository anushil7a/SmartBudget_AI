package com.example.project3_aadhika8_sguragai.sense.classify;

import com.example.project3_aadhika8_sguragai.data.ExpenseCategory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The category keyword table — one copy.
 *
 * <p>The previous code kept three independent copies of this list: in the OCR class, in the
 * dead classifier, and in the query parser. They had drifted apart, so a merchant could be
 * classified one way when scanned and matched a different way when searched. This is the
 * single source, used by both {@link NaiveBayesClassifier} seeding and the search parser.
 *
 * <p>Entries are the union of the three originals, deduplicated.
 */
public final class SeedCorpus {

    /** Each keyword is seeded as a pseudo-document with this weight. */
    public static final int SEED_WEIGHT = 5;

    private SeedCorpus() {
    }

    private static final String[] FOOD = {
            "starbucks", "mcdonald", "burger king", "wendys", "subway", "chipotle",
            "taco bell", "pizza hut", "dominos", "dunkin", "panera", "chick-fil-a",
            "popeyes", "kfc", "arbys", "sonic", "five guys", "ihop", "denny",
            "applebees", "olive garden", "red lobster", "cheesecake factory",
            "buffalo wild wings", "outback", "coffee", "cafe", "restaurant", "diner",
            "food", "pizza", "burger", "sushi", "thai", "chinese", "mexican", "italian",
            "lunch", "dinner", "breakfast", "eat", "taco"
    };

    private static final String[] TRANSPORT = {
            "shell", "exxon", "chevron", "mobil", "bp", "texaco", "speedway", "circle k",
            "wawa", "uber", "lyft", "taxi", "parking", "metro", "transit", "amtrak",
            "greyhound", "gas", "fuel", "bus", "train", "flight", "airline"
    };

    private static final String[] GROCERIES = {
            "walmart", "target", "costco", "kroger", "safeway", "whole foods",
            "trader joe", "aldi", "publix", "heb", "wegmans", "food lion", "giant",
            "stop shop", "piggly wiggly", "sprouts", "grocery", "market", "supermarket",
            "produce", "meat"
    };

    private static final String[] ENTERTAINMENT = {
            "netflix", "spotify", "hulu", "disney", "hbo", "amc", "regal", "cinemark",
            "playstation", "xbox", "nintendo", "steam", "ticketmaster", "stubhub",
            "live nation", "movie", "theater", "concert", "game", "streaming",
            "entertainment", "ticket", "show", "cinema", "amusement"
    };

    private static final String[] SHOPPING = {
            "amazon", "ebay", "best buy", "apple store", "nike", "adidas", "nordstrom",
            "macys", "jcpenney", "kohls", "ross", "tj maxx", "marshalls", "home depot",
            "lowes", "ikea", "wayfair", "shop", "store", "mall", "clothing", "clothes",
            "shoes", "electronics", "furniture", "apparel", "fashion"
    };

    private static final String[] BILLS = {
            "verizon", "att", "tmobile", "sprint", "comcast", "xfinity", "spectrum",
            "cox", "electric", "water", "gas company", "insurance", "geico",
            "progressive", "state farm", "allstate", "bill", "phone", "internet",
            "rent", "mortgage", "utility", "cable"
    };

    private static final Map<String, ExpenseCategory> KEYWORDS = build();

    private static Map<String, ExpenseCategory> build() {
        Map<String, ExpenseCategory> m = new LinkedHashMap<>();
        put(m, FOOD, ExpenseCategory.FOOD);
        put(m, TRANSPORT, ExpenseCategory.TRANSPORT);
        put(m, GROCERIES, ExpenseCategory.GROCERIES);
        put(m, ENTERTAINMENT, ExpenseCategory.ENTERTAINMENT);
        put(m, SHOPPING, ExpenseCategory.SHOPPING);
        put(m, BILLS, ExpenseCategory.BILLS);
        return Collections.unmodifiableMap(m);
    }

    private static void put(Map<String, ExpenseCategory> m, String[] words, ExpenseCategory c) {
        for (String w : words) {
            // First category to claim a keyword keeps it, so the union stays deterministic.
            m.putIfAbsent(w, c);
        }
    }

    /** Keyword to category. Used for seeding and for category detection in search queries. */
    public static Map<String, ExpenseCategory> keywords() {
        return KEYWORDS;
    }

    /** Give a fresh model a starting opinion. Runs once; see SeedCorpus usage in the repository. */
    public static void seed(NaiveBayesClassifier classifier) {
        for (Map.Entry<String, ExpenseCategory> e : KEYWORDS.entrySet()) {
            classifier.learnWeighted(e.getKey(), e.getValue(), SEED_WEIGHT);
        }
    }
}
