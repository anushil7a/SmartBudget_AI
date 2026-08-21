package com.example.project3_aadhika8_sguragai.ui;

import android.content.Context;

import androidx.core.content.ContextCompat;

import com.example.project3_aadhika8_sguragai.R;
import com.example.project3_aadhika8_sguragai.data.ExpenseCategory;

/**
 * Category to colour, by name rather than by position.
 *
 * <p>The charts used to index a colour array with the enum's ordinal, but the array was
 * written in a different order than {@code ExpenseCategory.values()} — so Groceries drew in
 * Food's colour, Food in Transport's, and so on down the list. A switch cannot drift like
 * that.
 */
public final class CategoryPalette {

    private CategoryPalette() {
    }

    public static int colorRes(ExpenseCategory category) {
        if (category == null) {
            return R.color.category_other;
        }
        switch (category) {
            case FOOD:
                return R.color.category_food;
            case TRANSPORT:
                return R.color.category_transport;
            case ENTERTAINMENT:
                return R.color.category_entertainment;
            case GROCERIES:
                return R.color.category_groceries;
            case BILLS:
                return R.color.category_bills;
            case SHOPPING:
                return R.color.category_shopping;
            case OTHER:
            default:
                return R.color.category_other;
        }
    }

    public static int color(Context context, ExpenseCategory category) {
        return ContextCompat.getColor(context, colorRes(category));
    }
}
