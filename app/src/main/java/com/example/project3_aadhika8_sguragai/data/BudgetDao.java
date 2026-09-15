package com.example.project3_aadhika8_sguragai.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.lifecycle.LiveData;

@Dao
public interface BudgetDao {

    @Query("SELECT amount FROM budget WHERE id = 1 LIMIT 1")
    Double getBudget();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Budget budget);

    @Query("SELECT amount FROM budget WHERE id = 1 LIMIT 1")
    LiveData<Double> observeBudget();
}
