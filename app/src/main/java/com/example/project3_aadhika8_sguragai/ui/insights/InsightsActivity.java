package com.example.project3_aadhika8_sguragai.ui.insights;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.project3_aadhika8_sguragai.BarChartFragment;
import com.example.project3_aadhika8_sguragai.PieChartFragment;
import com.example.project3_aadhika8_sguragai.R;
import com.example.project3_aadhika8_sguragai.TrendChartFragment;
import com.example.project3_aadhika8_sguragai.ui.home.HomeActivity;
import com.example.project3_aadhika8_sguragai.ui.scan.ScanActivity;
import com.example.project3_aadhika8_sguragai.ui.search.SearchActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

/**
 * The three existing charts, kept as they were and restyled: transparent backgrounds,
 * hairline axes, the category palette, no legend boxes.
 */
public class InsightsActivity extends AppCompatActivity implements DateRangeHost {

    private static final int[] TAB_TITLES = {
            R.string.tab_categories,
            R.string.tab_monthly,
            R.string.tab_trend
    };

    /**
     * Charts default to the last three months, which is wide enough for a trend to have a
     * shape without burying the current month in history.
     */
    private final java.time.LocalDate today = java.time.LocalDate.now();

    @Override
    public String getStartDate() {
        return today.minusMonths(2).withDayOfMonth(1)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    @Override
    public String getEndDate() {
        return today.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_insights);

        ViewPager2 pager = findViewById(R.id.viewPager);
        pager.setAdapter(new ChartPagerAdapter(this));

        TabLayout tabs = findViewById(R.id.tabLayout);
        new TabLayoutMediator(tabs, pager,
                (tab, position) -> tab.setText(TAB_TITLES[position])).attach();

        setupNavigation();
    }

    private void setupNavigation() {
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_insights);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_insights) {
                return true;
            }
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, HomeActivity.class));
            } else if (id == R.id.nav_scan) {
                startActivity(new Intent(this, ScanActivity.class));
            } else if (id == R.id.nav_search) {
                startActivity(new Intent(this, SearchActivity.class));
            }
            finish();
            return true;
        });
    }

    private static class ChartPagerAdapter extends FragmentStateAdapter {

        ChartPagerAdapter(@NonNull AppCompatActivity activity) {
            super(activity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new PieChartFragment();
                case 1:
                    return new BarChartFragment();
                default:
                    return new TrendChartFragment();
            }
        }

        @Override
        public int getItemCount() {
            return TAB_TITLES.length;
        }
    }
}
