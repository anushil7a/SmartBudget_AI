package com.example.project3_aadhika8_sguragai.ui.search;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.project3_aadhika8_sguragai.R;
import com.example.project3_aadhika8_sguragai.data.Expense;
import com.example.project3_aadhika8_sguragai.sense.search.SearchFilter;
import com.example.project3_aadhika8_sguragai.ui.home.ExpenseAdapter;
import com.example.project3_aadhika8_sguragai.ui.home.HomeActivity;
import com.example.project3_aadhika8_sguragai.ui.insights.InsightsActivity;
import com.example.project3_aadhika8_sguragai.ui.scan.ScanActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
import java.util.Locale;

/**
 * Plain-language search.
 *
 * <p>The chip row is the honest part of this screen: rather than silently guessing, it shows
 * exactly what the parser understood, and each chip can be removed to widen the query. What
 * the app inferred stays arguable instead of feeling like magic.
 */
public class SearchActivity extends AppCompatActivity {

    private SearchViewModel model;

    private TextInputEditText editQuery;
    private ChipGroup chipGroup;
    private TextView textStatus;
    private TextView textPrivacyNote;
    private TextView textAnswer;
    private View scrollAnswer;
    private RecyclerView listResults;
    private ProgressBar progress;

    private ExpenseAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        model = new ViewModelProvider(this).get(SearchViewModel.class);

        bindViews();
        setupNavigation();
        observeModel();
    }

    private void bindViews() {
        editQuery = findViewById(R.id.editQuery);
        chipGroup = findViewById(R.id.chipGroupFilters);
        textStatus = findViewById(R.id.textStatus);
        textPrivacyNote = findViewById(R.id.textPrivacyNote);
        textAnswer = findViewById(R.id.textAnswer);
        scrollAnswer = findViewById(R.id.scrollAnswer);
        listResults = findViewById(R.id.listResults);
        progress = findViewById(R.id.progressSearch);

        adapter = new ExpenseAdapter(new ExpenseAdapter.Listener() {
            @Override
            public void onClick(Expense expense) {
                // Results are read-only here; corrections belong on Home and Scan.
            }

            @Override
            public void onRecategorise(Expense expense) {
            }
        });
        listResults.setLayoutManager(new LinearLayoutManager(this));
        listResults.setAdapter(adapter);

        TextInputLayout layout = findViewById(R.id.inputLayoutQuery);
        layout.setEndIconOnClickListener(v -> submit());

        editQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submit();
                return true;
            }
            return false;
        });

        MaterialButtonToggleGroup toggle = findViewById(R.id.toggleMode);
        toggle.check(R.id.buttonFind);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            model.setMode(checkedId == R.id.buttonAsk
                    ? SearchViewModel.Mode.ASK
                    : SearchViewModel.Mode.FIND);
        });
    }

    private void setupNavigation() {
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_search);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_search) {
                return true;
            }
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, HomeActivity.class));
            } else if (id == R.id.nav_scan) {
                startActivity(new Intent(this, ScanActivity.class));
            } else if (id == R.id.nav_insights) {
                startActivity(new Intent(this, InsightsActivity.class));
            }
            finish();
            return true;
        });
    }

    private void observeModel() {
        model.mode().observe(this, mode -> {
            boolean ask = mode == SearchViewModel.Mode.ASK;

            // The disclosure is the whole point of having two modes.
            textPrivacyNote.setText(ask ? R.string.privacy_ask : R.string.privacy_find);
            textPrivacyNote.setTextColor(getColorAttr(ask
                    ? androidx.appcompat.R.attr.colorError
                    : com.google.android.material.R.attr.colorOnSurfaceVariant));

            findViewById(R.id.inputLayoutQuery);
            scrollAnswer.setVisibility(ask ? View.VISIBLE : View.GONE);
            listResults.setVisibility(ask ? View.GONE : View.VISIBLE);
            chipGroup.setVisibility(View.GONE);
            textStatus.setText(null);
        });

        model.chips().observe(this, this::renderChips);

        model.results().observe(this, list -> adapter.submitList(list));

        model.status().observe(this, status -> textStatus.setText(
                status == null ? null : status.toUpperCase(Locale.US)));

        model.busy().observe(this, busy ->
                progress.setVisibility(Boolean.TRUE.equals(busy) ? View.VISIBLE : View.GONE));

        model.answer().observe(this, answer -> textAnswer.setText(answer));
    }

    private void renderChips(List<SearchFilter.Chip> chips) {
        chipGroup.removeAllViews();
        if (chips == null || chips.isEmpty()) {
            chipGroup.setVisibility(View.GONE);
            return;
        }
        chipGroup.setVisibility(View.VISIBLE);

        for (SearchFilter.Chip c : chips) {
            Chip view = new Chip(this);
            view.setText(c.label);
            view.setCloseIconVisible(true);
            view.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(
                    getColorAttr(com.google.android.material.R.attr.colorSurfaceVariant)));
            view.setTextColor(getColorAttr(
                    com.google.android.material.R.attr.colorOnSurface));
            view.setOnCloseIconClickListener(v -> model.removeChip(c));

            // Chips arrive as the parse resolves, rather than appearing fully formed.
            view.setAlpha(0f);
            view.animate().alpha(1f).setDuration(180).start();

            chipGroup.addView(view);
        }
    }

    private void submit() {
        String query = editQuery.getText() == null ? "" : editQuery.getText().toString().trim();
        if (query.isEmpty()) {
            return;
        }
        if (model.mode().getValue() == SearchViewModel.Mode.ASK) {
            if (!model.hasApiKey()) {
                textAnswer.setText(R.string.no_api_key);
                return;
            }
            model.ask(query);
        } else {
            model.find(query);
        }
    }

    private int getColorAttr(int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }
}
