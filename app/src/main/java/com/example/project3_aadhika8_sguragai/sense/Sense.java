package com.example.project3_aadhika8_sguragai.sense;

/** Constants shared across the sense engines. */
public final class Sense {

    /**
     * Anything below this is rendered in the review state — amber ring, focused for edit.
     *
     * <p>One constant, deliberately shared by capture and classify, so "needs review" means
     * the same thing whether it came from OCR or from the classifier.
     */
    public static final float REVIEW_THRESHOLD = 0.6f;

    private Sense() {
    }
}
