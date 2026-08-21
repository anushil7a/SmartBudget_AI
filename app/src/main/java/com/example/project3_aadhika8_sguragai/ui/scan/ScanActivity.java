package com.example.project3_aadhika8_sguragai.ui.scan;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;

import com.example.project3_aadhika8_sguragai.R;
import com.example.project3_aadhika8_sguragai.data.ExpenseCategory;
import com.example.project3_aadhika8_sguragai.sense.Sense;
import com.example.project3_aadhika8_sguragai.sense.capture.Field;
import com.example.project3_aadhika8_sguragai.sense.capture.ReceiptExtraction;
import com.example.project3_aadhika8_sguragai.sense.classify.Prediction;
import com.example.project3_aadhika8_sguragai.ui.home.HomeActivity;
import com.example.project3_aadhika8_sguragai.ui.insights.InsightsActivity;
import com.example.project3_aadhika8_sguragai.ui.search.SearchActivity;
import com.example.project3_aadhika8_sguragai.ui.widget.ReceiptOverlayView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Photograph a receipt, watch the parse land, correct it if it is wrong.
 *
 * <p>The reveal is the point: instead of a spinner, the detected regions are highlighted in
 * order while their values type into the form, so the user can see which line each number
 * came from. The category button carries the classifier's confidence and, when it is low,
 * an alert-coloured ring — correcting it is what teaches the model.
 */
public class ScanActivity extends AppCompatActivity {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter PRETTY =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US);

    private ScanViewModel model;

    private ImageView imageReceipt;
    private ReceiptOverlayView overlay;
    private View layoutPlaceholder;
    private ProgressBar progress;
    private MaterialCardView cardReview;

    private TextInputEditText editMerchant;
    private TextInputEditText editAmount;
    private TextInputEditText editNote;
    private TextView textMerchantConfidence;
    private TextView textAmountConfidence;
    private TextView textEvidence;
    private MaterialButton buttonDate;
    private MaterialButton buttonCategory;

    private LocalDate selectedDate = LocalDate.now();
    private ExpenseCategory selectedCategory = ExpenseCategory.OTHER;
    private Uri photoUri;

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && photoUri != null) {
                    loadAndScan(photoUri);
                }
            });

    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        loadAndScan(uri);
                    }
                }
            });

    private final ActivityResultLauncher<String> cameraPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (granted) {
                    launchCamera();
                } else {
                    Toast.makeText(this, R.string.camera_permission_required,
                            Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        model = new ViewModelProvider(this).get(ScanViewModel.class);

        bindViews();
        setupNavigation();
        observeModel();
        updateDateButton();
        updateCategoryButton(null);
    }

    private void bindViews() {
        imageReceipt = findViewById(R.id.imageReceipt);
        overlay = findViewById(R.id.receiptOverlay);
        layoutPlaceholder = findViewById(R.id.layoutPlaceholder);
        progress = findViewById(R.id.progressScan);
        cardReview = findViewById(R.id.cardReview);

        editMerchant = findViewById(R.id.editMerchant);
        editAmount = findViewById(R.id.editAmount);
        editNote = findViewById(R.id.editNote);
        textMerchantConfidence = findViewById(R.id.textMerchantConfidence);
        textAmountConfidence = findViewById(R.id.textAmountConfidence);
        textEvidence = findViewById(R.id.textEvidence);
        buttonDate = findViewById(R.id.buttonDate);
        buttonCategory = findViewById(R.id.buttonCategory);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.buttonCamera).setOnClickListener(v -> checkPermissionAndCapture());
        findViewById(R.id.buttonGallery).setOnClickListener(v -> launchGallery());
        findViewById(R.id.buttonSave).setOnClickListener(v -> save());

        buttonDate.setOnClickListener(v -> pickDate());
        buttonCategory.setOnClickListener(v -> pickCategory());
    }

    private void setupNavigation() {
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_scan);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_scan) {
                return true;
            }
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, HomeActivity.class));
            } else if (id == R.id.nav_search) {
                startActivity(new Intent(this, SearchActivity.class));
            } else if (id == R.id.nav_insights) {
                startActivity(new Intent(this, InsightsActivity.class));
            }
            finish();
            return true;
        });
    }

    private void observeModel() {
        model.scanning().observe(this, busy ->
                progress.setVisibility(Boolean.TRUE.equals(busy) ? View.VISIBLE : View.GONE));

        model.error().observe(this, message -> {
            if (message != null) {
                Toast.makeText(this, getString(R.string.error_ocr_failed) + ": " + message,
                        Toast.LENGTH_LONG).show();
                // Still open the sheet: a failed read should not block manual entry.
                cardReview.setVisibility(View.VISIBLE);
            }
        });

        model.extraction().observe(this, this::onExtracted);
        model.prediction().observe(this, this::updateCategoryButton);
    }

    private void checkPermissionAndCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        File photoFile;
        try {
            String stamp = String.valueOf(System.currentTimeMillis());
            File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            photoFile = File.createTempFile("RECEIPT_" + stamp + "_", ".jpg", dir);
        } catch (IOException e) {
            Toast.makeText(this, R.string.error_image_file, Toast.LENGTH_SHORT).show();
            return;
        }

        photoUri = FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", photoFile);

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        cameraLauncher.launch(intent);
    }

    private void launchGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    private void loadAndScan(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(in);
            if (bitmap == null) {
                Toast.makeText(this, R.string.error_loading_image, Toast.LENGTH_SHORT).show();
                return;
            }
            imageReceipt.setImageBitmap(bitmap);
            imageReceipt.setVisibility(View.VISIBLE);
            layoutPlaceholder.setVisibility(View.GONE);
            overlay.setSourceSize(bitmap.getWidth(), bitmap.getHeight());
            model.scan(bitmap);
        } catch (Exception e) {
            Toast.makeText(this, R.string.error_loading_image, Toast.LENGTH_SHORT).show();
        }
    }

    /** Run the reveal, then open the sheet with the values already filled in. */
    private void onExtracted(ReceiptExtraction result) {
        if (result == null) {
            return;
        }
        overlay.reveal(result, () -> {
            fillForm(result);
            cardReview.setVisibility(View.VISIBLE);
        });
    }

    private void fillForm(ReceiptExtraction result) {
        if (result.merchant.isPresent()) {
            editMerchant.setText(result.merchant.value);
        }
        textMerchantConfidence.setText(confidenceLabel(result.merchant));
        tintConfidence(textMerchantConfidence, result.merchant);

        if (result.total.isPresent()) {
            editAmount.setText(String.format(Locale.US, "%.2f", result.total.value));
        }
        textAmountConfidence.setText(sourceLabel(result.total));
        tintConfidence(textAmountConfidence, result.total);

        if (result.date.isPresent()) {
            try {
                selectedDate = LocalDate.parse(result.date.value);
                updateDateButton();
            } catch (Exception ignored) {
                // keep today
            }
        }
    }

    private String confidenceLabel(Field<?> field) {
        if (!field.isPresent()) {
            return getString(R.string.not_found_check_this);
        }
        return getString(R.string.read_with_confidence, Math.round(field.confidence * 100));
    }

    private String sourceLabel(Field<?> field) {
        if (!field.isPresent()) {
            return getString(R.string.not_found_check_this);
        }
        if (field.sourceLine != null) {
            return getString(R.string.from_line, field.sourceLine.trim());
        }
        return getString(R.string.read_with_confidence, Math.round(field.confidence * 100));
    }

    private void tintConfidence(TextView view, Field<?> field) {
        int color = field.needsReview()
                ? getColorAttr(androidx.appcompat.R.attr.colorError)
                : getColorAttr(com.google.android.material.R.attr.colorOnSurfaceVariant);
        view.setTextColor(color);
    }

    private int getColorAttr(int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    /**
     * The confidence ring. Below the shared threshold the button is stroked in the alert
     * colour and the evidence line explains what the guess was based on.
     */
    private void updateCategoryButton(Prediction prediction) {
        if (prediction != null) {
            selectedCategory = prediction.category;
        }

        if (prediction == null) {
            buttonCategory.setText(selectedCategory.name());
            buttonCategory.setStrokeColor(android.content.res.ColorStateList.valueOf(
                    getColorAttr(com.google.android.material.R.attr.colorOutline)));
            textEvidence.setVisibility(View.GONE);
            return;
        }

        buttonCategory.setText(getString(R.string.category_with_confidence,
                prediction.category.name(), Math.round(prediction.confidence * 100)));

        boolean uncertain = prediction.confidence < Sense.REVIEW_THRESHOLD;
        buttonCategory.setStrokeColor(android.content.res.ColorStateList.valueOf(
                uncertain ? getColorAttr(androidx.appcompat.R.attr.colorError)
                          : getColorAttr(androidx.appcompat.R.attr.colorPrimary)));

        if (prediction.evidenceTokens.isEmpty()) {
            textEvidence.setVisibility(View.GONE);
        } else {
            textEvidence.setVisibility(View.VISIBLE);
            textEvidence.setText(getString(R.string.because_of,
                    String.join(", ", prediction.evidenceTokens)));
        }
    }

    private void pickCategory() {
        ExpenseCategory[] categories = ExpenseCategory.values();
        String[] labels = new String[categories.length];
        for (int i = 0; i < categories.length; i++) {
            labels[i] = categories[i].name();
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.category)
                .setItems(labels, (d, which) -> {
                    selectedCategory = categories[which];
                    buttonCategory.setText(selectedCategory.name());
                    // The ring resolves: a chosen category is certain by definition.
                    buttonCategory.setStrokeColor(android.content.res.ColorStateList.valueOf(
                            getColorAttr(androidx.appcompat.R.attr.colorPrimary)));
                    textEvidence.setVisibility(View.GONE);
                    buttonCategory.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void pickDate() {
        new DatePickerDialog(this,
                (view, year, month, day) -> {
                    selectedDate = LocalDate.of(year, month + 1, day);
                    updateDateButton();
                },
                selectedDate.getYear(),
                selectedDate.getMonthValue() - 1,
                selectedDate.getDayOfMonth()).show();
    }

    private void updateDateButton() {
        buttonDate.setText(selectedDate.format(PRETTY));
    }

    private void save() {
        String merchant = text(editMerchant);
        if (merchant.isEmpty()) {
            editMerchant.setError(getString(R.string.merchant_required));
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(text(editAmount));
        } catch (NumberFormatException e) {
            editAmount.setError(getString(R.string.amount_required));
            return;
        }

        model.save(merchant, amount, selectedCategory, selectedDate.format(ISO), text(editNote),
                () -> {
                    Toast.makeText(this, R.string.expense_saved, Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, HomeActivity.class));
                    finish();
                });
    }

    private String text(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }
}
