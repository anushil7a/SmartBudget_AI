package com.example.project3_aadhika8_sguragai.sense.search;

import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteQuery;

import com.example.project3_aadhika8_sguragai.data.ExpenseCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * Compiles a {@link SearchFilter} to parameterised SQL, run locally through
 * {@code ExpenseDao.search}.
 *
 * <p>Every value is a bind argument. Nothing derived from the user's query string is
 * concatenated into the statement — merchant terms in particular arrive straight from typed
 * input, and they go in as parameters.
 */
public final class SearchFilterSqlBuilder {

    private SearchFilterSqlBuilder() {
    }

    /** The statement and its arguments, exposed separately so tests can assert both. */
    public static class Compiled {
        public final String sql;
        public final Object[] args;

        Compiled(String sql, Object[] args) {
            this.sql = sql;
            this.args = args;
        }
    }

    public static SupportSQLiteQuery build(SearchFilter f) {
        Compiled c = compile(f);
        return new SimpleSQLiteQuery(c.sql, c.args);
    }

    public static Compiled compile(SearchFilter f) {
        StringBuilder sql = new StringBuilder("SELECT * FROM expenses");
        List<Object> args = new ArrayList<>();
        List<String> where = new ArrayList<>();

        if (!f.categories.isEmpty()) {
            StringBuilder in = new StringBuilder("category IN (");
            boolean first = true;
            for (ExpenseCategory c : f.categories) {
                if (!first) {
                    in.append(", ");
                }
                in.append('?');
                args.add(c.name());
                first = false;
            }
            in.append(')');
            where.add(in.toString());
        }

        if (f.startDate != null) {
            where.add("date >= ?");
            args.add(f.startDate);
        }
        if (f.endDate != null) {
            where.add("date <= ?");
            args.add(f.endDate);
        }

        if (f.minAmount != null) {
            where.add("amount >= ?");
            args.add(f.minAmount);
        }
        if (f.maxAmount != null) {
            where.add("amount <= ?");
            args.add(f.maxAmount);
        }

        // Each term must appear somewhere on the row; the row's three text columns are the
        // places it could be.
        for (String term : f.merchantTerms) {
            where.add("(merchant LIKE ? OR title LIKE ? OR note LIKE ?)");
            String like = "%" + term + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }

        if (!where.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", where));
        }

        switch (f.sort) {
            case AMOUNT_DESC:
                sql.append(" ORDER BY amount DESC, date DESC");
                break;
            case AMOUNT_ASC:
                sql.append(" ORDER BY amount ASC, date DESC");
                break;
            case DATE_DESC:
            default:
                sql.append(" ORDER BY date DESC, id DESC");
                break;
        }

        if (f.limit != null && f.limit > 0) {
            sql.append(" LIMIT ?");
            args.add(f.limit);
        }

        return new Compiled(sql.toString(), args.toArray());
    }
}
