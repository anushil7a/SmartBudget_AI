package com.example.project3_aadhika8_sguragai;

import com.example.project3_aadhika8_sguragai.data.*;

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
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class ReceiptScanActivity extends AppCompatActivity {

    private ImageView imageReceipt;
    private LinearLayout layoutPlaceholder;
    private FrameLayout layoutProcessing;
    private MaterialButton buttonCamera;
    private MaterialButton buttonGallery;
    private MaterialCardView cardExtractedInfo;

    private TextInputEditText editMerchant;
    private TextInputEditText editAmount;
    private MaterialButton buttonDate;
    private MaterialAutoCompleteTextView dropdownCategory;
    private TextInputEditText editNote;
    private MaterialButton buttonConfirm;

    private OcrProcessor ocrProcessor;
    private AppDatabase db;
    private ExpenseDao expenseDao;

    private Uri photoUri;
    private Calendar selectedDate;
    private Bitmap currentBitmap;

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && photoUri != null) {
                    processImage(photoUri);
                }
            }
    );

    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        processImage(imageUri);
                    }
                }
            }
    );

    private final ActivityResultLauncher<String> requestCameraPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    launchCamera();
                } else {
                    Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt_scan);

        initViews();
        initDatabase();
        setupToolbar();
        setupClickListeners();
        setupCategoryDropdown();

        ocrProcessor = new OcrProcessor();
        selectedDate = Calendar.getInstance();
        updateDateButton();
    }

    private void initViews() {
        imageReceipt = findViewById(R.id.imageReceipt);
        layoutPlaceholder = findViewById(R.id.layoutPlaceholder);
        layoutProcessing = findViewById(R.id.layoutProcessing);
        buttonCamera = findViewById(R.id.buttonCamera);
        buttonGallery = findViewById(R.id.buttonGallery);
        cardExtractedInfo = findViewById(R.id.cardExtractedInfo);

        editMerchant = findViewById(R.id.editMerchant);
        editAmount = findViewById(R.id.editAmount);
        buttonDate = findViewById(R.id.buttonDate);
        dropdownCategory = findViewById(R.id.dropdownCategory);
        editNote = findViewById(R.id.editNote);
        buttonConfirm = findViewById(R.id.buttonConfirm);
    }

    private void initDatabase() {
        db = AppDatabase.getInstance(getApplicationContext());
        expenseDao = db.expenseDao();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupClickListeners() {
        buttonCamera.setOnClickListener(v -> checkCameraPermissionAndLaunch());
        buttonGallery.setOnClickListener(v -> launchGallery());
        buttonDate.setOnClickListener(v -> showDatePicker());
        buttonConfirm.setOnClickListener(v -> saveExpense());
    }

    private void setupCategoryDropdown() {
        ExpenseCategory[] cats = ExpenseCategory.values();
        String[] categoryNames = Arrays.stream(cats)
                .map(c -> formatCategoryName(c.name()))
                .toArray(String[]::new);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, categoryNames);
        dropdownCategory.setAdapter(adapter);
        dropdownCategory.setText(categoryNames[0], false);
    }

    private void checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        
        File photoFile;
        try {
            photoFile = createImageFile();
        } catch (IOException e) {
            Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show();
            return;
        }

        photoUri = FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", photoFile);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        cameraLauncher.launch(intent);
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "RECEIPT_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void launchGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    private void processImage(Uri imageUri) {
        showProcessing(true);

        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            currentBitmap = BitmapFactory.decodeStream(inputStream);
            
            if (currentBitmap != null) {
                imageReceipt.setImageBitmap(currentBitmap);
                imageReceipt.setVisibility(View.VISIBLE);
                layoutPlaceholder.setVisibility(View.GONE);

                // Process with OCR
                ocrProcessor.processImage(currentBitmap, new OcrProcessor.OcrCallback() {
                    @Override
                    public void onSuccess(OcrProcessor.OcrResult result) {
                        runOnUiThread(() -> {
                            showProcessing(false);
                            displayExtractedInfo(result);
                        });
                    }

                    @Override
                    public void onFailure(String error) {
                        runOnUiThread(() -> {
                            showProcessing(false);
                            Toast.makeText(ReceiptScanActivity.this,
                                    getString(R.string.error_ocr_failed) + ": " + error,
                                    Toast.LENGTH_LONG).show();
                            // Still show the card for manual entry
                            cardExtractedInfo.setVisibility(View.VISIBLE);
                        });
                    }
                });
            }
        } catch (Exception e) {
            showProcessing(false);
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
        }
    }

    private void showProcessing(boolean show) {
        layoutProcessing.setVisibility(show ? View.VISIBLE : View.GONE);
        buttonCamera.setEnabled(!show);
        buttonGallery.setEnabled(!show);
    }

    private void displayExtractedInfo(OcrProcessor.OcrResult result) {
        cardExtractedInfo.setVisibility(View.VISIBLE);

        if (result.merchant != null && !result.merchant.isEmpty()) {
            editMerchant.setText(result.merchant);
        }

        if (result.amount != null) {
            editAmount.setText(String.format(Locale.getDefault(), "%.2f", result.amount));
        }

        if (result.date != null) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date date = sdf.parse(result.date);
                if (date != null) {
                    selectedDate.setTime(date);
                    updateDateButton();
                }
            } catch (Exception ignored) {}
        }

        if (result.suggestedCategory != null) {
            dropdownCategory.setText(formatCategoryName(result.suggestedCategory.name()), false);
        }
    }

    private void showDatePicker() {
        new DatePickerDialog(this,
                (view, year, month, day) -> {
                    selectedDate.set(year, month, day);
                    updateDateButton();
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void updateDateButton() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        buttonDate.setText(sdf.format(selectedDate.getTime()));
    }

    private void saveExpense() {
        String merchant = editMerchant.getText().toString().trim();
        String amountStr = editAmount.getText().toString().trim();
        String note = editNote.getText().toString().trim();

        if (merchant.isEmpty()) {
            editMerchant.setError(getString(R.string.error_title_required));
            return;
        }

        if (amountStr.isEmpty()) {
            editAmount.setError(getString(R.string.error_amount_required));
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            editAmount.setError(getString(R.string.error_invalid_amount));
            return;
        }

        // Get category
        String selectedCatName = dropdownCategory.getText().toString();
        ExpenseCategory category = ExpenseCategory.OTHER;
        for (ExpenseCategory cat : ExpenseCategory.values()) {
            if (formatCategoryName(cat.name()).equals(selectedCatName)) {
                category = cat;
                break;
            }
        }

        // Get date
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String dateStr = sdf.format(selectedDate.getTime());

        // Create and save expense
        Expense expense = new Expense();
        expense.title = merchant;
        expense.amount = amount;
        expense.category = category;
        expense.date = dateStr;
        expense.note = note.isEmpty() ? "Scanned from receipt" : note;

        expenseDao.insert(expense);

        Toast.makeText(this, R.string.expense_added, Toast.LENGTH_SHORT).show();
        finish();
    }

    private String formatCategoryName(String name) {
        if (name == null || name.isEmpty()) return "";
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.getDefault());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ocrProcessor != null) {
            ocrProcessor.close();
        }
    }
}
