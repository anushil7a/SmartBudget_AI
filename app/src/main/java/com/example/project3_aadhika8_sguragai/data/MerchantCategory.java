package com.example.project3_aadhika8_sguragai.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * An explicit user pin: "Trader Joe's is always GROCERIES".
 *
 * <p>This used to be written implicitly on every category correction, which left the app with
 * two competing memories — this table and the classifier — that could disagree. It is now
 * written only when the user deliberately chooses "always categorise this merchant as…".
 * A pin short-circuits the model and reports full confidence; corrections go to the
 * classifier instead, via unlearn/learn.
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
