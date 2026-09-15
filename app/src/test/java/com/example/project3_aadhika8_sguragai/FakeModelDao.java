package com.example.project3_aadhika8_sguragai;

import com.example.project3_aadhika8_sguragai.data.CategoryCount;
import com.example.project3_aadhika8_sguragai.data.ExpenseCategory;
import com.example.project3_aadhika8_sguragai.data.ModelDao;
import com.example.project3_aadhika8_sguragai.data.TokenCount;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory stand-in for the Room DAO, so the classifier's arithmetic can be tested on the
 * JVM. {@link ModelDao} is a plain interface for exactly this reason.
 */
public class FakeModelDao implements ModelDao {

    private final Map<String, Integer> tokens = new HashMap<>();          // "token|CATEGORY"
    private final Map<ExpenseCategory, CategoryCount> categories = new EnumMap<>(ExpenseCategory.class);

    private String key(String token, ExpenseCategory c) {
        return token + "|" + c.name();
    }

    @Override
    public Integer rawTokenCount(String token, ExpenseCategory category) {
        return tokens.get(key(token, category));
    }

    /** Convenience for assertions. */
    public int countOrZero(String token, ExpenseCategory category) {
        Integer v = tokens.get(key(token, category));
        return v == null ? 0 : v;
    }

    @Override
    public void upsertToken(TokenCount tc) {
        if (tc.count <= 0) {
            tokens.remove(key(tc.token, tc.category));
        } else {
            tokens.put(key(tc.token, tc.category), tc.count);
        }
    }

    @Override
    public List<TokenCount> tokenCountsFor(List<String> wanted) {
        Set<String> want = new HashSet<>(wanted);
        List<TokenCount> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : tokens.entrySet()) {
            int bar = e.getKey().lastIndexOf('|');
            String token = e.getKey().substring(0, bar);
            if (want.contains(token)) {
                out.add(new TokenCount(token,
                        ExpenseCategory.valueOf(e.getKey().substring(bar + 1)),
                        e.getValue()));
            }
        }
        return out;
    }

    @Override
    public int vocabularySize() {
        Set<String> distinct = new HashSet<>();
        for (String k : tokens.keySet()) {
            distinct.add(k.substring(0, k.lastIndexOf('|')));
        }
        return distinct.size();
    }

    @Override
    public List<CategoryCount> allCategoryCounts() {
        return new ArrayList<>(categories.values());
    }

    @Override
    public CategoryCount categoryCount(ExpenseCategory category) {
        return categories.get(category);
    }

    @Override
    public void upsertCategory(CategoryCount cc) {
        categories.put(cc.category, cc);
    }

    @Override
    public int totalDocs() {
        int sum = 0;
        for (CategoryCount c : categories.values()) {
            sum += c.docCount;
        }
        return sum;
    }

    @Override
    public int tokenRowCount() {
        return tokens.size();
    }
}
