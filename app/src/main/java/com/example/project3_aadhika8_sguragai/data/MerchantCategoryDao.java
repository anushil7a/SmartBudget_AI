package com.example.project3_aadhika8_sguragai.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MerchantCategoryDao {

    @Query("SELECT * FROM merchant_category WHERE merchantPattern = :pattern LIMIT 1")
    MerchantCategory getByPattern(String pattern);

    @Query("SELECT * FROM merchant_category WHERE userDefined = 1 ORDER BY updatedAt DESC")
    List<MerchantCategory> getUserDefinedMappings();

    @Query("SELECT * FROM merchant_category ORDER BY updatedAt DESC")
    List<MerchantCategory> getAllMappings();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(MerchantCategory mapping);

    @Query("DELETE FROM merchant_category WHERE merchantPattern = :pattern")
    void delete(String pattern);

    @Query("SELECT COUNT(*) FROM merchant_category")
    int getCount();
}
