package com.example.project3_aadhika8_sguragai.data;

import androidx.room.TypeConverter;

public class Converters {

    @TypeConverter
    public static String fromCategory(ExpenseCategory cat) {
        return cat == null ? null : cat.name();
    }

    @TypeConverter
    public static ExpenseCategory toCategory(String value) {
        return value == null ? null : ExpenseCategory.valueOf(value);
    }
}
