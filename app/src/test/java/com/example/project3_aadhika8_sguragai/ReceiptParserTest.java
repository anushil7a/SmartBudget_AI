package com.example.project3_aadhika8_sguragai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.project3_aadhika8_sguragai.sense.Sense;
import com.example.project3_aadhika8_sguragai.sense.capture.Box;
import com.example.project3_aadhika8_sguragai.sense.capture.ReceiptExtraction;
import com.example.project3_aadhika8_sguragai.sense.capture.ReceiptParser;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The parser's job is to find the total a human would point at. The old implementation
 * returned the largest number on the page, which is wrong on any receipt that prints cash
 * tendered, a rewards balance, or a change line — several fixtures here are exactly that.
 *
 * <p>Fixture format: one OCR line per row. An optional {@code [h=NN]} prefix sets that line's
 * glyph height, since real receipts print the store name larger and the merchant rule reads
 * that geometry. Default height is 20.
 */
public class ReceiptParserTest {

    /** Fixed so "reject future dates" and "reject anything older than 24 months" are stable. */
    private static final LocalDate TODAY = LocalDate.of(2026, 1, 15);

    private ReceiptExtraction parseFixture(String name) throws Exception {
        List<ReceiptParser.ParsedLine> lines = new ArrayList<>();
        int y = 0;
        InputStream in = getClass().getResourceAsStream("/receipts/" + name);
        assertNotNull("fixture missing: " + name, in);
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String raw;
            while ((raw = r.readLine()) != null) {
                int height = 20;
                String text = raw;
                if (text.startsWith("[h=")) {
                    int close = text.indexOf(']');
                    height = Integer.parseInt(text.substring(3, close));
                    text = text.substring(close + 1);
                }
                lines.add(new ReceiptParser.ParsedLine(text, new Box(0, y, 400, y + height)));
                y += height + 4;
            }
        }
        return new ReceiptParser().parseText(lines, TODAY);
    }

    // ---- Total ----

    @Test
    public void picksTotalNotLargestNumber_whenCashTenderedIsBigger() throws Exception {
        ReceiptExtraction r = parseFixture("cash_tendered_larger.txt");
        assertEquals(23.41, r.total.value, 0.001);
        assertEquals(0.95f, r.total.confidence, 0.001f);
    }

    @Test
    public void picksTotalNotLargestNumber_whenRewardsBalanceIsBigger() throws Exception {
        ReceiptExtraction r = parseFixture("loyalty_rewards_larger.txt");
        assertEquals(8.75, r.total.value, 0.001);
    }

    @Test
    public void derivesTotalFromSubtotalPlusTax_whenNoTotalKeyword() throws Exception {
        ReceiptExtraction r = parseFixture("subtotal_only.txt");
        assertEquals(10.85, r.total.value, 0.001);
        assertEquals(0.55f, r.total.confidence, 0.001f);
    }

    @Test
    public void prefersLastTotalOccurrence() throws Exception {
        ReceiptExtraction r = parseFixture("duplicate_total.txt");
        assertEquals(41.20, r.total.value, 0.001);
    }

    @Test
    public void grandTotalOutranksTotal() throws Exception {
        ReceiptExtraction r = parseFixture("grand_total_wins.txt");
        assertEquals(20.00, r.total.value, 0.001);
    }

    @Test
    public void amountDueIsAWeakerAnchorThanTotal() throws Exception {
        ReceiptExtraction r = parseFixture("amount_due.txt");
        assertEquals(64.30, r.total.value, 0.001);
        assertEquals(0.75f, r.total.confidence, 0.001f);
    }

    @Test
    public void tipLineIsNotMistakenForTheTotal() throws Exception {
        ReceiptExtraction r = parseFixture("restaurant_tip.txt");
        assertEquals(50.00, r.total.value, 0.001);
    }

    @Test
    public void noTotalAnywhere_fallsBackToLargestAtLowConfidence() throws Exception {
        ReceiptExtraction r = parseFixture("no_total_keyword.txt");
        assertEquals(4.75, r.total.value, 0.001);
        assertEquals(0.30f, r.total.confidence, 0.001f);
        assertTrue("a guessed total must be flagged", r.total.needsReview());
    }

    // ---- Merchant ----

    @Test
    public void merchantIsStoreName_notAddressOrPhone() throws Exception {
        ReceiptExtraction r = parseFixture("cash_tendered_larger.txt");
        assertEquals("Trader Joe's", r.merchant.value);
        assertEquals(0.9f, r.merchant.confidence, 0.001f);
    }

    @Test
    public void merchantSkipsStreetAndZipLines() throws Exception {
        ReceiptExtraction r = parseFixture("walmart_plain.txt");
        assertEquals("Walmart Supercenter", r.merchant.value);
    }

    @Test
    public void merchantIsAboveReviewThreshold_whenItIsThePageMax() throws Exception {
        ReceiptExtraction r = parseFixture("restaurant_tip.txt");
        assertEquals("Olive Garden", r.merchant.value);
        assertTrue(r.merchant.confidence >= Sense.REVIEW_THRESHOLD);
    }

    // ---- Date ----

    @Test
    public void readsDateFromReceipt() throws Exception {
        ReceiptExtraction r = parseFixture("cash_tendered_larger.txt");
        assertEquals("2025-11-25", r.date.value);
        assertTrue(r.date.confidence >= Sense.REVIEW_THRESHOLD);
    }

    @Test
    public void rejectsFutureDate_andFallsBackFlaggedForReview() throws Exception {
        ReceiptExtraction r = parseFixture("future_date.txt");
        assertEquals("a future date must not be trusted", "2026-01-15", r.date.value);
        assertEquals(0.2f, r.date.confidence, 0.001f);
        assertTrue(r.date.needsReview());
    }

    @Test
    public void missingDateFallsBackToToday_flaggedForReview() throws Exception {
        ReceiptExtraction r = parseFixture("no_total_keyword.txt");
        assertEquals("2026-01-15", r.date.value);
        assertTrue(r.date.needsReview());
    }

    // ---- Whole extraction ----

    @Test
    public void confidentReceiptDoesNotNeedReview() throws Exception {
        ReceiptExtraction r = parseFixture("walmart_plain.txt");
        assertTrue("clean receipt should parse cleanly", !r.needsReview());
    }

    @Test
    public void rawTextIsPreserved() throws Exception {
        ReceiptExtraction r = parseFixture("walmart_plain.txt");
        assertTrue(r.rawText.contains("TOTAL 26.62"));
    }
}
