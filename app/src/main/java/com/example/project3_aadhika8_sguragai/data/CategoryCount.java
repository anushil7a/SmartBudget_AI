package com.example.project3_aadhika8_sguragai.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/** Per-category document and token totals — the denominators in the Naive Bayes score. */
@Entity(tableName = "category_counts")
public class CategoryCount {

    @PrimaryKey
    @NonNull
    public ExpenseCategory category = ExpenseCategory.OTHER;

    public int docCount;

    /** Sum of this category's token counts, cached so scoring needs no aggregate query. */
    public int tokenTotal;

    public CategoryCount() {
    }

    @Ignore
    public CategoryCount(@NonNull ExpenseCategory category, int docCount, int tokenTotal) {
        this.category = category;
        this.docCount = docCount;
        this.tokenTotal = tokenTotal;
    }
}
