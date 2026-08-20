package com.example.project3_aadhika8_sguragai;

import com.example.project3_aadhika8_sguragai.data.*;

import android.graphics.Bitmap;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Processes receipt images using ML Kit OCR and extracts expense information.
 */
public class OcrProcessor {

    public interface OcrCallback {
        void onSuccess(OcrResult result);
        void onFailure(String error);
    }

    public static class OcrResult {
        public String merchant;
        public Double amount;
        public String date;
        public String rawText;
        public ExpenseCategory suggestedCategory;
    }

    private final TextRecognizer recognizer;

    public OcrProcessor() {
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    public void processImage(Bitmap bitmap, OcrCallback callback) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        
        recognizer.process(image)
                .addOnSuccessListener(text -> {
                    String rawText = text.getText();
                    OcrResult result = parseReceiptText(rawText);
                    callback.onSuccess(result);
                })
                .addOnFailureListener(e -> {
                    callback.onFailure(e.getMessage());
                });
    }

    private OcrResult parseReceiptText(String rawText) {
        OcrResult result = new OcrResult();
        result.rawText = rawText;

        // Extract merchant (usually first non-empty line or line with store name patterns)
        result.merchant = extractMerchant(rawText);

        // Extract total amount
        result.amount = extractAmount(rawText);

        // Extract date
        result.date = extractDate(rawText);

        // Suggest category based on merchant
        result.suggestedCategory = suggestCategory(result.merchant, rawText);

        return result;
    }

    private String extractMerchant(String text) {
        String[] lines = text.split("\n");
        
        // Common store name patterns to look for
        String[] storeKeywords = {"store", "mart", "shop", "market", "restaurant", "cafe", 
                "coffee", "pharmacy", "gas", "station", "grocery"};
        
        // First, try to find a line with store keywords
        for (String line : lines) {
            String lineLower = line.toLowerCase().trim();
            for (String keyword : storeKeywords) {
                if (lineLower.contains(keyword)) {
                    return cleanMerchantName(line);
                }
            }
        }
        
        // Otherwise, use the first substantial line (likely store name)
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() > 3 && !trimmed.matches("^[\\d\\s\\-\\/]+$")) {
                // Skip lines that are just numbers/dates
                return cleanMerchantName(trimmed);
            }
        }
        
        return "";
    }

    private String cleanMerchantName(String name) {
        // Remove common receipt artifacts
        name = name.replaceAll("[#*]+", "").trim();
        // Capitalize properly
        if (name.length() > 0) {
            return name.substring(0, 1).toUpperCase() + 
                   (name.length() > 1 ? name.substring(1) : "");
        }
        return name;
    }

    private Double extractAmount(String text) {
        // Patterns for total amount (prioritize "total", "grand total", "amount due", etc.)
        String[] totalPatterns = {
            "(?i)(?:grand\\s*)?total[:\\s]*\\$?([\\d,]+\\.\\d{2})",
            "(?i)amount\\s*(?:due)?[:\\s]*\\$?([\\d,]+\\.\\d{2})",
            "(?i)balance\\s*(?:due)?[:\\s]*\\$?([\\d,]+\\.\\d{2})",
            "(?i)(?:sub)?total[:\\s]*\\$?([\\d,]+\\.\\d{2})",
            "\\$([\\d,]+\\.\\d{2})"
        };

        Double highestAmount = null;
        
        for (String patternStr : totalPatterns) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(text);
            
            while (matcher.find()) {
                try {
                    String amountStr = matcher.group(1).replace(",", "");
                    double amount = Double.parseDouble(amountStr);
                    // Keep the highest amount found (likely the total)
                    if (highestAmount == null || amount > highestAmount) {
                        highestAmount = amount;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        
        return highestAmount;
    }

    private String extractDate(String text) {
        // Common date patterns on receipts
        String[] datePatterns = {
            // MM/DD/YYYY or MM-DD-YYYY
            "(\\d{1,2})[/\\-](\\d{1,2})[/\\-](\\d{4})",
            // MM/DD/YY or MM-DD-YY
            "(\\d{1,2})[/\\-](\\d{1,2})[/\\-](\\d{2})",
            // YYYY-MM-DD
            "(\\d{4})[/\\-](\\d{1,2})[/\\-](\\d{1,2})",
            // Month DD, YYYY
            "(?i)(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\s+(\\d{1,2}),?\\s+(\\d{4})"
        };

        for (String patternStr : datePatterns) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(text);
            
            if (matcher.find()) {
                String dateStr = matcher.group();
                return normalizeDate(dateStr);
            }
        }
        
        // Default to today if no date found
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date());
    }

    private String normalizeDate(String dateStr) {
        SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        
        // Try various input formats
        String[] inputFormats = {
            "MM/dd/yyyy", "MM-dd-yyyy",
            "MM/dd/yy", "MM-dd-yy",
            "yyyy-MM-dd", "yyyy/MM/dd",
            "MMM dd, yyyy", "MMM dd yyyy"
        };
        
        for (String format : inputFormats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
                Date date = sdf.parse(dateStr);
                if (date != null) {
                    return outputFormat.format(date);
                }
            } catch (ParseException ignored) {}
        }
        
        // Default to today
        return outputFormat.format(new Date());
    }

    private ExpenseCategory suggestCategory(String merchant, String rawText) {
        String searchText = (merchant + " " + rawText).toLowerCase();
        
        // Food & Dining
        if (containsAny(searchText, "starbucks", "mcdonald", "burger", "pizza", "cafe", 
                "coffee", "restaurant", "diner", "food", "eat", "lunch", "dinner", "breakfast",
                "subway", "chipotle", "taco", "sushi", "thai", "chinese", "italian")) {
            return ExpenseCategory.FOOD;
        }
        
        // Transport
        if (containsAny(searchText, "gas", "fuel", "shell", "exxon", "chevron", "mobil",
                "uber", "lyft", "taxi", "parking", "transit", "metro", "bus", "train")) {
            return ExpenseCategory.TRANSPORT;
        }
        
        // Groceries
        if (containsAny(searchText, "grocery", "market", "walmart", "target", "costco",
                "kroger", "safeway", "whole foods", "trader joe", "aldi", "publix")) {
            return ExpenseCategory.GROCERIES;
        }
        
        // Entertainment
        if (containsAny(searchText, "cinema", "movie", "theater", "netflix", "spotify",
                "concert", "ticket", "game", "amusement", "entertainment")) {
            return ExpenseCategory.ENTERTAINMENT;
        }
        
        // Shopping
        if (containsAny(searchText, "amazon", "ebay", "mall", "store", "shop", "clothing",
                "apparel", "nike", "adidas", "fashion", "electronics", "best buy")) {
            return ExpenseCategory.SHOPPING;
        }
        
        // Bills
        if (containsAny(searchText, "electric", "water", "utility", "phone", "internet",
                "cable", "insurance", "rent", "mortgage", "bill")) {
            return ExpenseCategory.BILLS;
        }
        
        return ExpenseCategory.OTHER;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public void close() {
        recognizer.close();
    }
}
