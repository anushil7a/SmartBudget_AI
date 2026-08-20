package com.example.project3_aadhika8_sguragai.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;

/**
 * One cell of the Naive Bayes count matrix: how often {@code token} has been seen
 * in a document labelled {@code category}.
 *
 * <p>The model lives in Room as plain rows rather than a serialized blob so it stays
 * inspectable and can be updated incrementally by learn/unlearn.
 */
@Entity(tableName = "token_counts", primaryKeys = {"token", "category"})
public class TokenCount {

    @NonNull
    public String token = "";

    @NonNull
    public ExpenseCategory category = ExpenseCategory.OTHER;

    public int count;

    public TokenCount() {
    }

    @Ignore
    public TokenCount(@NonNull String token, @NonNull ExpenseCategory category, int count) {
        this.token = token;
        this.category = category;
        this.count = count;
    }
}
