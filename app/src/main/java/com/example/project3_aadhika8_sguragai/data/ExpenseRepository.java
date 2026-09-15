package com.example.project3_aadhika8_sguragai.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.example.project3_aadhika8_sguragai.sense.classify.MerchantPins;
import com.example.project3_aadhika8_sguragai.sense.classify.NaiveBayesClassifier;
import com.example.project3_aadhika8_sguragai.sense.classify.Prediction;
import com.example.project3_aadhika8_sguragai.sense.classify.SeedCorpus;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The single door to the database.
 *
 * <p>Room no longer allows main-thread queries, so every read that is not already a
 * {@link LiveData} and every write goes through the one background thread this class owns.
 * Activities and ViewModels never touch a DAO directly.
 */
public class ExpenseRepository {

    private static final String TAG = "ExpenseRepository";
    private static final String PREFS = "getsense";
    private static final String KEY_SEEDED = "classifier_seeded_v1";

    private static volatile ExpenseRepository instance;

    private final Context app;
    private final AppDatabase db;
    private final File filesDir;
    private volatile NaiveBayesClassifier classifier;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    /** Result callback that lands back on the main thread. */
    public interface Result<T> {
        void onResult(T value);
    }

    private ExpenseRepository(Context ctx) {
        this.app = ctx.getApplicationContext();
        this.db = AppDatabase.getInstance(app);
        this.filesDir = app.getFilesDir();
        seedClassifierOnce();
    }

    /**
     * Give the classifier its starting opinion, exactly once, on the io thread.
     *
     * <p>Guarded by a preference rather than by "is the table empty", because a user who has
     * unlearned their way down to an empty model should not have the seed corpus quietly
     * pushed back in.
     */
    private void seedClassifierOnce() {
        io.execute(() -> {
            SharedPreferences prefs =
                    app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (prefs.getBoolean(KEY_SEEDED, false)) {
                return;
            }
            try {
                db.runInTransaction(() -> SeedCorpus.seed(classifier()));
                prefs.edit().putBoolean(KEY_SEEDED, true).apply();
            } catch (Exception e) {
                Log.e(TAG, "seeding failed", e);
            }
        });
    }

    /** The shared classifier. Call its methods on the io thread only. */
    public NaiveBayesClassifier classifier() {
        if (classifier == null) {
            synchronized (this) {
                if (classifier == null) {
                    classifier = new NaiveBayesClassifier(
                            db.modelDao(), new MerchantPins(db.merchantCategoryDao()));
                }
            }
        }
        return classifier;
    }

    /** Predict off the main thread, deliver on it. */
    public void predictCategory(String merchant, Result<Prediction> onResult) {
        query(() -> classifier().predict(merchant), onResult);
    }

    /** Called on every expense save, scanned or typed. */
    public void learn(String merchant, ExpenseCategory category) {
        io.execute(() -> classifier().learn(merchant, category));
    }

    /** A correction: strip the old category's counts, then credit the new one. */
    public void correctCategory(String merchant, ExpenseCategory from, ExpenseCategory to) {
        io.execute(() -> db.runInTransaction(
                () -> classifier().correct(merchant, from, to)));
    }

    public static ExpenseRepository get(Context ctx) {
        if (instance == null) {
            synchronized (ExpenseRepository.class) {
                if (instance == null) {
                    instance = new ExpenseRepository(ctx);
                }
            }
        }
        return instance;
    }

    // ---- DAO accessors, for callers already running on the io thread ----

    public ExpenseDao expenseDao() {
        return db.expenseDao();
    }

    public BudgetDao budgetDao() {
        return db.budgetDao();
    }

    public MerchantCategoryDao merchantCategoryDao() {
        return db.merchantCategoryDao();
    }

    public ChatMessageDao chatMessageDao() {
        return db.chatMessageDao();
    }

    public ModelDao modelDao() {
        return db.modelDao();
    }

    public AppDatabase database() {
        return db;
    }

    public File filesDir() {
        return filesDir;
    }

    // ---- Threading primitives ----

    /** Run work on the background thread, discarding the result. */
    public void io(Runnable work) {
        io.execute(work);
    }

    /** Run work on the background thread and deliver the result on the main thread. */
    public <T> void query(Callable<T> work, Result<T> onResult) {
        io.execute(() -> {
            T value = null;
            try {
                value = work.call();
            } catch (Exception e) {
                Log.e(TAG, "query failed", e);
            }
            final T delivered = value;
            main.post(() -> onResult.onResult(delivered));
        });
    }

    // ---- Observable reads ----

    public LiveData<List<Expense>> observeAll() {
        return db.expenseDao().observeAllExpenses();
    }

    public LiveData<List<Expense>> observeRange(String start, String end) {
        return db.expenseDao().observeExpensesInRange(start, end);
    }

    public LiveData<List<Expense>> observeNeedsReview(float threshold) {
        return db.expenseDao().observeNeedsReview(threshold);
    }

    public LiveData<Double> observeTotalForRange(String start, String end) {
        return db.expenseDao().observeTotalForRange(start, end);
    }

    public LiveData<Double> observeBudget() {
        return db.budgetDao().observeBudget();
    }

    // ---- Writes ----

    public void insert(Expense e, Result<Long> onId) {
        query(() -> db.expenseDao().insert(e), onId);
    }

    public void update(Expense e, Runnable done) {
        io.execute(() -> {
            db.expenseDao().update(e);
            if (done != null) {
                main.post(done);
            }
        });
    }

    /** Deleting an expense also deletes the receipt image it owns. */
    public void delete(Expense e, Runnable done) {
        io.execute(() -> {
            String path = e.receiptPath;
            db.expenseDao().delete(e);
            if (path != null) {
                File f = new File(filesDir, path);
                if (f.exists() && !f.delete()) {
                    Log.w(TAG, "could not delete receipt " + path);
                }
            }
            if (done != null) {
                main.post(done);
            }
        });
    }

    public void setBudget(double amount) {
        io.execute(() -> {
            Budget b = new Budget();
            b.id = 1;
            b.amount = amount;
            db.budgetDao().insert(b);
        });
    }
}
