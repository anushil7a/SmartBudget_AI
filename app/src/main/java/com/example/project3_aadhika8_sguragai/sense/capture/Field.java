package com.example.project3_aadhika8_sguragai.sense.capture;

import com.example.project3_aadhika8_sguragai.sense.Sense;

/**
 * One extracted value plus everything the UI needs to justify it.
 *
 * <p>The confidence and the source line are the point: the scan screen shows the parse
 * instead of asserting a number, and {@link #boundingBox} is what the reveal animation
 * highlights.
 */
public class Field<T> {

    /** Null if not found. */
    public final T value;

    /** 0.0 – 1.0 */
    public final float confidence;

    /** The OCR line it came from, or null. */
    public final String sourceLine;

    /** Where that line sat on the receipt, for the overlay. */
    public final Box boundingBox;

    public Field(T value, float confidence, String sourceLine, Box boundingBox) {
        this.value = value;
        this.confidence = confidence;
        this.sourceLine = sourceLine;
        this.boundingBox = boundingBox;
    }

    public static <T> Field<T> missing() {
        return new Field<>(null, 0f, null, null);
    }

    public boolean isPresent() {
        return value != null;
    }

    /** Below the shared threshold the UI asks rather than asserts. */
    public boolean needsReview() {
        return value == null || confidence < Sense.REVIEW_THRESHOLD;
    }

    @Override
    public String toString() {
        return "Field{" + value + " @" + confidence + "}";
    }
}
