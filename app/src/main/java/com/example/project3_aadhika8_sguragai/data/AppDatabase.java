package com.example.project3_aadhika8_sguragai.data;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;


@Database(
        entities = {Expense.class, Budget.class, MerchantCategory.class, ChatMessage.class},
        version = 3,
        exportSchema = false
)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    private static AppDatabase instance;

    public abstract ExpenseDao expenseDao();

    public abstract BudgetDao budgetDao();

    public abstract MerchantCategoryDao merchantCategoryDao();

    public abstract ChatMessageDao chatMessageDao();

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "budgetbuddy.db"
                    )
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration()
                    .addCallback(seedData)
                    .build();
        }
        return instance;
    }

    private static RoomDatabase.Callback seedData = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);

            // October sample data (lots of activity throughout the month)
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 45.20, 'GROCERIES', 'Weekly Groceries', '2025-10-03', 'Produce and snacks');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 9.75, 'FOOD', 'Coffee and Bagel', '2025-10-04', 'Breakfast on the go');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 65.00, 'TRANSPORT', 'Gas Refill', '2025-10-05', 'Filled tank');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 29.99, 'ENTERTAINMENT', 'Movie Night', '2025-10-06', 'Tickets and popcorn');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 120.00, 'BILLS', 'Electric Bill', '2025-10-07', 'October power bill');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 52.40, 'GROCERIES', 'Warehouse Club', '2025-10-10', 'Bulk items');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 18.25, 'FOOD', 'Dinner Out', '2025-10-11', 'Thai restaurant');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 15.99, 'ENTERTAINMENT', 'Netflix Subscription', '2025-10-12', 'Monthly plan');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 85.00, 'SHOPPING', 'Clothes', '2025-10-14', 'Jeans and shirt');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 33.10, 'TRANSPORT', 'Uber Rides', '2025-10-16', 'Weekend outings');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 12.50, 'FOOD', 'Lunch with Colleagues', '2025-10-18', 'Sandwich shop');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 64.80, 'GROCERIES', 'Weekly Groceries', '2025-10-21', 'Pantry restock');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 25.00, 'OTHER', 'Gift for Friend', '2025-10-24', 'Birthday present');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 40.00, 'ENTERTAINMENT', 'Concert Tickets', '2025-10-27', 'Local show');");

            // November sample data (heavy spending across categories)
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 55.60, 'GROCERIES', 'Weekly Groceries', '2025-11-01', 'Produce and snacks');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 11.25, 'FOOD', 'Brunch', '2025-11-02', 'Pancakes and coffee');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 70.00, 'TRANSPORT', 'Gas Refill', '2025-11-03', 'Filled tank');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 130.00, 'BILLS', 'Internet + Phone', '2025-11-04', 'Monthly services');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 15.99, 'ENTERTAINMENT', 'Netflix Subscription', '2025-11-05', 'Monthly plan');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 95.30, 'SHOPPING', 'Online Orders', '2025-11-07', 'Household items');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 48.75, 'GROCERIES', 'Weekly Groceries', '2025-11-09', 'Dinner ingredients');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 20.50, 'FOOD', 'Dinner Out', '2025-11-11', 'Italian restaurant');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 42.00, 'TRANSPORT', 'Gas Refill', '2025-11-13', 'Filled half tank');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 60.00, 'ENTERTAINMENT', 'Live Show', '2025-11-15', 'Comedy night');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 110.00, 'BILLS', 'Electric Bill', '2025-11-18', 'November power bill');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 68.40, 'GROCERIES', 'Weekly Groceries', '2025-11-20', 'Vegetables and snacks');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 25.00, 'OTHER', 'Charity Donation', '2025-11-22', 'Local charity');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 12.50, 'FOOD', 'Chipotle Lunch', '2025-11-25', 'Bowl with chicken');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 4.25, 'FOOD', 'Starbucks Latte', '2025-11-25', 'Caramel latte');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 90.00, 'BILLS', 'Electric Bill', '2025-11-27', 'Black Friday energy use');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 220.00, 'SHOPPING', 'Black Friday Deals', '2025-11-29', 'Electronics and clothes');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 68.40, 'GROCERIES', 'Big Grocery Run', '2025-11-30', 'Stocking up for December');");

            // December sample data (lots of spending but ONLY up to Dec 7)
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 35.00, 'GROCERIES', 'Groceries Run', '2025-12-01', 'Produce and snacks');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 18.75, 'FOOD', 'Dinner Out', '2025-12-02', 'Italian restaurant');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 60.00, 'TRANSPORT', 'Uber Rides', '2025-12-03', 'Commute to office');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 120.00, 'BILLS', 'Internet + Phone', '2025-12-04', 'Monthly services');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 9.99, 'ENTERTAINMENT', 'Spotify Subscription', '2025-12-05', 'Premium plan');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 220.00, 'SHOPPING', 'Winter Clothes', '2025-12-06', 'Jacket and boots');");
            db.execSQL("INSERT INTO expenses (id, amount, category, title, date, note) VALUES " +
                    "(NULL, 55.25, 'GROCERIES', 'Warehouse Club', '2025-12-07', 'Bulk items');");

            // Initial monthly budget
            db.execSQL("INSERT INTO budget (id, amount) VALUES (1, 1500.0);");
        }
    };
}
