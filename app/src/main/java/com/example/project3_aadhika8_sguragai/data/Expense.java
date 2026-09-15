package com.example.project3_aadhika8_sguragai.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;

@Entity(
        tableName = "expenses",
        indices = {
                @Index(value = {"date"}),
                @Index(value = {"category"})
        }
)
public class Expense {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public double amount;

    public ExpenseCategory category;
    public String title;

    public String date;   // yyyy-MM-dd

    public String note;

    /** Raw merchant as captured; distinct from the user-editable {@link #title}. */
    public String merchant;

    public CaptureSource source;

    public CategorySource categorySource;

    /** Null when the category was chosen by hand. */
    public Float predictionConfidence;

    /** Relative path under filesDir/receipts/, or null. */
    public String receiptPath;

    public long createdAt;
}
