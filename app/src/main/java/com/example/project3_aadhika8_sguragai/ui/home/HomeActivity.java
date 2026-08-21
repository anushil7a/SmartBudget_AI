package com.example.project3_aadhika8_sguragai.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.project3_aadhika8_sguragai.ChartsActivity;
import com.example.project3_aadhika8_sguragai.R;
import com.example.project3_aadhika8_sguragai.ReceiptScanActivity;
import com.example.project3_aadhika8_sguragai.SummaryActivity;
import com.example.project3_aadhika8_sguragai.data.Expense;
import com.example.project3_aadhika8_sguragai.data.ExpenseCategory;
import com.example.project3_aadhika8_sguragai.ui.widget.BudgetMeterView;
import com.example.project3_aadhika8_sguragai.ui.widget.SparklineView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The month at a glance: what has been spent, what the classifier is unsure about, and the
 * feed.
 *
 * <p>This screen holds no data-access logic. Everything comes from {@link HomeViewModel} as
 * LiveData; the Activity's whole job is binding.
 */
public class HomeActivity extends AppCompatActivity {

    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);

    private HomeViewModel model;

    private TextView textMonth;
    private TextView textSpent;
    private TextView textBudgetCaption;
    private TextView textNeedsReviewHeader;
    private TextView textEmpty;
    private View layoutNeedsReview;
    private BudgetMeterView budgetMeter;
    private SparklineView sparkline;

    private ExpenseAdapter feedAdapter;
    private ExpenseAdapter reviewAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        model = new ViewModelProvider(this).get(HomeViewModel.class);

        bindViews();
        setupLists();
        setupNavigation();
        observeModel();
    }

    private void bindViews() {
        textMonth = findViewById(R.id.textMonth);
        textSpent = findViewById(R.id.textSpent);
        textBudgetCaption = findViewById(R.id.textBudgetCaption);
        textNeedsReviewHeader = findViewById(R.id.textNeedsReviewHeader);
        textEmpty = findViewById(R.id.textEmpty);
        layoutNeedsReview = findViewById(R.id.layoutNeedsReview);
        budgetMeter = findViewById(R.id.budgetMeter);
        sparkline = findViewById(R.id.sparkline);

        findViewById(R.id.buttonPrevMonth).setOnClickListener(v -> model.previousMonth());
        findViewById(R.id.buttonNextMonth).setOnClickListener(v -> model.nextMonth());

        FloatingActionButton fab = findViewById(R.id.fabAdd);
        fab.setOnClickListener(v ->
                startActivity(new Intent(this, ReceiptScanActivity.class)));

        textBudgetCaption.setOnClickListener(v -> promptForBudget());
    }

    private void setupLists() {
        ExpenseAdapter.Listener listener = new ExpenseAdapter.Listener() {
            @Override
            public void onClick(Expense expense) {
                promptForCategory(expense);
            }

            @Override
            public void onRecategorise(Expense expense) {
                promptForCategory(expense);
            }
        };

        feedAdapter = new ExpenseAdapter(listener);
        reviewAdapter = new ExpenseAdapter(listener);

        RecyclerView feed = findViewById(R.id.listExpenses);
        feed.setLayoutManager(new LinearLayoutManager(this));
        feed.setAdapter(feedAdapter);

        RecyclerView review = findViewById(R.id.listNeedsReview);
        review.setLayoutManager(new LinearLayoutManager(this));
        review.setAdapter(reviewAdapter);
    }

    private void setupNavigation() {
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_home);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                return true;
            }
            if (id == R.id.nav_scan) {
                startActivity(new Intent(this, ReceiptScanActivity.class));
                return true;
            }
            if (id == R.id.nav_search) {
                startActivity(new Intent(this, SummaryActivity.class));
                return true;
            }
            if (id == R.id.nav_insights) {
                startActivity(new Intent(this, ChartsActivity.class));
                return true;
            }
            return false;
        });
    }

    private void observeModel() {
        model.month().observe(this, m ->
                textMonth.setText(m.format(MONTH_LABEL).toUpperCase(Locale.US)));

        model.expenses().observe(this, list -> {
            feedAdapter.submitList(list);
            boolean empty = list == null || list.isEmpty();
            textEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            sparkline.setPoints(model.dailyTotals());
        });

        model.needsReview().observe(this, list -> {
            boolean any = list != null && !list.isEmpty();
            layoutNeedsReview.setVisibility(any ? View.VISIBLE : View.GONE);
            reviewAdapter.submitList(list);
            if (any) {
                textNeedsReviewHeader.setText(
                        getString(R.string.needs_review_count, list.size()));
            }
        });

        model.spent().observe(this, spent -> {
            double value = spent == null ? 0 : spent;
            textSpent.setText(String.format(Locale.US, "$%,.2f", value));
            updateMeter();
        });

        model.budget().observe(this, b -> updateMeter());
    }

    private void updateMeter() {
        Double spent = model.spent().getValue();
        Double budget = model.budget().getValue();
        double s = spent == null ? 0 : spent;
        double b = budget == null ? 0 : budget;

        budgetMeter.setValue(s, b);

        if (b <= 0) {
            textBudgetCaption.setText(R.string.set_a_budget);
            return;
        }
        double left = b - s;
        if (left >= 0) {
            textBudgetCaption.setText(getString(R.string.budget_caption,
                    String.format(Locale.US, "%,.0f", b),
                    String.format(Locale.US, "%,.0f", left)));
        } else {
            textBudgetCaption.setText(getString(R.string.budget_caption_over,
                    String.format(Locale.US, "%,.0f", b),
                    String.format(Locale.US, "%,.0f", -left)));
            textBudgetCaption.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        }
    }

    /**
     * Correcting a category here is the visible half of the learning loop: it writes the row
     * and tells the classifier to move its counts.
     */
    private void promptForCategory(@NonNull Expense expense) {
        ExpenseCategory[] categories = ExpenseCategory.values();
        String[] labels = new String[categories.length];
        for (int i = 0; i < categories.length; i++) {
            labels[i] = categories[i].name();
        }

        String name = expense.merchant != null ? expense.merchant : expense.title;
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(labels, (dialog, which) -> {
                    ExpenseCategory chosen = categories[which];
                    if (chosen != expense.category) {
                        model.recategorise(expense, chosen);
                        findViewById(R.id.listExpenses)
                                .performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void promptForBudget() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        Double current = model.budget().getValue();
        if (current != null) {
            input.setText(String.format(Locale.US, "%.0f", current));
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.monthly_budget)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    try {
                        model.setBudget(Double.parseDouble(input.getText().toString()));
                    } catch (NumberFormatException ignored) {
                        // leave the budget as it was
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
