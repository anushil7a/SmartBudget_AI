package com.example.project3_aadhika8_sguragai.sense.classify;

import com.example.project3_aadhika8_sguragai.data.CategoryCount;
import com.example.project3_aadhika8_sguragai.data.ExpenseCategory;
import com.example.project3_aadhika8_sguragai.data.ModelDao;
import com.example.project3_aadhika8_sguragai.data.TokenCount;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Multinomial Naive Bayes over the merchant string, in log space, learning from corrections.
 *
 * <p>Two things distinguish this from the dead {@code CategoryPredictor} it replaces. It is
 * actually called — on every save, scanned or manual. And it can <em>unlearn</em>: correcting
 * a category decrements the counts it originally credited to the wrong class, so that class
 * does not quietly keep the evidence and blur every later prediction.
 *
 * <p>The model lives in {@link ModelDao}. That is a plain interface, so this class is
 * unit-testable against an in-memory fake with no device involved.
 */
public class NaiveBayesClassifier {

    /** Laplace smoothing. */
    private static final double ALPHA = 1.0;

    /** Whole words carry more signal than the trigrams that back them up. */
    private static final int WORD_WEIGHT = 3;
    private static final int TRIGRAM_WEIGHT = 1;

    private static final int MAX_EVIDENCE = 3;

    /** An explicit user pin, which beats anything the model has learned. */
    public interface PinLookup {
        ExpenseCategory pinnedFor(String merchant);
    }

    private final ModelDao dao;
    private final PinLookup pins;

    public NaiveBayesClassifier(ModelDao dao) {
        this(dao, null);
    }

    public NaiveBayesClassifier(ModelDao dao, PinLookup pins) {
        this.dao = dao;
        this.pins = pins;
    }

    // ------------------------------------------------------------ Features

    /**
     * Merchant string to weighted features.
     *
     * <p>Word tokens are the signal; character trigrams are the safety net. "MCDONALDS #4021"
     * and the seeded "mcdonald" share no word token, but they share six trigrams, which is
     * what keeps a truncated or misspelt OCR read landing in the right category.
     */
    public static Map<String, Integer> features(String merchant) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (merchant == null) {
            return out;
        }
        String normalised = merchant.toLowerCase(Locale.US).replaceAll("[^a-z0-9\\s]", " ");
        for (String word : normalised.split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            out.merge(word, WORD_WEIGHT, Integer::sum);
            for (int i = 0; i + 3 <= word.length(); i++) {
                out.merge(word.substring(i, i + 3), TRIGRAM_WEIGHT, Integer::sum);
            }
        }
        return out;
    }

    // ------------------------------------------------------------- Predict

    public Prediction predict(String merchant) {
        if (pins != null && merchant != null && !merchant.trim().isEmpty()) {
            ExpenseCategory pinned = pins.pinnedFor(merchant);
            if (pinned != null) {
                // The user said "always this". Nothing to be uncertain about.
                return new Prediction(pinned, 1.0f, Collections.singletonList("pinned"));
            }
        }

        Map<String, Integer> feats = features(merchant);
        if (feats.isEmpty()) {
            return Prediction.unknown();
        }

        int totalDocs = dao.totalDocs();
        if (totalDocs == 0) {
            return Prediction.unknown();
        }

        int vocab = Math.max(1, dao.vocabularySize());
        ExpenseCategory[] all = ExpenseCategory.values();

        Map<ExpenseCategory, CategoryCount> catCounts = new EnumMap<>(ExpenseCategory.class);
        for (CategoryCount cc : dao.allCategoryCounts()) {
            catCounts.put(cc.category, cc);
        }

        // token -> category -> count, for just the tokens in this input
        Map<String, Map<ExpenseCategory, Integer>> counts = new LinkedHashMap<>();
        for (TokenCount tc : dao.tokenCountsFor(new ArrayList<>(feats.keySet()))) {
            counts.computeIfAbsent(tc.token, k -> new EnumMap<>(ExpenseCategory.class))
                    .put(tc.category, tc.count);
        }

        // A category nobody has ever filed anything under cannot be predicted. Beyond being
        // meaningless, it actively wins: with tokenTotal 0 its smoothing denominator is the
        // smallest of any class, so every *unmatched* token scores highest there and an
        // untrained category swallows anything unfamiliar. It becomes predictable the moment
        // the user files something under it.
        List<ExpenseCategory> considered = new ArrayList<>();
        for (ExpenseCategory c : all) {
            CategoryCount cc = catCounts.get(c);
            if (cc != null && cc.docCount > 0) {
                considered.add(c);
            }
        }
        if (considered.isEmpty()) {
            return Prediction.unknown();
        }

        // If not one token of this input has ever been seen in any trained category, there is
        // no evidence to reason from. Say so, rather than letting the smallest vocabulary win.
        boolean anyEvidence = false;
        for (Map<ExpenseCategory, Integer> byCat : counts.values()) {
            for (ExpenseCategory c : considered) {
                Integer v = byCat.get(c);
                if (v != null && v > 0) {
                    anyEvidence = true;
                    break;
                }
            }
            if (anyEvidence) {
                break;
            }
        }
        if (!anyEvidence) {
            return Prediction.unknown();
        }

        Map<ExpenseCategory, Double> scores = new EnumMap<>(ExpenseCategory.class);
        // per category, per token log-likelihood — kept so evidence can be worked out after
        Map<ExpenseCategory, Map<String, Double>> perToken = new EnumMap<>(ExpenseCategory.class);

        for (ExpenseCategory c : considered) {
            CategoryCount cc = catCounts.get(c);
            int docCount = cc.docCount;
            int tokenTotal = cc.tokenTotal;

            double score = Math.log(docCount + ALPHA)
                    - Math.log(totalDocs + ALPHA * considered.size());

            Map<String, Double> tokenScores = new LinkedHashMap<>();
            double denominator = Math.log(tokenTotal + ALPHA * vocab);

            for (Map.Entry<String, Integer> f : feats.entrySet()) {
                Map<ExpenseCategory, Integer> byCat = counts.get(f.getKey());
                int count = byCat == null || byCat.get(c) == null ? 0 : byCat.get(c);
                double logP = Math.log(count + ALPHA) - denominator;
                double weighted = f.getValue() * logP;
                score += weighted;
                tokenScores.put(f.getKey(), logP);
            }

            scores.put(c, score);
            perToken.put(c, tokenScores);
        }

        ExpenseCategory winner = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Map.Entry<ExpenseCategory, Double> e : scores.entrySet()) {
            if (e.getValue() > bestScore) {
                bestScore = e.getValue();
                winner = e.getKey();
            }
        }

        float confidence = (float) softmax(scores, bestScore);
        List<String> evidence = evidenceFor(winner, feats, perToken);
        return new Prediction(winner, confidence, evidence);
    }

    /** Posterior of the winner, computed stably by subtracting the max before exponentiating. */
    private double softmax(Map<ExpenseCategory, Double> scores, double best) {
        double sum = 0;
        for (double s : scores.values()) {
            sum += Math.exp(s - best);
        }
        return sum == 0 ? 0 : 1.0 / sum;
    }

    /**
     * The three word tokens that most favour the winner over its closest rival. Trigrams are
     * excluded — "sta" explains nothing to a person; "starbucks" does.
     */
    private List<String> evidenceFor(ExpenseCategory winner,
                                     Map<String, Integer> feats,
                                     Map<ExpenseCategory, Map<String, Double>> perToken) {
        if (winner == null) {
            return Collections.emptyList();
        }
        Map<String, Double> winnerScores = perToken.get(winner);
        List<Map.Entry<String, Double>> ranked = new ArrayList<>();

        for (String token : feats.keySet()) {
            if (feats.get(token) < WORD_WEIGHT) {
                continue;   // trigram
            }
            double runnerUp = Double.NEGATIVE_INFINITY;
            for (Map.Entry<ExpenseCategory, Map<String, Double>> e : perToken.entrySet()) {
                if (e.getKey() == winner) {
                    continue;
                }
                runnerUp = Math.max(runnerUp, e.getValue().getOrDefault(token, 0d));
            }
            double ratio = winnerScores.getOrDefault(token, 0d) - runnerUp;
            if (ratio > 0) {
                ranked.add(new java.util.AbstractMap.SimpleEntry<>(token, ratio));
            }
        }

        ranked.sort(Comparator.<Map.Entry<String, Double>>comparingDouble(Map.Entry::getValue)
                .reversed());

        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_EVIDENCE, ranked.size()); i++) {
            out.add(ranked.get(i).getKey());
        }
        return out;
    }

    // --------------------------------------------------------------- Learn

    /** Called on every expense save, scanned or manual. */
    public void learn(String merchant, ExpenseCategory category) {
        adjust(merchant, category, 1);
    }

    /** Seeding inserts each keyword as a pseudo-document with extra weight. */
    public void learnWeighted(String merchant, ExpenseCategory category, int weight) {
        adjust(merchant, category, weight);
    }

    /**
     * Take back what {@link #learn} credited. Called with the <em>old</em> category when the
     * user corrects one; without this the wrong class keeps its counts forever.
     */
    public void unlearn(String merchant, ExpenseCategory category) {
        adjust(merchant, category, -1);
    }

    /** A correction is one atomic unlearn-then-learn. */
    public void correct(String merchant, ExpenseCategory from, ExpenseCategory to) {
        if (from == to) {
            return;
        }
        if (from != null) {
            unlearn(merchant, from);
        }
        if (to != null) {
            learn(merchant, to);
        }
    }

    private void adjust(String merchant, ExpenseCategory category, int multiplier) {
        if (category == null) {
            return;
        }
        Map<String, Integer> feats = features(merchant);
        if (feats.isEmpty()) {
            return;
        }

        int tokenDelta = 0;
        for (Map.Entry<String, Integer> f : feats.entrySet()) {
            Integer existing = dao.rawTokenCount(f.getKey(), category);
            int current = existing == null ? 0 : existing;
            int delta = f.getValue() * multiplier;
            int updated = Math.max(0, current + delta);
            tokenDelta += updated - current;      // what actually changed, after flooring
            dao.upsertToken(new TokenCount(f.getKey(), category, updated));
        }

        CategoryCount cc = dao.categoryCount(category);
        if (cc == null) {
            cc = new CategoryCount(category, 0, 0);
        }
        cc.docCount = Math.max(0, cc.docCount + (multiplier > 0 ? 1 : -1));
        cc.tokenTotal = Math.max(0, cc.tokenTotal + tokenDelta);
        dao.upsertCategory(cc);
    }
}
