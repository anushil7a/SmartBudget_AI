package com.example.project3_aadhika8_sguragai.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {
                Expense.class,
                Budget.class,
                MerchantCategory.class,
                ChatMessage.class,
                TokenCount.class,
                CategoryCount.class
        },
        version = 4,
        exportSchema = true
)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    private static AppDatabase instance;

    public abstract ExpenseDao expenseDao();

    public abstract BudgetDao budgetDao();

    public abstract MerchantCategoryDao merchantCategoryDao();

    public abstract ChatMessageDao chatMessageDao();

    public abstract ModelDao modelDao();

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "budgetbuddy.db"
                    )
                    // No allowMainThreadQueries: every read and write goes through
                    // ExpenseRepository's executor, and each screen reads LiveData from a
                    // ViewModel. No fallbackToDestructiveMigration: an upgrade must not
                    // silently discard the user's data or the classifier's learned counts.
                    .addMigrations(Migrations.MIGRATION_3_4)
                    .addCallback(seedData)
                    .build();
        }
        return instance;
    }

    /**
     * Sample rows, seeded once on first create.
     *
     * <p>Each row is {amount, category, title, dayOfMonth, monthsAgo, note}. Dates are
     * relative rather than fixed: hard-coded 2025 dates meant a fresh install opened on an
     * empty current month and looked broken. {@code monthsAgo} 0 is the current month, and
     * those rows stop at day 7 so the month reads as partially through.
     *
     * <p>The v4 columns are filled in uniformly below — seeded data predates scanning, so it
     * is MANUAL/USER with no confidence and no receipt.
     */
    private static final String[][] SEED_EXPENSES = {
            // Two months ago — activity spread across the month
            {"45.20", "GROCERIES", "Weekly Groceries", "3", "2", "Produce and snacks"},
            {"9.75", "FOOD", "Coffee and Bagel", "4", "2", "Breakfast on the go"},
            {"65.00", "TRANSPORT", "Gas Refill", "5", "2", "Filled tank"},
            {"29.99", "ENTERTAINMENT", "Movie Night", "6", "2", "Tickets and popcorn"},
            {"120.00", "BILLS", "Electric Bill", "7", "2", "Monthly power bill"},
            {"52.40", "GROCERIES", "Warehouse Club", "10", "2", "Bulk items"},
            {"18.25", "FOOD", "Dinner Out", "11", "2", "Thai restaurant"},
            {"15.99", "ENTERTAINMENT", "Netflix Subscription", "12", "2", "Monthly plan"},
            {"85.00", "SHOPPING", "Clothes", "14", "2", "Jeans and shirt"},
            {"33.10", "TRANSPORT", "Uber Rides", "16", "2", "Weekend outings"},
            {"12.50", "FOOD", "Lunch with Colleagues", "18", "2", "Sandwich shop"},
            {"64.80", "GROCERIES", "Weekly Groceries", "21", "2", "Pantry restock"},
            {"25.00", "OTHER", "Gift for Friend", "24", "2", "Birthday present"},
            {"40.00", "ENTERTAINMENT", "Concert Tickets", "27", "2", "Local show"},

            // Last month — heavier spending across categories
            {"55.60", "GROCERIES", "Weekly Groceries", "1", "1", "Produce and snacks"},
            {"11.25", "FOOD", "Brunch", "2", "1", "Pancakes and coffee"},
            {"70.00", "TRANSPORT", "Gas Refill", "3", "1", "Filled tank"},
            {"130.00", "BILLS", "Internet + Phone", "4", "1", "Monthly services"},
            {"15.99", "ENTERTAINMENT", "Netflix Subscription", "5", "1", "Monthly plan"},
            {"95.30", "SHOPPING", "Online Orders", "7", "1", "Household items"},
            {"48.75", "GROCERIES", "Weekly Groceries", "9", "1", "Dinner ingredients"},
            {"20.50", "FOOD", "Dinner Out", "11", "1", "Italian restaurant"},
            {"42.00", "TRANSPORT", "Gas Refill", "13", "1", "Filled half tank"},
            {"60.00", "ENTERTAINMENT", "Live Show", "15", "1", "Comedy night"},
            {"110.00", "BILLS", "Electric Bill", "18", "1", "Monthly power bill"},
            {"68.40", "GROCERIES", "Weekly Groceries", "20", "1", "Vegetables and snacks"},
            {"25.00", "OTHER", "Charity Donation", "22", "1", "Local charity"},
            {"12.50", "FOOD", "Chipotle Lunch", "25", "1", "Bowl with chicken"},
            {"4.25", "FOOD", "Starbucks Latte", "25", "1", "Caramel latte"},
            {"90.00", "BILLS", "Electric Bill", "27", "1", "Higher usage this month"},
            {"220.00", "SHOPPING", "Big Sale Haul", "29", "1", "Electronics and clothes"},
            {"68.40", "GROCERIES", "Big Grocery Run", "30", "1", "Stocking up"},

            // This month — dense, but only through day 7
            {"35.00", "GROCERIES", "Groceries Run", "1", "0", "Produce and snacks"},
            {"18.75", "FOOD", "Dinner Out", "2", "0", "Italian restaurant"},
            {"60.00", "TRANSPORT", "Uber Rides", "3", "0", "Commute to office"},
            {"120.00", "BILLS", "Internet + Phone", "4", "0", "Monthly services"},
            {"9.99", "ENTERTAINMENT", "Spotify Subscription", "5", "0", "Premium plan"},
            {"220.00", "SHOPPING", "New Clothes", "6", "0", "Jacket and boots"},
            {"55.25", "GROCERIES", "Warehouse Club", "7", "0", "Bulk items"},
    };

    private static final RoomDatabase.Callback seedData = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);

            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.format.DateTimeFormatter iso =
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");

            for (String[] row : SEED_EXPENSES) {
                int day = Integer.parseInt(row[3]);
                int monthsAgo = Integer.parseInt(row[4]);

                java.time.LocalDate month = today.minusMonths(monthsAgo);
                // Clamp so day 30 survives a 28-day February.
                int safeDay = Math.min(day, month.lengthOfMonth());
                String date = month.withDayOfMonth(safeDay).format(iso);

                db.execSQL(
                        "INSERT INTO expenses "
                                + "(amount, category, title, date, note, merchant, source, "
                                + "categorySource, predictionConfidence, receiptPath, createdAt) "
                                + "VALUES (?, ?, ?, ?, ?, ?, 'MANUAL', 'USER', NULL, NULL, 0)",
                        new Object[]{
                                Double.parseDouble(row[0]),
                                row[1],
                                row[2],
                                date,
                                row[5],
                                row[2]   // merchant mirrors title for seeded rows
                        });
            }

            db.execSQL("INSERT INTO budget (id, amount) VALUES (1, 1500.0)");
        }
    };
}
