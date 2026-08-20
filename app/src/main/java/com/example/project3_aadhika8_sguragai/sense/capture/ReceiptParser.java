package com.example.project3_aadhika8_sguragai.sense.capture;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns OCR lines into a merchant, a total, and a date — each with a confidence.
 *
 * <p>The previous implementation returned the largest currency figure on the page. On a real
 * receipt that is frequently the cash tendered, a rewards balance, or the change line, so the
 * total is instead anchored to keywords and read bottom-up.
 *
 * <p>{@link #parseText} is pure and takes no Android types, which is what lets the parser be
 * tested against real receipt text on the JVM. {@code parse(Bitmap, …)} in
 * {@link ReceiptScanner} is the thin ML Kit shell around it.
 */
public class ReceiptParser {

    /** One OCR line and where it sat on the page. */
    public static class ParsedLine {
        public final String text;
        public final Box box;

        public ParsedLine(String text, Box box) {
            this.text = text == null ? "" : text;
            this.box = box;
        }
    }

    // ---- Total ----

    /** Anchor strength, strongest first. Ordinal is the priority. */
    private enum Anchor {
        GRAND_TOTAL(0.95f),
        TOTAL(0.95f),
        AMOUNT_DUE(0.75f),
        BALANCE_DUE(0.75f),
        SUBTOTAL(0.55f);

        final float confidence;

        Anchor(float confidence) {
            this.confidence = confidence;
        }
    }

    private static final float FALLBACK_TOTAL_CONFIDENCE = 0.30f;

    private static final Pattern GRAND_TOTAL = Pattern.compile("\\bgrand\\s+total\\b");
    private static final Pattern SUBTOTAL = Pattern.compile("\\bsub\\s*total\\b");
    private static final Pattern TOTAL = Pattern.compile("\\btotal\\b");
    private static final Pattern AMOUNT_DUE = Pattern.compile("\\bamount\\s+due\\b");
    private static final Pattern BALANCE_DUE = Pattern.compile("\\bbalance\\s+due\\b");
    private static final Pattern TAX = Pattern.compile("\\b(tax|vat|gst|hst)\\b");

    /**
     * A line mentioning any of these is describing how the bill was settled, not what it came
     * to. Rejecting them is what stops "CASH 50.00" beating "TOTAL 23.41".
     */
    private static final Pattern PAYMENT_NOISE = Pattern.compile(
            "\\b(change|cash|tender|tendered|card|visa|mastercard|debit|credit|tip|cash\\s*back)\\b");

    /** Currency amounts. Requires two decimal places so item counts and IDs do not qualify. */
    private static final Pattern AMOUNT = Pattern.compile("(?<![\\d.])\\$?\\s*(\\d{1,6}(?:[.,]\\d{2}))(?![\\d])");

    // ---- Merchant ----

    private static final Pattern ZIP = Pattern.compile("\\b\\d{5}(?:-\\d{4})?\\b");
    private static final Pattern PHONE = Pattern.compile(
            "\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4}");
    private static final Pattern STREET = Pattern.compile(
            "\\b(st|street|ave|avenue|rd|road|blvd|boulevard|dr|drive|ln|lane|suite|ste)\\b|#\\d");
    private static final Pattern NOT_A_NAME = Pattern.compile(
            "\\b(receipt|invoice|order|welcome|thank\\s+you)\\b");

    // ---- Date ----

    private static final DateTimeFormatter OUT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int MAX_AGE_MONTHS = 24;
    private static final float DATE_FALLBACK_CONFIDENCE = 0.2f;

    private static final Pattern DATE_NUMERIC = Pattern.compile(
            "\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2,4})\\b");
    private static final Pattern DATE_ISO = Pattern.compile(
            "\\b(\\d{4})[/-](\\d{1,2})[/-](\\d{1,2})\\b");
    private static final Pattern DATE_TEXTUAL = Pattern.compile(
            "\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?\\s+(\\d{1,2}),?\\s+(\\d{4})\\b");

    private static final String[] MONTHS =
            {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};

    // ---------------------------------------------------------------- API

    public ReceiptExtraction parseText(List<ParsedLine> lines) {
        return parseText(lines, LocalDate.now());
    }

    public ReceiptExtraction parseText(List<ParsedLine> lines, LocalDate today) {
        ReceiptExtraction out = new ReceiptExtraction();

        StringBuilder raw = new StringBuilder();
        for (ParsedLine l : lines) {
            raw.append(l.text).append('\n');
        }
        out.rawText = raw.toString();

        if (lines.isEmpty()) {
            out.date = new Field<>(today.format(OUT), DATE_FALLBACK_CONFIDENCE, null, null);
            return out;
        }

        out.total = extractTotal(lines);
        out.merchant = extractMerchant(lines);
        out.date = extractDate(lines, today);
        return out;
    }

    // ------------------------------------------------------------- Total

    private Field<Double> extractTotal(List<ParsedLine> lines) {
        Anchor best = null;
        int bestIndex = -1;
        Double bestValue = null;

        // Bottom-to-top: on a receipt the settlement block is at the foot, and when the same
        // anchor appears twice the later one is the real one.
        for (int i = lines.size() - 1; i >= 0; i--) {
            String lower = lines.get(i).text.toLowerCase(Locale.US);
            Anchor anchor = anchorOf(lower);
            if (anchor == null) {
                continue;
            }
            if (PAYMENT_NOISE.matcher(lower).find()) {
                continue;   // "TOTAL TENDERED", "CREDIT CARD TOTAL", …
            }
            Double amount = amountOnLineOrNext(lines, i);
            if (amount == null) {
                continue;
            }
            // Strictly better anchor wins; equal anchor keeps the lower (later) line, which
            // is the first one we meet walking upward.
            if (best == null || anchor.ordinal() < best.ordinal()) {
                best = anchor;
                bestIndex = i;
                bestValue = amount;
            }
        }

        if (best == Anchor.SUBTOTAL) {
            Double tax = findTax(lines);
            double sum = bestValue + (tax == null ? 0d : tax);
            return new Field<>(round2(sum), Anchor.SUBTOTAL.confidence,
                    lines.get(bestIndex).text, lines.get(bestIndex).box);
        }

        if (best != null) {
            return new Field<>(round2(bestValue), best.confidence,
                    lines.get(bestIndex).text, lines.get(bestIndex).box);
        }

        return largestAmountFallback(lines);
    }

    private Anchor anchorOf(String lower) {
        if (GRAND_TOTAL.matcher(lower).find()) return Anchor.GRAND_TOTAL;
        if (SUBTOTAL.matcher(lower).find()) return Anchor.SUBTOTAL;
        if (TOTAL.matcher(lower).find()) return Anchor.TOTAL;
        if (AMOUNT_DUE.matcher(lower).find()) return Anchor.AMOUNT_DUE;
        if (BALANCE_DUE.matcher(lower).find()) return Anchor.BALANCE_DUE;
        return null;
    }

    /** The amount on this line, or failing that the first amount on the next line. */
    private Double amountOnLineOrNext(List<ParsedLine> lines, int index) {
        Double here = lastAmountIn(lines.get(index).text);
        if (here != null) {
            return here;
        }
        if (index + 1 < lines.size()) {
            return firstAmountIn(lines.get(index + 1).text);
        }
        return null;
    }

    private Double findTax(List<ParsedLine> lines) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            String lower = lines.get(i).text.toLowerCase(Locale.US);
            if (TAX.matcher(lower).find() && !SUBTOTAL.matcher(lower).find()) {
                Double v = lastAmountIn(lines.get(i).text);
                if (v != null) {
                    return v;
                }
            }
        }
        return null;
    }

    /** The old behaviour, kept only as a last resort and flagged as such. */
    private Field<Double> largestAmountFallback(List<ParsedLine> lines) {
        double max = Double.NEGATIVE_INFINITY;
        ParsedLine from = null;
        for (ParsedLine l : lines) {
            Double v = lastAmountIn(l.text);
            if (v != null && v > max) {
                max = v;
                from = l;
            }
        }
        if (from == null) {
            return Field.missing();
        }
        return new Field<>(round2(max), FALLBACK_TOTAL_CONFIDENCE, from.text, from.box);
    }

    private Double firstAmountIn(String text) {
        Matcher m = AMOUNT.matcher(text);
        return m.find() ? toDouble(m.group(1)) : null;
    }

    private Double lastAmountIn(String text) {
        Matcher m = AMOUNT.matcher(text);
        String last = null;
        while (m.find()) {
            last = m.group(1);
        }
        return last == null ? null : toDouble(last);
    }

    private Double toDouble(String s) {
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private double round2(double v) {
        return Math.round(v * 100d) / 100d;
    }

    // ---------------------------------------------------------- Merchant

    private Field<String> extractMerchant(List<ParsedLine> lines) {
        int pageTop = Integer.MAX_VALUE;
        int pageBottom = Integer.MIN_VALUE;
        List<Integer> heights = new ArrayList<>();
        for (ParsedLine l : lines) {
            if (l.box == null) continue;
            pageTop = Math.min(pageTop, l.box.top);
            pageBottom = Math.max(pageBottom, l.box.bottom);
            heights.add(l.box.height());
        }
        if (heights.isEmpty()) {
            return firstSubstantialLine(lines);
        }

        int medianHeight = median(heights);
        int maxHeight = Collections.max(heights);
        int cutoff = pageTop + (int) ((pageBottom - pageTop) * 0.25);

        ParsedLine winner = null;
        int bestScore = Integer.MIN_VALUE;

        for (ParsedLine l : lines) {
            if (l.box == null || l.box.top > cutoff) {
                continue;   // store names print at the head of the receipt
            }
            String text = l.text.trim();
            if (text.length() < 3) {
                continue;
            }
            String lower = text.toLowerCase(Locale.US);

            int score = 0;
            if (l.box.height() > medianHeight) score += 2;
            if (ZIP.matcher(text).find() || PHONE.matcher(text).find()
                    || STREET.matcher(lower).find()) {
                score -= 3;
            }
            if (isMajorityDigits(text)) score -= 2;
            if (NOT_A_NAME.matcher(lower).find()) score -= 2;

            if (score > bestScore) {
                bestScore = score;
                winner = l;
            }
        }

        if (winner == null) {
            return firstSubstantialLine(lines);
        }

        float confidence = winner.box.height() == maxHeight ? 0.9f : 0.6f;
        return new Field<>(titleCase(clean(winner.text)), confidence, winner.text, winner.box);
    }

    private Field<String> firstSubstantialLine(List<ParsedLine> lines) {
        for (ParsedLine l : lines) {
            if (l.text.trim().length() > 3) {
                return new Field<>(titleCase(clean(l.text)), 0.3f, l.text, l.box);
            }
        }
        return Field.missing();
    }

    private boolean isMajorityDigits(String text) {
        int digits = 0;
        int letters = 0;
        for (char c : text.toCharArray()) {
            if (Character.isDigit(c)) digits++;
            else if (Character.isLetter(c)) letters++;
        }
        return digits > letters;
    }

    private String clean(String s) {
        return s.replaceAll("[#*]+", " ").replaceAll("\\s+", " ").trim();
    }

    private String titleCase(String s) {
        String[] parts = s.toLowerCase(Locale.US).split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    private int median(List<Integer> values) {
        List<Integer> copy = new ArrayList<>(values);
        Collections.sort(copy);
        return copy.get(copy.size() / 2);
    }

    // -------------------------------------------------------------- Date

    private Field<String> extractDate(List<ParsedLine> lines, LocalDate today) {
        int pageTop = Integer.MAX_VALUE;
        int pageBottom = Integer.MIN_VALUE;
        for (ParsedLine l : lines) {
            if (l.box == null) continue;
            pageTop = Math.min(pageTop, l.box.top);
            pageBottom = Math.max(pageBottom, l.box.bottom);
        }
        int span = Math.max(1, pageBottom - pageTop);
        LocalDate oldest = today.minusMonths(MAX_AGE_MONTHS);

        ParsedLine bestLine = null;
        LocalDate best = null;
        boolean bestWellPlaced = false;

        for (ParsedLine l : lines) {
            LocalDate d = firstDateIn(l.text);
            if (d == null) {
                continue;
            }
            if (d.isAfter(today) || d.isBefore(oldest)) {
                continue;   // a receipt is never from the future, rarely from three years ago
            }
            // Item lines are full of digit runs that parse as dates; the real date is
            // printed in the header or the footer.
            boolean wellPlaced = false;
            if (l.box != null) {
                double rel = (l.box.top - pageTop) / (double) span;
                wellPlaced = rel <= 0.20 || rel >= 0.80;
            }
            if (best == null || (wellPlaced && !bestWellPlaced)) {
                best = d;
                bestLine = l;
                bestWellPlaced = wellPlaced;
            }
        }

        if (best == null) {
            return new Field<>(today.format(OUT), DATE_FALLBACK_CONFIDENCE, null, null);
        }
        return new Field<>(best.format(OUT), bestWellPlaced ? 0.85f : 0.6f,
                bestLine.text, bestLine.box);
    }

    private LocalDate firstDateIn(String text) {
        String lower = text.toLowerCase(Locale.US);

        Matcher iso = DATE_ISO.matcher(lower);
        if (iso.find()) {
            return safeDate(num(iso.group(1)), num(iso.group(2)), num(iso.group(3)));
        }

        Matcher textual = DATE_TEXTUAL.matcher(lower);
        if (textual.find()) {
            int month = monthIndex(textual.group(1)) + 1;
            return safeDate(num(textual.group(3)), month, num(textual.group(2)));
        }

        Matcher numeric = DATE_NUMERIC.matcher(lower);
        if (numeric.find()) {
            int a = num(numeric.group(1));
            int b = num(numeric.group(2));
            int year = num(numeric.group(3));
            if (year < 100) {
                year += 2000;
            }
            // US receipts print MM/DD/YYYY; fall back to DD/MM when the first field cannot
            // be a month.
            if (a > 12 && b <= 12) {
                return safeDate(year, b, a);
            }
            return safeDate(year, a, b);
        }
        return null;
    }

    private int monthIndex(String abbrev) {
        for (int i = 0; i < MONTHS.length; i++) {
            if (MONTHS[i].equals(abbrev)) {
                return i;
            }
        }
        return 0;
    }

    private int num(String s) {
        return Integer.parseInt(s);
    }

    private LocalDate safeDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (java.time.DateTimeException e) {
            return null;
        }
    }
}
