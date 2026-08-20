package com.example.project3_aadhika8_sguragai.sense.classify;

import com.example.project3_aadhika8_sguragai.data.ExpenseCategory;
import com.example.project3_aadhika8_sguragai.data.MerchantCategory;
import com.example.project3_aadhika8_sguragai.data.MerchantCategoryDao;

import java.util.List;
import java.util.Locale;

/**
 * Explicit "always file this merchant here" pins, which short-circuit the model.
 *
 * <p>Note the changed meaning of {@link MerchantCategory}: it used to be written implicitly
 * whenever a category was corrected, which made it a second, competing memory alongside the
 * classifier. It is now written only when the user explicitly chooses "always categorise this
 * merchant as…". Explicit beats learned; everything else is the classifier's business.
 *
 * <p>Must be called off the main thread — it reads the database.
 */
public class MerchantPins implements NaiveBayesClassifier.PinLookup {

    private final MerchantCategoryDao dao;

    public MerchantPins(MerchantCategoryDao dao) {
        this.dao = dao;
    }

    @Override
    public ExpenseCategory pinnedFor(String merchant) {
        if (merchant == null || merchant.trim().isEmpty()) {
            return null;
        }
        String needle = merchant.toLowerCase(Locale.US).trim();

        MerchantCategory exact = dao.getByPattern(needle);
        if (exact != null && exact.userDefined) {
            return exact.category;
        }

        // A pin is a merchant name, and OCR rarely hands back exactly what was pinned
        // ("TRADER JOE'S #182"), so a contained pin counts as a match.
        List<MerchantCategory> pins = dao.getUserDefinedMappings();
        for (MerchantCategory pin : pins) {
            String pattern = pin.merchantPattern.toLowerCase(Locale.US).trim();
            if (!pattern.isEmpty() && needle.contains(pattern)) {
                return pin.category;
            }
        }
        return null;
    }

    /** Write a pin. Only ever call this from an explicit user choice. */
    public void pin(String merchant, ExpenseCategory category) {
        if (merchant == null || merchant.trim().isEmpty()) {
            return;
        }
        dao.insert(new MerchantCategory(
                merchant.toLowerCase(Locale.US).trim(), category, true));
    }

    public void unpin(String merchant) {
        if (merchant != null) {
            dao.delete(merchant.toLowerCase(Locale.US).trim());
        }
    }
}
