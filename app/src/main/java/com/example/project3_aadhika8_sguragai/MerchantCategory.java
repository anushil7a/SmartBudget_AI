package com.example.project3_aadhika8_sguragai;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity for storing learned merchant-to-category mappings.
 * When a user corrects a category, we store the mapping so future expenses
 * from the same merchant auto-categorize correctly.
 */
@Entity(tableName = "merchant_category")
public class MerchantCategory {

    @PrimaryKey
    @NonNull
    public String merchantPattern;

    public ExpenseCategory category;

    public boolean userDefined;

    public long updatedAt;

    public MerchantCategory() {
        this.merchantPattern = "";
        this.updatedAt = System.currentTimeMillis();
    }

    public MerchantCategory(@NonNull String merchantPattern, ExpenseCategory category, boolean userDefined) {
        this.merchantPattern = merchantPattern;
        this.category = category;
        this.userDefined = userDefined;
        this.updatedAt = System.currentTimeMillis();
    }
}
