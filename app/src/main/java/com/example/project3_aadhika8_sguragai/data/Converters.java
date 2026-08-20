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

    @TypeConverter
    public static String fromCaptureSource(CaptureSource s) {
        return s == null ? null : s.name();
    }

    @TypeConverter
    public static CaptureSource toCaptureSource(String value) {
        return value == null ? null : CaptureSource.valueOf(value);
    }

    @TypeConverter
    public static String fromCategorySource(CategorySource s) {
        return s == null ? null : s.name();
    }

    @TypeConverter
    public static CategorySource toCategorySource(String value) {
        return value == null ? null : CategorySource.valueOf(value);
    }
}
