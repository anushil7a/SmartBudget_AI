package com.example.project3_aadhika8_sguragai.ui.home;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.project3_aadhika8_sguragai.data.Expense;
import com.example.project3_aadhika8_sguragai.data.ExpenseCategory;
import com.example.project3_aadhika8_sguragai.data.ExpenseRepository;
import com.example.project3_aadhika8_sguragai.sense.Sense;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything the Home screen needs, and nothing about how it looks.
 *
 * <p>The Activity used to hold a DAO and call it inline on the main thread. All of that lives
 * here now, behind {@link ExpenseRepository}, which is what makes it safe to drop
 * {@code allowMainThreadQueries()}.
 */
public class HomeViewModel extends AndroidViewModel {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ExpenseRepository repo;

    private final MutableLiveData<LocalDate> month = new MutableLiveData<>();

    private final LiveData<List<Expense>> monthExpenses;
    private final LiveData<List<Expense>> needsReview;
    private final LiveData<Double> budget;
    private final MediatorLiveData<Double> spent = new MediatorLiveData<>();

    public HomeViewModel(@NonNull Application application) {
        super(application);
        repo = ExpenseRepository.get(application);
        month.setValue(LocalDate.now().withDayOfMonth(1));

        monthExpenses = Transformations.switchMap(month, m ->
                repo.observeRange(m.format(ISO), m.plusMonths(1).minusDays(1).format(ISO)));

        needsReview = repo.observeNeedsReview(Sense.REVIEW_THRESHOLD);
        budget = repo.observeBudget();

        // Total is derived from the rows already being observed, rather than a second query.
        spent.addSource(monthExpenses, list -> spent.setValue(sum(list)));
    }

    private double sum(List<Expense> list) {
        double total = 0;
        if (list != null) {
            for (Expense e : list) {
                total += e.amount;
            }
        }
        return total;
    }

    public LiveData<LocalDate> month() {
        return month;
    }

    public LiveData<List<Expense>> expenses() {
        return monthExpenses;
    }

    /** Rows the classifier was not confident about — the "needs review" strip. */
    public LiveData<List<Expense>> needsReview() {
        return needsReview;
    }

    public LiveData<Double> spent() {
        return spent;
    }

    public LiveData<Double> budget() {
        return budget;
    }

    public void previousMonth() {
        LocalDate m = month.getValue();
        if (m != null) {
            month.setValue(m.minusMonths(1));
        }
    }

    public void nextMonth() {
        LocalDate m = month.getValue();
        if (m != null) {
            month.setValue(m.plusMonths(1));
        }
    }

    /** Daily totals across the visible month, for the sparkline. */
    public List<Double> dailyTotals() {
        List<Expense> list = monthExpenses.getValue();
        LocalDate m = month.getValue();
        if (list == null || m == null) {
            return Collections.emptyList();
        }
        int days = m.lengthOfMonth();
        List<Double> totals = new ArrayList<>(Collections.nCopies(days, 0d));
        for (Expense e : list) {
            try {
                int day = LocalDate.parse(e.date).getDayOfMonth();
                totals.set(day - 1, totals.get(day - 1) + e.amount);
            } catch (Exception ignored) {
                // a malformed date should not take the chart down
            }
        }
        return totals;
    }

    /**
     * Correcting a category runs unlearn(old) then learn(new), so the classifier stops
     * crediting the category the user just rejected.
     */
    public void recategorise(Expense expense, ExpenseCategory to) {
        ExpenseCategory from = expense.category;
        String merchant = expense.merchant != null ? expense.merchant : expense.title;

        expense.category = to;
        expense.categorySource = com.example.project3_aadhika8_sguragai.data.CategorySource.USER;
        expense.predictionConfidence = null;   // a human chose it; there is no confidence

        repo.update(expense, null);
        repo.correctCategory(merchant, from, to);
    }

    public void setBudget(double amount) {
        repo.setBudget(amount);
    }

    public void delete(Expense expense) {
        repo.delete(expense, null);
    }
}
