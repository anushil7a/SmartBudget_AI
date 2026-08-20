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
                    // TEMPORARY — remove in Phase 6 (Task 6.2) once HomeActivity and
                    // SummaryActivity are replaced by ViewModels reading through
                    // ExpenseRepository. The legacy screens still call DAOs inline; this
                    // keeps the app runnable while the sense/ engines are built out.
                    // The spec requires this flag gone in the finished app.
                    .allowMainThreadQueries()
                    // No fallbackToDestructiveMigration: an upgrade must not silently
                    // discard the user's data or the classifier's learned counts.
                    .addMigrations(Migrations.MIGRATION_3_4)
                    .addCallback(seedData)
                    .build();
        }
        return instance;
    }

    /**
     * Sample rows, seeded once on first create.
     *
     * <p>Each row is {amount, category, title, date, note}. The v4 columns are filled in
     * uniformly below: seeded data predates scanning, so it is MANUAL/USER with no
     * confidence and no receipt.
     */
    private static final String[][] SEED_EXPENSES = {
            // October — activity spread across the month
            {"45.20", "GROCERIES", "Weekly Groceries", "2025-10-03", "Produce and snacks"},
            {"9.75", "FOOD", "Coffee and Bagel", "2025-10-04", "Breakfast on the go"},
            {"65.00", "TRANSPORT", "Gas Refill", "2025-10-05", "Filled tank"},
            {"29.99", "ENTERTAINMENT", "Movie Night", "2025-10-06", "Tickets and popcorn"},
            {"120.00", "BILLS", "Electric Bill", "2025-10-07", "October power bill"},
            {"52.40", "GROCERIES", "Warehouse Club", "2025-10-10", "Bulk items"},
            {"18.25", "FOOD", "Dinner Out", "2025-10-11", "Thai restaurant"},
            {"15.99", "ENTERTAINMENT", "Netflix Subscription", "2025-10-12", "Monthly plan"},
            {"85.00", "SHOPPING", "Clothes", "2025-10-14", "Jeans and shirt"},
            {"33.10", "TRANSPORT", "Uber Rides", "2025-10-16", "Weekend outings"},
            {"12.50", "FOOD", "Lunch with Colleagues", "2025-10-18", "Sandwich shop"},
            {"64.80", "GROCERIES", "Weekly Groceries", "2025-10-21", "Pantry restock"},
            {"25.00", "OTHER", "Gift for Friend", "2025-10-24", "Birthday present"},
            {"40.00", "ENTERTAINMENT", "Concert Tickets", "2025-10-27", "Local show"},

            // November — heavier spending across categories
            {"55.60", "GROCERIES", "Weekly Groceries", "2025-11-01", "Produce and snacks"},
            {"11.25", "FOOD", "Brunch", "2025-11-02", "Pancakes and coffee"},
            {"70.00", "TRANSPORT", "Gas Refill", "2025-11-03", "Filled tank"},
            {"130.00", "BILLS", "Internet + Phone", "2025-11-04", "Monthly services"},
            {"15.99", "ENTERTAINMENT", "Netflix Subscription", "2025-11-05", "Monthly plan"},
            {"95.30", "SHOPPING", "Online Orders", "2025-11-07", "Household items"},
            {"48.75", "GROCERIES", "Weekly Groceries", "2025-11-09", "Dinner ingredients"},
            {"20.50", "FOOD", "Dinner Out", "2025-11-11", "Italian restaurant"},
            {"42.00", "TRANSPORT", "Gas Refill", "2025-11-13", "Filled half tank"},
            {"60.00", "ENTERTAINMENT", "Live Show", "2025-11-15", "Comedy night"},
            {"110.00", "BILLS", "Electric Bill", "2025-11-18", "November power bill"},
            {"68.40", "GROCERIES", "Weekly Groceries", "2025-11-20", "Vegetables and snacks"},
            {"25.00", "OTHER", "Charity Donation", "2025-11-22", "Local charity"},
            {"12.50", "FOOD", "Chipotle Lunch", "2025-11-25", "Bowl with chicken"},
            {"4.25", "FOOD", "Starbucks Latte", "2025-11-25", "Caramel latte"},
            {"90.00", "BILLS", "Electric Bill", "2025-11-27", "Black Friday energy use"},
            {"220.00", "SHOPPING", "Black Friday Deals", "2025-11-29", "Electronics and clothes"},
            {"68.40", "GROCERIES", "Big Grocery Run", "2025-11-30", "Stocking up for December"},

            // December — dense, but only through Dec 7
            {"35.00", "GROCERIES", "Groceries Run", "2025-12-01", "Produce and snacks"},
            {"18.75", "FOOD", "Dinner Out", "2025-12-02", "Italian restaurant"},
            {"60.00", "TRANSPORT", "Uber Rides", "2025-12-03", "Commute to office"},
            {"120.00", "BILLS", "Internet + Phone", "2025-12-04", "Monthly services"},
            {"9.99", "ENTERTAINMENT", "Spotify Subscription", "2025-12-05", "Premium plan"},
            {"220.00", "SHOPPING", "Winter Clothes", "2025-12-06", "Jacket and boots"},
            {"55.25", "GROCERIES", "Warehouse Club", "2025-12-07", "Bulk items"},
    };

    private static final RoomDatabase.Callback seedData = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);

            for (String[] row : SEED_EXPENSES) {
                db.execSQL(
                        "INSERT INTO expenses "
                                + "(amount, category, title, date, note, merchant, source, "
                                + "categorySource, predictionConfidence, receiptPath, createdAt) "
                                + "VALUES (?, ?, ?, ?, ?, ?, 'MANUAL', 'USER', NULL, NULL, 0)",
                        new Object[]{
                                Double.parseDouble(row[0]),
                                row[1],
                                row[2],
                                row[3],
                                row[4],
                                row[2]   // merchant mirrors title for seeded rows
                        });
            }

            db.execSQL("INSERT INTO budget (id, amount) VALUES (1, 1500.0)");
        }
    };
}
