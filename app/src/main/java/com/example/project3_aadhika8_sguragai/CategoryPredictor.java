package com.example.project3_aadhika8_sguragai;

import android.content.Context;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Predicts expense categories based on merchant names and learns from user corrections.
 * Prioritizes user-defined mappings over built-in keyword patterns.
 */
public class CategoryPredictor {

    private final MerchantCategoryDao merchantCategoryDao;
    private final Map<String, ExpenseCategory> builtInMappings;

    public CategoryPredictor(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.merchantCategoryDao = db.merchantCategoryDao();
        this.builtInMappings = initBuiltInMappings();
    }

    /**
     * Predicts category for a given merchant/title.
     * @param merchantOrTitle The merchant name or expense title
     * @return Predicted category, or null if no prediction could be made
     */
    public ExpenseCategory predict(String merchantOrTitle) {
        if (merchantOrTitle == null || merchantOrTitle.isEmpty()) {
            return null;
        }

        String normalized = normalizeText(merchantOrTitle);

        // First, check user-defined mappings (highest priority)
        MerchantCategory userMapping = merchantCategoryDao.getByPattern(normalized);
        if (userMapping != null && userMapping.userDefined) {
            return userMapping.category;
        }

        // Check partial matches in user mappings
        List<MerchantCategory> allUserMappings = merchantCategoryDao.getUserDefinedMappings();
        for (MerchantCategory mapping : allUserMappings) {
            if (normalized.contains(mapping.merchantPattern.toLowerCase()) ||
                mapping.merchantPattern.toLowerCase().contains(normalized)) {
                return mapping.category;
            }
        }

        // Check built-in keyword mappings
        for (Map.Entry<String, ExpenseCategory> entry : builtInMappings.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Check general category keywords
        return predictFromKeywords(normalized);
    }

    /**
     * Records a user's category choice to learn from.
     * @param merchantOrTitle The merchant name or expense title
     * @param category The category the user chose
     */
    public void learn(String merchantOrTitle, ExpenseCategory category) {
        if (merchantOrTitle == null || merchantOrTitle.isEmpty() || category == null) {
            return;
        }

        String normalized = normalizeText(merchantOrTitle);
        
        MerchantCategory mapping = new MerchantCategory(normalized, category, true);
        merchantCategoryDao.insert(mapping);
    }

    /**
     * Checks if a prediction would differ from what was previously predicted.
     * Used to determine if we should ask the user to remember a new mapping.
     */
    public boolean wouldChangePrediction(String merchantOrTitle, ExpenseCategory newCategory) {
        ExpenseCategory currentPrediction = predict(merchantOrTitle);
        return currentPrediction == null || currentPrediction != newCategory;
    }

    private String normalizeText(String text) {
        return text.toLowerCase(Locale.getDefault())
                .replaceAll("[^a-z0-9\\s]", "")
                .trim();
    }

    private Map<String, ExpenseCategory> initBuiltInMappings() {
        Map<String, ExpenseCategory> mappings = new HashMap<>();

        // Food & Dining
        String[] foodKeywords = {"starbucks", "mcdonald", "burger king", "wendys", "subway",
                "chipotle", "taco bell", "pizza hut", "dominos", "dunkin", "panera",
                "chick-fil-a", "popeyes", "kfc", "arbys", "sonic", "five guys",
                "ihop", "denny", "applebees", "olive garden", "red lobster",
                "cheesecake factory", "buffalo wild wings", "outback"};
        for (String k : foodKeywords) mappings.put(k, ExpenseCategory.FOOD);

        // Transport
        String[] transportKeywords = {"shell", "exxon", "chevron", "mobil", "bp", "texaco",
                "speedway", "circle k", "wawa", "uber", "lyft", "taxi", "parking",
                "metro", "transit", "amtrak", "greyhound"};
        for (String k : transportKeywords) mappings.put(k, ExpenseCategory.TRANSPORT);

        // Groceries
        String[] groceryKeywords = {"walmart", "target", "costco", "kroger", "safeway",
                "whole foods", "trader joe", "aldi", "publix", "heb", "wegmans",
                "food lion", "giant", "stop shop", "piggly wiggly", "sprouts"};
        for (String k : groceryKeywords) mappings.put(k, ExpenseCategory.GROCERIES);

        // Entertainment
        String[] entertainmentKeywords = {"netflix", "spotify", "hulu", "disney", "hbo",
                "amc", "regal", "cinemark", "playstation", "xbox", "nintendo",
                "steam", "ticketmaster", "stubhub", "live nation"};
        for (String k : entertainmentKeywords) mappings.put(k, ExpenseCategory.ENTERTAINMENT);

        // Shopping
        String[] shoppingKeywords = {"amazon", "ebay", "best buy", "apple store", "nike",
                "adidas", "nordstrom", "macys", "jcpenney", "kohls", "ross",
                "tj maxx", "marshalls", "home depot", "lowes", "ikea", "wayfair"};
        for (String k : shoppingKeywords) mappings.put(k, ExpenseCategory.SHOPPING);

        // Bills
        String[] billsKeywords = {"verizon", "att", "tmobile", "sprint", "comcast",
                "xfinity", "spectrum", "cox", "electric", "water", "gas company",
                "insurance", "geico", "progressive", "state farm", "allstate"};
        for (String k : billsKeywords) mappings.put(k, ExpenseCategory.BILLS);

        return mappings;
    }

    private ExpenseCategory predictFromKeywords(String text) {
        // General keyword matching
        if (containsAny(text, "coffee", "cafe", "restaurant", "diner", "food", 
                "pizza", "burger", "sushi", "thai", "chinese", "mexican", "italian",
                "lunch", "dinner", "breakfast", "eat")) {
            return ExpenseCategory.FOOD;
        }

        if (containsAny(text, "gas", "fuel", "uber", "lyft", "taxi", "parking",
                "transit", "bus", "train", "flight", "airline")) {
            return ExpenseCategory.TRANSPORT;
        }

        if (containsAny(text, "grocery", "market", "supermarket", "produce", "meat")) {
            return ExpenseCategory.GROCERIES;
        }

        if (containsAny(text, "movie", "theater", "concert", "game", "streaming",
                "entertainment", "ticket", "show")) {
            return ExpenseCategory.ENTERTAINMENT;
        }

        if (containsAny(text, "shop", "store", "mall", "clothing", "clothes",
                "shoes", "electronics", "furniture")) {
            return ExpenseCategory.SHOPPING;
        }

        if (containsAny(text, "bill", "electric", "water", "phone", "internet",
                "insurance", "rent", "mortgage", "utility")) {
            return ExpenseCategory.BILLS;
        }

        return null;
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
