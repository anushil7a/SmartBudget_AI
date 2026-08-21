package com.example.project3_aadhika8_sguragai.ui.scan;

import android.app.Application;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.project3_aadhika8_sguragai.data.CaptureSource;
import com.example.project3_aadhika8_sguragai.data.CategorySource;
import com.example.project3_aadhika8_sguragai.data.Expense;
import com.example.project3_aadhika8_sguragai.data.ExpenseCategory;
import com.example.project3_aadhika8_sguragai.data.ExpenseRepository;
import com.example.project3_aadhika8_sguragai.sense.capture.ReceiptExtraction;
import com.example.project3_aadhika8_sguragai.sense.capture.ReceiptScanner;
import com.example.project3_aadhika8_sguragai.sense.capture.ReceiptStore;
import com.example.project3_aadhika8_sguragai.sense.classify.Prediction;

/**
 * Holds one in-progress scan: the image, what the parser made of it, and what the classifier
 * thinks it is.
 *
 * <p>Keeping this out of the Activity is what lets the extraction survive a rotation — the
 * previous screen lost the whole scan and made the user photograph the receipt again.
 */
public class ScanViewModel extends AndroidViewModel {

    private final ExpenseRepository repo;
    private final ReceiptScanner scanner = new ReceiptScanner();
    private final ReceiptStore store;

    private final MutableLiveData<ReceiptExtraction> extraction = new MutableLiveData<>();
    private final MutableLiveData<Prediction> prediction = new MutableLiveData<>();
    private final MutableLiveData<Boolean> scanning = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private Bitmap bitmap;

    /** What the classifier originally said, so a correction can unlearn the right category. */
    private ExpenseCategory predictedCategory;

    public ScanViewModel(@NonNull Application application) {
        super(application);
        repo = ExpenseRepository.get(application);
        store = new ReceiptStore(application.getFilesDir());
    }

    public LiveData<ReceiptExtraction> extraction() {
        return extraction;
    }

    public LiveData<Prediction> prediction() {
        return prediction;
    }

    public LiveData<Boolean> scanning() {
        return scanning;
    }

    public LiveData<String> error() {
        return error;
    }

    public Bitmap bitmap() {
        return bitmap;
    }

    public void scan(Bitmap source) {
        this.bitmap = source;
        scanning.setValue(true);

        scanner.scan(source, new ReceiptScanner.Callback() {
            @Override
            public void onExtracted(ReceiptExtraction result) {
                scanning.postValue(false);
                extraction.postValue(result);
                if (result.merchant.isPresent()) {
                    predict(result.merchant.value);
                }
            }

            @Override
            public void onError(Exception e) {
                scanning.postValue(false);
                error.postValue(e.getMessage());
            }
        });
    }

    private void predict(String merchant) {
        repo.predictCategory(merchant, p -> {
            if (p != null) {
                predictedCategory = p.category;
                prediction.setValue(p);
            }
        });
    }

    /**
     * Save, and teach.
     *
     * <p>The classifier learns on every save. If the user changed the category away from what
     * was predicted, the prediction is unlearned first — otherwise the wrong category keeps
     * the counts and every later guess gets blurrier.
     */
    public void save(String merchant,
                     double amount,
                     ExpenseCategory category,
                     String date,
                     String note,
                     Runnable onSaved) {

        Expense e = new Expense();
        e.merchant = merchant;
        e.title = merchant;
        e.amount = amount;
        e.category = category;
        e.date = date;
        e.note = note;
        e.source = CaptureSource.SCANNED;
        e.createdAt = System.currentTimeMillis();

        boolean userChanged = predictedCategory != null && predictedCategory != category;
        Prediction p = prediction.getValue();

        if (userChanged || predictedCategory == null) {
            e.categorySource = CategorySource.USER;
            e.predictionConfidence = null;
        } else {
            e.categorySource = CategorySource.PREDICTED;
            e.predictionConfidence = p == null ? null : p.confidence;
        }

        if (bitmap != null) {
            e.receiptPath = store.save(bitmap);
        }

        repo.insert(e, id -> {
            if (userChanged) {
                repo.correctCategory(merchant, predictedCategory, category);
            } else {
                repo.learn(merchant, category);
            }
            if (onSaved != null) {
                onSaved.run();
            }
        });
    }

    @Override
    protected void onCleared() {
        scanner.close();
        super.onCleared();
    }
}
