package com.example.project3_aadhika8_sguragai.sense.capture;

/** Everything the parser could work out from one receipt. */
public class ReceiptExtraction {

    public Field<String> merchant = Field.missing();

    public Field<Double> total = Field.missing();

    /** yyyy-MM-dd */
    public Field<String> date = Field.missing();

    public String rawText = "";

    /** True when any field came out below the review threshold. */
    public boolean needsReview() {
        return merchant.needsReview() || total.needsReview() || date.needsReview();
    }
}
