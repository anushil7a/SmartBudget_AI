package com.example.project3_aadhika8_sguragai.data;

/**
 * Where an expense's category came from.
 *
 * <p>PREDICTED — the classifier chose it; {@code predictionConfidence} is set.
 * USER — the person chose it by hand.
 * OVERRIDE — an explicit merchant pin supplied it (see {@link MerchantCategory}).
 */
public enum CategorySource {
    PREDICTED,
    USER,
    OVERRIDE
}
