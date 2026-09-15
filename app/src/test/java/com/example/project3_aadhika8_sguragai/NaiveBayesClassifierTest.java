package com.example.project3_aadhika8_sguragai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.example.project3_aadhika8_sguragai.data.ExpenseCategory;
import com.example.project3_aadhika8_sguragai.sense.Sense;
import com.example.project3_aadhika8_sguragai.sense.classify.NaiveBayesClassifier;
import com.example.project3_aadhika8_sguragai.sense.classify.Prediction;
import com.example.project3_aadhika8_sguragai.sense.classify.SeedCorpus;

import org.junit.Before;
import org.junit.Test;

/**
 * The "gets smarter" claim is the whole premise of the app, and in the previous code it was
 * never exercised — the classifier was constructed nowhere and {@code learn()} was never
 * called. These tests pin the behaviour that claim depends on: a correction has to move
 * evidence, not just add it.
 */
public class NaiveBayesClassifierTest {

    private FakeModelDao dao;
    private NaiveBayesClassifier classifier;

    @Before
    public void setUp() {
        dao = new FakeModelDao();
        classifier = new NaiveBayesClassifier(dao);
        SeedCorpus.seed(classifier);
    }

    @Test
    public void seededModelPredictsKnownMerchant() {
        Prediction p = classifier.predict("STARBUCKS #4021");
        assertEquals(ExpenseCategory.FOOD, p.category);
        assertTrue("a seeded merchant should not need review, was " + p.confidence,
                p.confidence > Sense.REVIEW_THRESHOLD);
    }

    @Test
    public void seededModelSeparatesCategories() {
        assertEquals(ExpenseCategory.TRANSPORT, classifier.predict("SHELL OIL").category);
        assertEquals(ExpenseCategory.GROCERIES, classifier.predict("TRADER JOE'S").category);
        assertEquals(ExpenseCategory.ENTERTAINMENT, classifier.predict("NETFLIX.COM").category);
        assertEquals(ExpenseCategory.BILLS, classifier.predict("VERIZON WIRELESS").category);
    }

    @Test
    public void learningAnUnknownMerchantMakesItPredictable() {
        assertNotEquals(ExpenseCategory.FOOD, classifier.predict("blue bottle").category);
        for (int i = 0; i < 3; i++) {
            classifier.learn("blue bottle", ExpenseCategory.FOOD);
        }
        assertEquals(ExpenseCategory.FOOD, classifier.predict("blue bottle").category);
    }

    @Test
    public void correctionFlipsPrediction() {
        classifier.learn("acme corp", ExpenseCategory.SHOPPING);
        assertEquals(ExpenseCategory.SHOPPING, classifier.predict("acme corp").category);

        classifier.correct("acme corp", ExpenseCategory.SHOPPING, ExpenseCategory.BILLS);
        assertEquals(ExpenseCategory.BILLS, classifier.predict("acme corp").category);
    }

    @Test
    public void correctionLeavesNoStaleCountsInTheOldCategory() {
        classifier.learn("acme corp", ExpenseCategory.SHOPPING);
        assertTrue("precondition: learning wrote counts",
                dao.countOrZero("acme", ExpenseCategory.SHOPPING) > 0);

        classifier.correct("acme corp", ExpenseCategory.SHOPPING, ExpenseCategory.BILLS);

        assertEquals("the wrong class must not keep its counts, or predictions go mushy",
                0, dao.countOrZero("acme", ExpenseCategory.SHOPPING));
        assertTrue(dao.countOrZero("acme", ExpenseCategory.BILLS) > 0);
    }

    @Test
    public void unlearnRemovesTheEvidenceItAdded() {
        classifier.learn("zzz widgets", ExpenseCategory.FOOD);
        assertEquals(ExpenseCategory.FOOD, classifier.predict("zzz widgets").category);

        classifier.unlearn("zzz widgets", ExpenseCategory.FOOD);
        assertEquals(0, dao.countOrZero("zzz", ExpenseCategory.FOOD));
    }

    @Test
    public void unlearnFloorsAtZero() {
        classifier.learn("qqq", ExpenseCategory.FOOD);
        classifier.unlearn("qqq", ExpenseCategory.FOOD);
        classifier.unlearn("qqq", ExpenseCategory.FOOD);   // must not go negative

        assertEquals(0, dao.countOrZero("qqq", ExpenseCategory.FOOD));
        assertTrue("docCount must not go negative", dao.totalDocs() >= 0);
    }

    @Test
    public void unseenEvidenceFreeStringStaysBelowThreshold() {
        Prediction p = classifier.predict("xqzj wvbk");
        assertTrue("nothing is known about this string, so do not assert a category: "
                + p.category + " @ " + p.confidence, p.confidence < Sense.REVIEW_THRESHOLD);
    }

    @Test
    public void trigramsRescueAMisspeltOrTruncatedRead() {
        // "mcdonalds" is not a seeded token; "mcdonald" is. The overlapping character
        // trigrams are what carry the prediction across the bad OCR read.
        Prediction p = classifier.predict("MCDONALDS #4021");
        assertEquals(ExpenseCategory.FOOD, p.category);
    }

    @Test
    public void evidenceTokensExplainThePrediction() {
        Prediction p = classifier.predict("STARBUCKS");
        assertTrue("expected starbucks in " + p.evidenceTokens,
                p.evidenceTokens.contains("starbucks"));
        assertTrue(p.evidenceTokens.size() <= 3);
    }

    @Test
    public void confidenceIsAProbability() {
        Prediction p = classifier.predict("STARBUCKS");
        assertTrue(p.confidence >= 0f && p.confidence <= 1f);
    }

    @Test
    public void emptyMerchantDoesNotCrashAndNeedsReview() {
        Prediction p = classifier.predict("");
        assertTrue(p.confidence < Sense.REVIEW_THRESHOLD);
    }
}
