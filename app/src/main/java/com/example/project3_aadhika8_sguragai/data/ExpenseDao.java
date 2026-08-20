package com.example.project3_aadhika8_sguragai.data;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Update;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.lifecycle.LiveData;
import androidx.sqlite.db.SupportSQLiteQuery;

import java.util.List;

@Dao
public interface ExpenseDao {

    @Insert
    long insert(Expense e);

    @Update
    int update(Expense e);

    @Delete
    int delete(Expense e);

    @Query("SELECT * FROM expenses WHERE date = :day ORDER BY id DESC")
    List<Expense> getExpensesForDay(String day);

    @Query("SELECT IFNULL(SUM(amount), 0) FROM expenses WHERE date = :day")
    double getTotalForDay(String day);

    @Query("SELECT * FROM expenses " +
            "WHERE category = :cat AND date BETWEEN :start AND :end " +
            "ORDER BY date DESC")
    List<Expense> getExpensesByCategory(ExpenseCategory cat, String start, String end);

    @Query("SELECT IFNULL(SUM(amount), 0) FROM expenses " +
            "WHERE category = :cat AND date BETWEEN :start AND :end")
    double getTotalByCategory(ExpenseCategory cat, String start, String end);

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    Expense getExpenseById(long id);

    @Query("SELECT * FROM expenses ORDER BY date DESC, id DESC")
    List<Expense> getAllExpenses();

    @Query("SELECT * FROM expenses WHERE date BETWEEN :start AND :end ORDER BY date DESC, id DESC")
    List<Expense> getExpensesInRange(String start, String end);

    @Query("SELECT IFNULL(SUM(amount), 0) FROM expenses WHERE date BETWEEN :start AND :end")
    double getTotalForRange(String start, String end);

    // --- Observable reads. The repository hands these straight to ViewModels; Room
    // --- runs them on its own query executor, so nothing blocks the main thread.

    @Query("SELECT * FROM expenses ORDER BY date DESC, id DESC")
    LiveData<List<Expense>> observeAllExpenses();

    @Query("SELECT * FROM expenses WHERE date BETWEEN :start AND :end ORDER BY date DESC, id DESC")
    LiveData<List<Expense>> observeExpensesInRange(String start, String end);

    @Query("SELECT * FROM expenses WHERE predictionConfidence IS NOT NULL "
            + "AND predictionConfidence < :threshold ORDER BY date DESC, id DESC")
    LiveData<List<Expense>> observeNeedsReview(float threshold);

    @Query("SELECT IFNULL(SUM(amount), 0) FROM expenses WHERE date BETWEEN :start AND :end")
    LiveData<Double> observeTotalForRange(String start, String end);

    /** Plain-language search compiles to one of these. See SearchFilterSqlBuilder. */
    @RawQuery
    List<Expense> search(SupportSQLiteQuery query);
}
