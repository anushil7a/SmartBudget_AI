package com.example.project3_aadhika8_sguragai;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements ExpenseAdapter.OnExpenseClickListener {

    // Budget Overview Card
    private TextView textMonthlyBudgetHome;
    private TextView textSpent;
    private TextView textRemaining;
    private LinearProgressIndicator progressBudget;
    private TextView textBudgetWarningHome;

    // Daily Summary Card
    private MaterialButton buttonSelectDate;
    private MaterialButton buttonPrevDay;
    private MaterialButton buttonNextDay;
    private TextView textTotalAmount;

    // Streak & Badges Card
    private TextView textStreakStatus;
    private ImageView imageStreakBadge;
    private ImageView badgeToday, badgeYesterday, badgeTwoDaysAgo;
    private TextView textBadgesThisMonth;

    // Transactions
    private RecyclerView recyclerTransactions;
    private LinearLayout layoutEmptyState;
    private ExpenseAdapter expenseAdapter;

    // Navigation
    private FloatingActionButton fabAdd;
    private BottomNavigationView bottomNavigation;

    // Data
    private Calendar calendar;
    private AppDatabase db;
    private ExpenseDao expenseDao;
    private BudgetDao budgetDao;
    private List<Expense> expenses;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initDatabase();
        setupRecyclerView();
        setupClickListeners();
        setupBottomNavigation();

        calendar = Calendar.getInstance();
        updateDateButtonText();
        loadDataForSelectedDate();
    }

    private void initViews() {
        // Budget Overview Card
        textMonthlyBudgetHome = findViewById(R.id.textMonthlyBudgetHome);
        textSpent = findViewById(R.id.textSpent);
        textRemaining = findViewById(R.id.textRemaining);
        progressBudget = findViewById(R.id.progressBudget);
        textBudgetWarningHome = findViewById(R.id.textBudgetWarningHome);

        // Daily Summary Card
        buttonSelectDate = findViewById(R.id.buttonSelectDate);
        buttonPrevDay = findViewById(R.id.buttonPrevDay);
        buttonNextDay = findViewById(R.id.buttonNextDay);
        textTotalAmount = findViewById(R.id.textTotalAmount);

        // Streak & Badges Card
        textStreakStatus = findViewById(R.id.textStreakStatus);
        imageStreakBadge = findViewById(R.id.imageStreakBadge);
        badgeToday = findViewById(R.id.badgeToday);
        badgeYesterday = findViewById(R.id.badgeYesterday);
        badgeTwoDaysAgo = findViewById(R.id.badgeTwoDaysAgo);
        textBadgesThisMonth = findViewById(R.id.textBadgesThisMonth);

        // Transactions
        recyclerTransactions = findViewById(R.id.recyclerTransactions);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);

        // Navigation
        fabAdd = findViewById(R.id.fabAdd);
        bottomNavigation = findViewById(R.id.bottomNavigation);
    }

    private void initDatabase() {
        db = AppDatabase.getInstance(getApplicationContext());
        expenseDao = db.expenseDao();
        budgetDao = db.budgetDao();
    }

    private void setupRecyclerView() {
        expenseAdapter = new ExpenseAdapter(this);
        recyclerTransactions.setLayoutManager(new LinearLayoutManager(this));
        recyclerTransactions.setAdapter(expenseAdapter);
    }

    private void setupClickListeners() {
        buttonSelectDate.setOnClickListener(v -> showDatePicker());

        buttonPrevDay.setOnClickListener(v -> {
            calendar.add(Calendar.DAY_OF_MONTH, -1);
            updateDateButtonText();
            loadDataForSelectedDate();
        });

        buttonNextDay.setOnClickListener(v -> {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            updateDateButtonText();
            loadDataForSelectedDate();
        });

        fabAdd.setOnClickListener(v -> showAddExpenseOptions());
    }

    private void setupBottomNavigation() {
        bottomNavigation.setSelectedItemId(R.id.nav_home);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                return true;
            } else if (id == R.id.nav_charts) {
                startActivity(new Intent(this, ChartsActivity.class));
                return true;
            } else if (id == R.id.nav_chat) {
                startActivity(new Intent(this, ChatActivity.class));
                return true;
            } else if (id == R.id.nav_summary) {
                startActivity(new Intent(this, SummaryActivity.class));
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        bottomNavigation.setSelectedItemId(R.id.nav_home);
        loadDataForSelectedDate();
    }

    @Override
    public void onExpenseClick(Expense expense) {
        showExpenseBottomSheet(expense);
    }

    private void showAddExpenseOptions() {
        String[] options = {"Add Manually", "Scan Receipt"};
        new AlertDialog.Builder(this)
                .setTitle("Add Expense")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showExpenseBottomSheet(null);
                    } else {
                        startActivity(new Intent(this, ReceiptScanActivity.class));
                    }
                })
                .show();
    }

    private void showDatePicker() {
        int y = calendar.get(Calendar.YEAR);
        int m = calendar.get(Calendar.MONTH);
        int d = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    updateDateButtonText();
                    loadDataForSelectedDate();
                },
                y, m, d
        );
        dialog.show();
    }

    private void updateDateButtonText() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        buttonSelectDate.setText(sdf.format(calendar.getTime()));
    }

    private String getSelectedDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(calendar.getTime());
    }

    private void loadDataForSelectedDate() {
        String day = getSelectedDateString();

        // Daily total
        double total = expenseDao.getTotalForDay(day);
        textTotalAmount.setText(String.format(Locale.getDefault(), "$%.2f", total));

        // Load expenses for RecyclerView
        expenses = expenseDao.getExpensesForDay(day);
        expenseAdapter.setExpenses(expenses);

        // Toggle empty state
        if (expenses == null || expenses.isEmpty()) {
            recyclerTransactions.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            recyclerTransactions.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
        }

        updateBadges();
        updateMonthlyBudgetStatus();
        updateMonthlyBadgeCount();
        updateStreakStatus();
    }

    private void showExpenseBottomSheet(Expense expenseToEdit) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this, R.style.ThemeOverlay_App_BottomSheetDialog);
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_expense, null);
        bottomSheet.setContentView(sheetView);

        // Get views
        TextView textSheetTitle = sheetView.findViewById(R.id.textSheetTitle);
        TextInputEditText editTitle = sheetView.findViewById(R.id.editTitle);
        TextInputEditText editAmount = sheetView.findViewById(R.id.editAmount);
        MaterialButton buttonDate = sheetView.findViewById(R.id.buttonDate);
        MaterialAutoCompleteTextView dropdownCategory = sheetView.findViewById(R.id.dropdownCategory);
        TextInputEditText editNote = sheetView.findViewById(R.id.editNote);
        MaterialButton buttonDelete = sheetView.findViewById(R.id.buttonDelete);
        MaterialButton buttonCancel = sheetView.findViewById(R.id.buttonCancel);
        MaterialButton buttonSave = sheetView.findViewById(R.id.buttonSave);

        // Setup category dropdown
        ExpenseCategory[] cats = ExpenseCategory.values();
        String[] categoryNames = Arrays.stream(cats)
                .map(c -> formatCategoryName(c.name()))
                .toArray(String[]::new);
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, categoryNames);
        dropdownCategory.setAdapter(catAdapter);

        // Setup date
        Calendar tempCal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat displayFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

        if (expenseToEdit != null) {
            textSheetTitle.setText(R.string.edit_expense);
            editTitle.setText(expenseToEdit.title);
            editAmount.setText(String.valueOf(expenseToEdit.amount));
            editNote.setText(expenseToEdit.note);
            dropdownCategory.setText(formatCategoryName(expenseToEdit.category.name()), false);
            try {
                tempCal.setTime(sdf.parse(expenseToEdit.date));
            } catch (Exception ignored) {}
            buttonDelete.setVisibility(View.VISIBLE);
        } else {
            textSheetTitle.setText(R.string.add_expense);
            try {
                tempCal.setTime(sdf.parse(getSelectedDateString()));
            } catch (Exception ignored) {}
            dropdownCategory.setText(categoryNames[0], false);
        }

        buttonDate.setText(displayFormat.format(tempCal.getTime()));

        // Date picker
        buttonDate.setOnClickListener(v -> {
            new DatePickerDialog(this,
                    (view, year, month, day) -> {
                        tempCal.set(year, month, day);
                        buttonDate.setText(displayFormat.format(tempCal.getTime()));
                    },
                    tempCal.get(Calendar.YEAR),
                    tempCal.get(Calendar.MONTH),
                    tempCal.get(Calendar.DAY_OF_MONTH)
            ).show();
        });

        // Cancel
        buttonCancel.setOnClickListener(v -> bottomSheet.dismiss());

        // Delete
        buttonDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete Expense")
                    .setMessage("Are you sure you want to delete this expense?")
                    .setPositiveButton("Delete", (d, w) -> {
                        expenseDao.delete(expenseToEdit);
                        Toast.makeText(this, R.string.expense_deleted, Toast.LENGTH_SHORT).show();
                        bottomSheet.dismiss();
                        loadDataForSelectedDate();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        // Save
        buttonSave.setOnClickListener(v -> {
            String title = editTitle.getText().toString().trim();
            String amtStr = editAmount.getText().toString().trim();
            String note = editNote.getText().toString().trim();
            String chosenDate = sdf.format(tempCal.getTime());

            if (title.isEmpty()) {
                editTitle.setError(getString(R.string.error_title_required));
                return;
            }
            if (amtStr.isEmpty()) {
                editAmount.setError(getString(R.string.error_amount_required));
                return;
            }

            double amount;
            try {
                amount = Double.parseDouble(amtStr);
            } catch (NumberFormatException e) {
                editAmount.setError(getString(R.string.error_invalid_amount));
                return;
            }

            // Find selected category
            String selectedCatName = dropdownCategory.getText().toString();
            ExpenseCategory cat = cats[0];
            for (ExpenseCategory c : cats) {
                if (formatCategoryName(c.name()).equals(selectedCatName)) {
                    cat = c;
                    break;
                }
            }

            if (expenseToEdit == null) {
                Expense e = new Expense();
                e.title = title;
                e.amount = amount;
                e.category = cat;
                e.note = note;
                e.date = chosenDate;
                expenseDao.insert(e);
                Toast.makeText(this, R.string.expense_added, Toast.LENGTH_SHORT).show();
            } else {
                expenseToEdit.title = title;
                expenseToEdit.amount = amount;
                expenseToEdit.category = cat;
                expenseToEdit.note = note;
                expenseToEdit.date = chosenDate;
                expenseDao.update(expenseToEdit);
                Toast.makeText(this, R.string.expense_updated, Toast.LENGTH_SHORT).show();
            }

            bottomSheet.dismiss();
            loadDataForSelectedDate();
        });

        bottomSheet.show();
    }

    private String formatCategoryName(String name) {
        if (name == null || name.isEmpty()) return "";
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.getDefault());
    }

    private void updateBadges() {
        String selectedDay = getSelectedDateString();
        double totalForSelectedDay = expenseDao.getTotalForDay(selectedDay);

        badgeToday.setImageResource(
                totalForSelectedDay == 0 ? R.drawable.unlocked : R.drawable.locked
        );

        badgeYesterday.setVisibility(View.GONE);
        badgeTwoDaysAgo.setVisibility(View.GONE);
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

    private void updateMonthlyBudgetStatus() {
        double budget = getMonthlyBudget();

        Calendar now = Calendar.getInstance();
        now.set(Calendar.DAY_OF_MONTH, 1);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String monthStart = sdf.format(now.getTime());
        now.set(Calendar.DAY_OF_MONTH, now.getActualMaximum(Calendar.DAY_OF_MONTH));
        String monthEnd = sdf.format(now.getTime());

        double totalThisMonth = expenseDao.getTotalForRange(monthStart, monthEnd);
        double remaining = budget - totalThisMonth;

        // Update budget card
        textMonthlyBudgetHome.setText(String.format(Locale.getDefault(), "$%.2f", budget));
        textSpent.setText(String.format(Locale.getDefault(), "$%.2f", totalThisMonth));
        textRemaining.setText(String.format(Locale.getDefault(), "$%.2f", Math.max(0, remaining)));

        // Update progress bar
        int progress = budget > 0 ? (int) ((totalThisMonth / budget) * 100) : 0;
        progressBudget.setProgress(Math.min(progress, 100));

        // Warning states
        if (totalThisMonth > budget) {
            textBudgetWarningHome.setText(R.string.budget_exceeded);
            textBudgetWarningHome.setBackgroundResource(R.drawable.warning_badge_background);
            textBudgetWarningHome.setVisibility(View.VISIBLE);
        } else if (remaining <= 200) {
            textBudgetWarningHome.setText(R.string.budget_warning);
            textBudgetWarningHome.setBackgroundResource(R.drawable.warning_badge_background);
            textBudgetWarningHome.setVisibility(View.VISIBLE);
        } else {
            textBudgetWarningHome.setVisibility(View.GONE);
        }
    }

    private void updateMonthlyBadgeCount() {
        Calendar now = Calendar.getInstance();
        int currentYear = now.get(Calendar.YEAR);
        int currentMonth = now.get(Calendar.MONTH);

        Calendar cursor = Calendar.getInstance();
        cursor.set(Calendar.YEAR, currentYear);
        cursor.set(Calendar.MONTH, currentMonth);
        cursor.set(Calendar.DAY_OF_MONTH, 1);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        int badges = 0;

        while (!cursor.after(now)) {
            String dayStr = sdf.format(cursor.getTime());
            double total = expenseDao.getTotalForDay(dayStr);
            if (total == 0) {
                badges++;
            }
            cursor.add(Calendar.DAY_OF_MONTH, 1);
        }

        textBadgesThisMonth.setText(badges + " badges this month");
    }

    private void updateStreakStatus() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar base = Calendar.getInstance();
        try {
            base.setTime(sdf.parse(getSelectedDateString()));
        } catch (Exception ignored) { }

        Calendar cursor = (Calendar) base.clone();

        int streak = 0;
        int maxDaysLookback = 365;
        while (maxDaysLookback-- > 0) {
            String dayStr = sdf.format(cursor.getTime());
            double total = expenseDao.getTotalForDay(dayStr);
            if (total == 0) {
                streak++;
            } else {
                break;
            }
            cursor.add(Calendar.DAY_OF_MONTH, -1);
        }

        if (streak <= 0) {
            textStreakStatus.setText("0 day streak");
            imageStreakBadge.setVisibility(View.GONE);
            return;
        }

        String text = streak + " day streak";
        int badgeResId = 0;

        if (streak >= 30) {
            text += " (Gold)";
            badgeResId = R.drawable.gold;
        } else if (streak >= 7) {
            text += " (Silver)";
            badgeResId = R.drawable.silver;
        } else if (streak >= 3) {
            text += " (Bronze)";
            badgeResId = R.drawable.bronze;
        }

        textStreakStatus.setText(text);

        if (badgeResId != 0) {
            imageStreakBadge.setImageResource(badgeResId);
            imageStreakBadge.setVisibility(View.VISIBLE);
        } else {
            imageStreakBadge.setVisibility(View.GONE);
        }
    }
}
