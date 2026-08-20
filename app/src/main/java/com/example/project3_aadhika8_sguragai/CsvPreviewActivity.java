package com.example.project3_aadhika8_sguragai;

import com.example.project3_aadhika8_sguragai.data.*;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class CsvPreviewActivity extends AppCompatActivity {

    private AppDatabase db;
    private ExpenseDao expenseDao;
    private TextView textCsvPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_csv_preview);

        textCsvPreview = findViewById(R.id.textCsvPreview);

        db = AppDatabase.getInstance(getApplicationContext());
        expenseDao = db.expenseDao();

        loadCsvText();
    }

    private void loadCsvText() {
        List<Expense> allExpenses = expenseDao.getAllExpenses();

        if (allExpenses == null || allExpenses.isEmpty()) {
            textCsvPreview.setText("No expenses to show.");
            return;
        }

        StringBuilder sb = new StringBuilder();

        sb.append("Title,Amount,Category,Date,Note\n");

        for (Expense e : allExpenses) {
            String title = e.title == null ? "" : e.title;
            String amount = String.format(Locale.getDefault(), "%.2f", e.amount);
            String category = e.category == null ? "" : e.category.name();
            String date = e.date == null ? "" : e.date;
            String note = e.note == null ? "" : e.note;

            title = title.replace("\"", "\"\"");
            category = category.replace("\"", "\"\"");
            date = date.replace("\"", "\"\"");
            note = note.replace("\"", "\"\"");

            sb.append("\"").append(title).append("\",")
                    .append("\"").append(amount).append("\",")
                    .append("\"").append(category).append("\",")
                    .append("\"").append(date).append("\",")
                    .append("\"").append(note).append("\"\n");
        }

        textCsvPreview.setText(sb.toString());
    }
}
