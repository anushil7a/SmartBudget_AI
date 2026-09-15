package com.example.project3_aadhika8_sguragai.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Read/write access to the Naive Bayes count tables.
 *
 * <p>This is a plain interface on purpose: the classifier takes a {@code ModelDao} in its
 * constructor, so unit tests can hand it an in-memory fake and stay on the JVM.
 */
@Dao
public interface ModelDao {

    @Query("SELECT count FROM token_counts WHERE token = :token AND category = :category")
    Integer rawTokenCount(String token, ExpenseCategory category);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertToken(TokenCount tokenCount);

    @Query("SELECT * FROM token_counts WHERE token IN (:tokens)")
    List<TokenCount> tokenCountsFor(List<String> tokens);

    @Query("SELECT COUNT(DISTINCT token) FROM token_counts")
    int vocabularySize();

    @Query("SELECT * FROM category_counts")
    List<CategoryCount> allCategoryCounts();

    @Query("SELECT * FROM category_counts WHERE category = :category")
    CategoryCount categoryCount(ExpenseCategory category);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertCategory(CategoryCount categoryCount);

    @Query("SELECT IFNULL(SUM(docCount), 0) FROM category_counts")
    int totalDocs();

    @Query("SELECT COUNT(*) FROM token_counts")
    int tokenRowCount();
}
