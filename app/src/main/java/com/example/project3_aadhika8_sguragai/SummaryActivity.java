package com.example.project3_aadhika8_sguragai;

import com.example.project3_aadhika8_sguragai.data.*;
import com.example.project3_aadhika8_sguragai.sense.OpenAIService;
import com.example.project3_aadhika8_sguragai.sense.search.QueryParser;
import com.example.project3_aadhika8_sguragai.sense.search.SearchFilter;
import com.example.project3_aadhika8_sguragai.sense.search.SearchFilterSqlBuilder;

import androidx.appcompat.app.AppCompatActivity;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class SummaryActivity extends AppCompatActivity {

    private AppDatabase db;
    private ExpenseDao expenseDao;

    private MaterialButton buttonBack;
    private MaterialButton buttonFromDate;
    private MaterialButton buttonToDate;
    private MaterialButton buttonPresetWeek;
    private MaterialButton buttonPresetMonth;
    private MaterialButton buttonPresetYear;
    private MaterialButton buttonPresetAllTime;
    private MaterialButton buttonShowSummary;
    private MaterialButton buttonShowExpenses;
    private MaterialButton buttonExportCSV;
    private MaterialButton buttonViewCSV;
    private TextView textTotalRange;
    private TextView textRemainingBudget;
    private TextView textHighestCategory;
    private LinearLayout layoutSearch;
    private EditText editSearch;
    private MaterialButton buttonSearch;
    private EditText editMonthlyBudget;
    private MaterialButton buttonSetBudget;

    private ListView listSummary;
    private ListView listExpenses;

    private ArrayList<String> summaryLines;
    private ArrayList<String> expenseLines;
    private ArrayAdapter<String> summaryAdapter;
    private ArrayAdapter<String> expensesAdapter;

    private List<Expense> expensesInRange;

    private Calendar fromCal;
    private Calendar toCal;
    private Spinner spinnerSort;
    private BudgetDao budgetDao;

    private SimpleDateFormat buttonFormat;
    private SimpleDateFormat queryFormat;

    private boolean isSummaryVisible = false;
    private boolean isExpensesVisible = false;

    private BottomNavigationView bottomNavigation;
    private OpenAIService openAIService;
    private TextView textSearchResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summary);

        db = AppDatabase.getInstance(getApplicationContext());
        expenseDao = db.expenseDao();
        budgetDao = db.budgetDao();
        openAIService = new OpenAIService(BuildConfig.OPENAI_API_KEY);

        buttonBack = findViewById(R.id.buttonBack);
        buttonFromDate = findViewById(R.id.buttonFromDate);
        buttonToDate = findViewById(R.id.buttonToDate);
        buttonPresetWeek = findViewById(R.id.buttonPresetWeek);
        buttonPresetMonth = findViewById(R.id.buttonPresetMonth);
        buttonPresetYear = findViewById(R.id.buttonPresetYear);
        buttonPresetAllTime = findViewById(R.id.buttonPresetAllTime);
        buttonShowSummary = findViewById(R.id.buttonShowSummary);
        buttonShowExpenses = findViewById(R.id.buttonShowExpenses);
        buttonExportCSV = findViewById(R.id.buttonExportCSV);
        buttonViewCSV = findViewById(R.id.buttonViewCSV);
        textTotalRange = findViewById(R.id.textTotalRange);

        layoutSearch = findViewById(R.id.layoutSearch);
        editSearch = findViewById(R.id.editSearch);
        buttonSearch = findViewById(R.id.buttonSearch);

        listSummary = findViewById(R.id.listSummary);
        listExpenses = findViewById(R.id.listExpenses);

        editMonthlyBudget = findViewById(R.id.editMonthlyBudget);
        buttonSetBudget = findViewById(R.id.buttonSetBudget);

        textRemainingBudget = findViewById(R.id.textRemainingBudget);
        textHighestCategory = findViewById(R.id.textHighestCategory);

        bottomNavigation = findViewById(R.id.bottomNavigation);
        setupBottomNavigation();

        spinnerSort = findViewById(R.id.spinnerSort);
        spinnerSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                loadExpensesForCurrentRange();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        listSummary.setOnItemClickListener((parent, view, position, id) -> {
            ExpenseCategory[] cats = ExpenseCategory.values();
            if (position < 0 || position >= cats.length) {
                return;
            }
            ExpenseCategory selectedCat = cats[position];

            String start = getFromDateString();
            String end = getToDateString();

            Intent intent = new Intent(SummaryActivity.this, CategoryExpensesActivity.class);
            intent.putExtra(CategoryExpensesActivity.EXTRA_CATEGORY, selectedCat.name());
            intent.putExtra(CategoryExpensesActivity.EXTRA_START_DATE, start);
            intent.putExtra(CategoryExpensesActivity.EXTRA_END_DATE, end);
            startActivity(intent);
        });

        buttonSetBudget.setOnClickListener(v -> {
            String input = editMonthlyBudget.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(SummaryActivity.this, "Enter a budget amount", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                double budget = Double.parseDouble(input);
                saveMonthlyBudget(budget);
                Toast.makeText(SummaryActivity.this, R.string.budget_updated, Toast.LENGTH_SHORT).show();
                updateBudgetWarning();
            } catch (NumberFormatException e) {
                Toast.makeText(SummaryActivity.this, "Invalid number format", Toast.LENGTH_SHORT).show();
            }
        });


        fromCal = Calendar.getInstance();
        toCal = Calendar.getInstance();

        buttonFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        queryFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        setMonthPreset();


        buttonFromDate.setOnClickListener(v -> showDatePicker(true));
        buttonToDate.setOnClickListener(v -> showDatePicker(false));

        buttonPresetWeek.setOnClickListener(v -> setWeekPreset());
        buttonPresetMonth.setOnClickListener(v -> setMonthPreset());
        buttonPresetYear.setOnClickListener(v -> setYearPreset());
        buttonPresetAllTime.setOnClickListener(v -> setAllTimePreset());

        buttonShowSummary.setOnClickListener(v -> toggleSummary());
        buttonShowExpenses.setOnClickListener(v -> toggleExpenses());
        buttonSearch.setOnClickListener(v -> applyNaturalLanguageSearch());
        buttonExportCSV.setOnClickListener(v -> exportCSV());

        if (buttonBack != null) {
            buttonBack.setOnClickListener(v -> finish());
        }

        buttonViewCSV.setOnClickListener(v -> {
            Intent intent = new Intent(SummaryActivity.this, CsvPreviewActivity.class);
            startActivity(intent);
        });

        listSummary.setVisibility(View.GONE);
        listExpenses.setVisibility(View.GONE);
    }

    private void setupBottomNavigation() {
        bottomNavigation.setSelectedItemId(R.id.nav_summary);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return true;
            } else if (id == R.id.nav_charts) {
                startActivity(new Intent(this, ChartsActivity.class));
                return true;
            } else if (id == R.id.nav_chat) {
                startActivity(new Intent(this, ChatActivity.class));
                return true;
            } else if (id == R.id.nav_summary) {
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        bottomNavigation.setSelectedItemId(R.id.nav_summary);
        updateBudgetWarning();
    }


    private void showDatePicker(boolean isFrom) {
        Calendar c = isFrom ? fromCal : toCal;

        int y = c.get(Calendar.YEAR);
        int m = c.get(Calendar.MONTH);
        int d = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (DatePicker view, int year, int month, int dayOfMonth) -> {
                    c.set(year, month, dayOfMonth);
                    updateDateButtons();
                },
                y, m, d
        );
        dialog.show();
    }

    private void updateDateButtons() {
        buttonFromDate.setText("From: " + buttonFormat.format(fromCal.getTime()));
        buttonToDate.setText("To: " + buttonFormat.format(toCal.getTime()));
        updateTotalForRange();
        refreshVisibleSections();
    }

    private String getFromDateString() {
        return queryFormat.format(fromCal.getTime());
    }

    private String getToDateString() {
        return queryFormat.format(toCal.getTime());
    }

    private void setWeekPreset() {
        toCal = Calendar.getInstance();
        fromCal = Calendar.getInstance();
        fromCal.add(Calendar.DAY_OF_MONTH, -6);
        updateDateButtons();
    }

    private void setMonthPreset() {
        toCal = Calendar.getInstance();
        fromCal = Calendar.getInstance();
        fromCal.set(Calendar.DAY_OF_MONTH, 1);
        updateDateButtons();
    }

    private void setYearPreset() {
        toCal = Calendar.getInstance();
        fromCal = Calendar.getInstance();
        fromCal.set(Calendar.MONTH, Calendar.JANUARY);
        fromCal.set(Calendar.DAY_OF_MONTH, 1);
        updateDateButtons();
    }

    private void setAllTimePreset() {
        toCal = Calendar.getInstance();
        fromCal = Calendar.getInstance();
        fromCal.set(2000, Calendar.JANUARY, 1);
        updateDateButtons();
    }


    private void toggleSummary() {
        if (isSummaryVisible) {
            listSummary.setVisibility(android.view.View.GONE);
            isSummaryVisible = false;
        } else {
            loadSummaryForCurrentRange();
            listSummary.setVisibility(android.view.View.VISIBLE);
            isSummaryVisible = true;
        }
    }

    private void toggleExpenses() {
        if (isExpensesVisible) {
            listExpenses.setVisibility(android.view.View.GONE);
            layoutSearch.setVisibility(android.view.View.GONE);
            spinnerSort.setVisibility(android.view.View.GONE);
            isExpensesVisible = false;
        } else {
            loadExpensesForCurrentRange();
            listExpenses.setVisibility(android.view.View.VISIBLE);
            layoutSearch.setVisibility(android.view.View.VISIBLE);
            spinnerSort.setVisibility(android.view.View.VISIBLE);
            isExpensesVisible = true;
        }
    }
    private void updateTotalForRange() {
        String start = getFromDateString();
        String end = getToDateString();

        if (start.compareTo(end) > 0) {
            textTotalRange.setText("Total: $0.00");
            return;
        }

        double total = expenseDao.getTotalForRange(start, end);
        textTotalRange.setText(String.format(Locale.getDefault(), "Total: $%.2f", total));
        
        // Update highest category
        updateHighestCategory(start, end);
    }



    private void loadSummaryForCurrentRange() {
        String start = getFromDateString();
        String end = getToDateString();

        if (start.compareTo(end) > 0) {
            Toast.makeText(this, "From date must be before To date", Toast.LENGTH_SHORT).show();
            return;
        }

        summaryLines = new ArrayList<>();

        for (ExpenseCategory cat : ExpenseCategory.values()) {
            double total = expenseDao.getTotalByCategory(cat, start, end);
            summaryLines.add(cat.name() + ": $" +
                    String.format(Locale.getDefault(), "%.2f", total));
        }

        summaryAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                summaryLines
        );

        listSummary.setAdapter(summaryAdapter);
    }


    private void loadExpensesForCurrentRange() {
        String start = getFromDateString();
        String end = getToDateString();

        if (start.compareTo(end) > 0) {
            Toast.makeText(this, "From date must be before To date", Toast.LENGTH_SHORT).show();
            return;
        }

        expensesInRange = expenseDao.getExpensesInRange(start, end);

        int sortPos = spinnerSort.getSelectedItemPosition();
        if (sortPos == 0) {
            // Date (Newest First)
            expensesInRange.sort((a, b) -> b.date.compareTo(a.date));
        } else if (sortPos == 1) {
            // Amount (High to Low)
            expensesInRange.sort((a, b) -> Double.compare(b.amount, a.amount));
        } else if (sortPos == 2) {
            // Amount (Low to High)
            expensesInRange.sort((a, b) -> Double.compare(a.amount, b.amount));
        } else if (sortPos == 3) {
            // Category
            expensesInRange.sort((a, b) -> {
                String c1 = (a.category == null ? "" : a.category.name());
                String c2 = (b.category == null ? "" : b.category.name());
                return c1.compareTo(c2);
            });
        }

        expenseLines = new ArrayList<>();
        for (Expense e : expensesInRange) {
            String line = e.date + " - " + e.title + " - $" +
                    String.format(Locale.getDefault(), "%.2f", e.amount) +
                    " (" + (e.category == null ? "" : e.category.name()) + ")";
            expenseLines.add(line);
        }

        expensesAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                expenseLines
        );

        listExpenses.setAdapter(expensesAdapter);
    }

    private void applySearchFilter() {
        if (expensesInRange == null) {
            return;
        }

        String query = editSearch.getText().toString().trim().toLowerCase(Locale.getDefault());

        ArrayList<String> filtered = new ArrayList<>();

        for (Expense e : expensesInRange) {
            String title = e.title == null ? "" : e.title;
            String catName = e.category == null ? "" : e.category.name();
            String combined = title.toLowerCase(Locale.getDefault()) + " " +
                    catName.toLowerCase(Locale.getDefault());

            if (query.isEmpty() || combined.contains(query)) {
                String line = e.date + " - " + title + " - $" +
                        String.format(Locale.getDefault(), "%.2f", e.amount) +
                        " (" + catName + ")";
                filtered.add(line);
            }
        }

        expensesAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                filtered
        );
        listExpenses.setAdapter(expensesAdapter);
    }

    /**
     * Plain-language search, run entirely on the device.
     *
     * <p>This used to ship the whole expense history to OpenAI and show back a paragraph.
     * Now the query compiles to a typed SearchFilter, that compiles to parameterised SQL, and
     * the rows come from Room. Nothing about the user's spending leaves the phone.
     */
    private void applyNaturalLanguageSearch() {
        String queryText = editSearch.getText().toString().trim();
        if (queryText.isEmpty()) {
            loadExpensesForCurrentRange();
            return;
        }
        performLocalSearch(queryText);
    }

    private void performLocalSearch(String queryText) {
        SearchFilter filter = new QueryParser().parse(queryText);

        if (filter.isEmpty()) {
            Toast.makeText(this, "Could not understand that query", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Expense> results = expenseDao.search(SearchFilterSqlBuilder.build(filter));

        ArrayList<String> filtered = new ArrayList<>();
        for (Expense e : results) {
            String title = e.title == null ? "" : e.title;
            String catName = e.category == null ? "" : e.category.name();
            filtered.add(e.date + " - " + title + " - $"
                    + String.format(Locale.getDefault(), "%.2f", e.amount)
                    + " (" + catName + ")");
        }

        if (!isExpensesVisible) {
            listExpenses.setVisibility(View.VISIBLE);
            spinnerSort.setVisibility(View.VISIBLE);
            isExpensesVisible = true;
        }

        expensesAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                filtered
        );
        listExpenses.setAdapter(expensesAdapter);

        StringBuilder chips = new StringBuilder();
        for (SearchFilter.Chip c : filter.toChips()) {
            if (chips.length() > 0) chips.append(" · ");
            chips.append(c.label);
        }
        Toast.makeText(this, filtered.size() + " found — " + chips, Toast.LENGTH_SHORT).show();
    }


    private void exportCSV() {
        try {
            List<Expense> allExpenses = expenseDao.getAllExpenses();

            if (allExpenses == null || allExpenses.isEmpty()) {
                Toast.makeText(this, "No expenses to export", Toast.LENGTH_SHORT).show();
                return;
            }

            File csv = new File(getExternalFilesDir(null), "expenses_full.csv");
            FileWriter writer = new FileWriter(csv);

            writer.write("Title,Amount,Category,Date,Note\n");

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

                writer.write("\"" + title + "\"," +
                        "\"" + amount + "\"," +
                        "\"" + category + "\"," +
                        "\"" + date + "\"," +
                        "\"" + note + "\"\n");
            }

            writer.close();

            Toast.makeText(this,
                    "Exported to " + csv.getAbsolutePath(),
                    Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(this, "Error exporting CSV", Toast.LENGTH_SHORT).show();
        }
    }
    private void refreshVisibleSections() {
        String start = getFromDateString();
        String end = getToDateString();

        if (isSummaryVisible) {
            loadSummaryForCurrentRange();
            updateHighestCategory(start, end);
        }
        if (isExpensesVisible) {
            loadExpensesForCurrentRange();
        }
        updateBudgetWarning();
    }
    private void updateBudgetWarning() {
        double budget = getMonthlyBudget();
        Calendar now = Calendar.getInstance();
        now.set(Calendar.DAY_OF_MONTH, 1);
        String monthStart = queryFormat.format(now.getTime());
        now.set(Calendar.DAY_OF_MONTH, now.getActualMaximum(Calendar.DAY_OF_MONTH));
        String monthEnd = queryFormat.format(now.getTime());

        double totalThisMonth = expenseDao.getTotalForRange(monthStart, monthEnd);
        double remaining = budget - totalThisMonth;

        textRemainingBudget.setText(String.format(Locale.getDefault(), "Remaining: $%.2f", Math.max(0, remaining)));
        textRemainingBudget.setVisibility(View.VISIBLE);

        if (totalThisMonth > budget) {
            textRemainingBudget.setTextColor(getColor(R.color.error));
        } else if (remaining <= 200) {
            textRemainingBudget.setTextColor(getColor(R.color.warning));
        } else {
            textRemainingBudget.setTextColor(getColor(R.color.on_primary));
        }
    }

    private double getMonthlyBudget() {
        Double amount = budgetDao.getBudget();
        if (amount == null) {
            Budget b = new Budget();
            b.amount = 1500.0;
            budgetDao.insert(b);
            return 1500.0;
        }
        return amount;
    }

    private void saveMonthlyBudget(double amount) {
        Budget b = new Budget();
        b.amount = amount;
        budgetDao.insert(b);
    }

    private void updateHighestCategory(String start, String end) {
        ExpenseCategory highestCategory = null;
        double maxTotal = 0;

        for (ExpenseCategory cat : ExpenseCategory.values()) {
            double total = expenseDao.getTotalByCategory(cat, start, end);
            if (total > maxTotal) {
                maxTotal = total;
                highestCategory = cat;
            }
        }

        if (highestCategory != null && maxTotal > 0) {
            textHighestCategory.setText("Highest Spending Category: "
                    + highestCategory.name()
                    + " ($" + String.format(Locale.getDefault(), "%.2f", maxTotal) + ")");
            textHighestCategory.setVisibility(View.VISIBLE);
        } else {
            textHighestCategory.setText("Highest Spending Category: None");
            textHighestCategory.setVisibility(View.GONE);
        }
    }

}
