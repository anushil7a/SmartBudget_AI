package com.example.project3_aadhika8_sguragai.sense.classify;

import com.example.project3_aadhika8_sguragai.data.ExpenseCategory;
import com.example.project3_aadhika8_sguragai.sense.Sense;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A category guess and the reason for it.
 *
 * <p>{@link #evidenceTokens} is what lets the UI say "FOOD, 94% — starbucks, latte" rather
 * than asserting an unexplained result.
 */
public class Prediction {

    public final ExpenseCategory category;

    /** Softmax-normalised posterior of the winning category. */
    public final float confidence;

    /** Top 3 tokens by log-ratio against the runner-up. */
    public final List<String> evidenceTokens;

    public Prediction(ExpenseCategory category, float confidence, List<String> evidenceTokens) {
        this.category = category;
        this.confidence = confidence;
        this.evidenceTokens = evidenceTokens == null
                ? Collections.emptyList()
                : new ArrayList<>(evidenceTokens);
    }

    /** Below the shared threshold the UI asks rather than asserts. */
    public boolean needsReview() {
        return confidence < Sense.REVIEW_THRESHOLD;
    }

    public static Prediction unknown() {
        return new Prediction(ExpenseCategory.OTHER, 0f, Collections.emptyList());
    }

    @Override
    public String toString() {
        return category + " @ " + Math.round(confidence * 100) + "% " + evidenceTokens;
    }
}
