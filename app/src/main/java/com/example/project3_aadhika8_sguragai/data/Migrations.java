package com.example.project3_aadhika8_sguragai.data;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Written migrations. This replaces {@code fallbackToDestructiveMigration()}, which silently
 * dropped every row on a schema change — including whatever the classifier had learned.
 */
public final class Migrations {

    private Migrations() {
    }

    /**
     * v3 → v4: six new Expense columns plus the two Naive Bayes model tables.
     *
     * <p>The expenses table is recreated rather than ALTER-ed. {@code ADD COLUMN} on a table
     * with rows requires a DEFAULT for NOT NULL columns, and that default is recorded in the
     * table definition — which then fails {@code runMigrationsAndValidate}, because the
     * schema Room generates from the entity declares no defaults. Copy-and-rename reproduces
     * the target DDL exactly.
     *
     * <p>Existing rows survive: {@code merchant} backfills from {@code title},
     * {@code source} becomes MANUAL and {@code categorySource} USER, since anything already
     * in the database predates scanning and prediction.
     */
    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `expenses_new` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`amount` REAL NOT NULL, "
                    + "`category` TEXT, "
                    + "`title` TEXT, "
                    + "`date` TEXT, "
                    + "`note` TEXT, "
                    + "`merchant` TEXT, "
                    + "`source` TEXT, "
                    + "`categorySource` TEXT, "
                    + "`predictionConfidence` REAL, "
                    + "`receiptPath` TEXT, "
                    + "`createdAt` INTEGER NOT NULL)");

            db.execSQL("INSERT INTO `expenses_new` "
                    + "(`id`, `amount`, `category`, `title`, `date`, `note`, `merchant`, "
                    + "`source`, `categorySource`, `predictionConfidence`, `receiptPath`, `createdAt`) "
                    + "SELECT `id`, `amount`, `category`, `title`, `date`, `note`, `title`, "
                    + "'MANUAL', 'USER', NULL, NULL, 0 FROM `expenses`");

            db.execSQL("DROP TABLE `expenses`");
            db.execSQL("ALTER TABLE `expenses_new` RENAME TO `expenses`");

            db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_date` "
                    + "ON `expenses` (`date`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_category` "
                    + "ON `expenses` (`category`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `token_counts` ("
                    + "`token` TEXT NOT NULL, "
                    + "`category` TEXT NOT NULL, "
                    + "`count` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`token`, `category`))");

            db.execSQL("CREATE TABLE IF NOT EXISTS `category_counts` ("
                    + "`category` TEXT NOT NULL, "
                    + "`docCount` INTEGER NOT NULL, "
                    + "`tokenTotal` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`category`))");
        }
    };
}
