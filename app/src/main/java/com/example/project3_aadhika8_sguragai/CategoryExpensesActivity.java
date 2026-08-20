package com.example.project3_aadhika8_sguragai;

import com.example.project3_aadhika8_sguragai.data.*;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CategoryExpensesActivity extends AppCompatActivity {

    public static final String EXTRA_CATEGORY = "extra_category";
    public static final String EXTRA_START_DATE = "extra_start_date";
    public static final String EXTRA_END_DATE = "extra_end_date";

    private AppDatabase db;
    private ExpenseDao expenseDao;

    private TextView textTitle;
    private Button buttonBack;
    private ListView listCategoryExpenses;
    private Spinner spinnerCategorySort;

    private ArrayAdapter<String> adapter;
    private ArrayList<String> lines;
    private List<Expense> catExpenses;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_expenses);

        textTitle = findViewById(R.id.textCategoryTitle);
        buttonBack = findViewById(R.id.buttonBackFromCategory);
        listCategoryExpenses = findViewById(R.id.listCategoryExpenses);
        spinnerCategorySort = findViewById(R.id.spinnerCategorySort);

        db = AppDatabase.getInstance(getApplicationContext());
        expenseDao = db.expenseDao();

        String catName = getIntent().getStringExtra(EXTRA_CATEGORY);
        String start = getIntent().getStringExtra(EXTRA_START_DATE);
        String end = getIntent().getStringExtra(EXTRA_END_DATE);

        if (catName == null || start == null || end == null) {
            Toast.makeText(this, "Missing category or date range", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ExpenseCategory cat;
        try {
            cat = ExpenseCategory.valueOf(catName);
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, "Invalid category", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        textTitle.setText(cat.name() + " (" + start + " to " + end + ")");

        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"Date (Newest First)", "Amount (High to Low)", "Amount (Low to High)"}
        );
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategorySort.setAdapter(sortAdapter);

        spinnerCategorySort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applySortAndDisplay();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        loadCategoryExpenses(cat, start, end);

        buttonBack.setOnClickListener(v -> finish());
    }

    private void loadCategoryExpenses(ExpenseCategory cat, String start, String end) {
        catExpenses = expenseDao.getExpensesByCategory(cat, start, end);
        applySortAndDisplay();
    }

    private void applySortAndDisplay() {
        lines = new ArrayList<>();

        if (catExpenses == null || catExpenses.isEmpty()) {
            lines.add("No expenses found for this category in this range.");
        } else {
            List<Expense> sorted = new ArrayList<>(catExpenses);
            int pos = spinnerCategorySort.getSelectedItemPosition();

            if (pos == 0) {
                // Date (Newest First)
                sorted.sort((a, b) -> b.date.compareTo(a.date));
            } else if (pos == 1) {
                // Amount (High to Low)
                sorted.sort((a, b) -> Double.compare(b.amount, a.amount));
            } else if (pos == 2) {
                // Amount (Low to High)
                sorted.sort((a, b) -> Double.compare(a.amount, b.amount));
            }

            for (Expense e : sorted) {
                String title = e.title == null ? "" : e.title;
                String note = e.note == null ? "" : e.note;
                String line = e.date + " - " +
                        title + " - $" +
                        String.format(Locale.getDefault(), "%.2f", e.amount);

                if (!note.isEmpty()) {
                    line += "  [" + note + "]";
                }

                lines.add(line);
            }
        }

        adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                lines
        );
        listCategoryExpenses.setAdapter(adapter);
    }
}
