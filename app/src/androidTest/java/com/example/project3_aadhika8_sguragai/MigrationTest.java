package com.example.project3_aadhika8_sguragai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.project3_aadhika8_sguragai.data.AppDatabase;
import com.example.project3_aadhika8_sguragai.data.Migrations;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * The migration exists so an upgrade does not throw the user's history away. This asserts
 * exactly that: rows written under v3 are still there, with sensible values, under v4.
 */
@RunWith(AndroidJUnit4.class)
public class MigrationTest {

    private static final String DB = "migration-test";

    @Rule
    public MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase.class,
            java.util.Collections.emptyList(),
            new FrameworkSQLiteOpenHelperFactory());

    @Test
    public void migrate3To4_preservesRowsAndPopulatesNewColumns() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(DB, 3);
        db.execSQL("INSERT INTO expenses (amount, category, title, date, note) "
                + "VALUES (12.50, 'FOOD', 'Chipotle Lunch', '2025-11-25', 'Bowl with chicken')");
        db.execSQL("INSERT INTO expenses (amount, category, title, date, note) "
                + "VALUES (4.25, 'FOOD', 'Starbucks Latte', '2025-11-25', 'Caramel latte')");
        db.close();

        SupportSQLiteDatabase migrated =
                helper.runMigrationsAndValidate(DB, 4, true, Migrations.MIGRATION_3_4);

        Cursor c = migrated.query("SELECT title, merchant, source, categorySource, "
                + "predictionConfidence, receiptPath, createdAt FROM expenses ORDER BY id");

        assertEquals("both v3 rows must survive the upgrade", 2, c.getCount());

        assertTrue(c.moveToFirst());
        assertEquals("Chipotle Lunch", c.getString(0));
        assertEquals("merchant backfills from title", "Chipotle Lunch", c.getString(1));
        assertEquals("MANUAL", c.getString(2));
        assertEquals("USER", c.getString(3));
        assertTrue("pre-existing rows have no prediction", c.isNull(4));
        assertTrue("pre-existing rows have no receipt", c.isNull(5));
        assertEquals(0L, c.getLong(6));

        assertTrue(c.moveToNext());
        assertEquals("Starbucks Latte", c.getString(0));
        assertEquals("Starbucks Latte", c.getString(1));

        c.close();
    }

    @Test
    public void migrate3To4_createsEmptyModelTables() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(DB, 3);
        db.close();

        SupportSQLiteDatabase migrated =
                helper.runMigrationsAndValidate(DB, 4, true, Migrations.MIGRATION_3_4);

        Cursor tokens = migrated.query("SELECT COUNT(*) FROM token_counts");
        assertTrue(tokens.moveToFirst());
        assertEquals(0, tokens.getInt(0));
        tokens.close();

        Cursor cats = migrated.query("SELECT COUNT(*) FROM category_counts");
        assertTrue(cats.moveToFirst());
        assertEquals(0, cats.getInt(0));
        cats.close();
    }
}
