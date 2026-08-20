package com.example.project3_aadhika8_sguragai;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class ChartsActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private MaterialButton buttonWeek, buttonMonth, buttonYear;
    private BottomNavigationView bottomNavigation;

    private String startDate;
    private String endDate;

    private PieChartFragment pieChartFragment;
    private BarChartFragment barChartFragment;
    private TrendChartFragment trendChartFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_charts);

        initViews();
        setupViewPager();
        setupDateRangeButtons();
        setupBottomNavigation();

        // Default to this month
        setMonthRange();
    }

    private void initViews() {
        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);
        buttonWeek = findViewById(R.id.buttonWeek);
        buttonMonth = findViewById(R.id.buttonMonth);
        buttonYear = findViewById(R.id.buttonYear);
        bottomNavigation = findViewById(R.id.bottomNavigation);
    }

    private void setupViewPager() {
        pieChartFragment = new PieChartFragment();
        barChartFragment = new BarChartFragment();
        trendChartFragment = new TrendChartFragment();

        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @Override
            public int getItemCount() {
                return 3;
            }

            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 0:
                        return pieChartFragment;
                    case 1:
                        return barChartFragment;
                    case 2:
                        return trendChartFragment;
                    default:
                        return pieChartFragment;
                }
            }
        });

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText(R.string.pie_chart);
                    break;
                case 1:
                    tab.setText(R.string.bar_chart);
                    break;
                case 2:
                    tab.setText(R.string.trend_chart);
                    break;
            }
        }).attach();
    }

    private void setupDateRangeButtons() {
        buttonWeek.setOnClickListener(v -> {
            setWeekRange();
            updateSelectedButton(buttonWeek);
        });

        buttonMonth.setOnClickListener(v -> {
            setMonthRange();
            updateSelectedButton(buttonMonth);
        });

        buttonYear.setOnClickListener(v -> {
            setYearRange();
            updateSelectedButton(buttonYear);
        });

        // Default selection
        updateSelectedButton(buttonMonth);
    }

    private void updateSelectedButton(MaterialButton selected) {
        buttonWeek.setAlpha(buttonWeek == selected ? 1f : 0.6f);
        buttonMonth.setAlpha(buttonMonth == selected ? 1f : 0.6f);
        buttonYear.setAlpha(buttonYear == selected ? 1f : 0.6f);
    }

    private void setupBottomNavigation() {
        bottomNavigation.setSelectedItemId(R.id.nav_charts);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return true;
            } else if (id == R.id.nav_charts) {
                return true;
            } else if (id == R.id.nav_chat) {
                startActivity(new Intent(this, ChatActivity.class));
                return true;
            } else if (id == R.id.nav_summary) {
                startActivity(new Intent(this, SummaryActivity.class));
                return true;
            }
            return false;
        });
    }

    private void setWeekRange() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        endDate = sdf.format(cal.getTime());
        cal.add(Calendar.DAY_OF_MONTH, -6);
        startDate = sdf.format(cal.getTime());
        updateCharts();
    }

    private void setMonthRange() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        endDate = sdf.format(cal.getTime());
        cal.set(Calendar.DAY_OF_MONTH, 1);
        startDate = sdf.format(cal.getTime());
        updateCharts();
    }

    private void setYearRange() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        endDate = sdf.format(cal.getTime());
        cal.set(Calendar.MONTH, Calendar.JANUARY);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        startDate = sdf.format(cal.getTime());
        updateCharts();
    }

    private void updateCharts() {
        if (pieChartFragment != null) {
            pieChartFragment.updateData(startDate, endDate);
        }
        if (barChartFragment != null) {
            barChartFragment.updateData(startDate, endDate);
        }
        if (trendChartFragment != null) {
            trendChartFragment.updateData(startDate, endDate);
        }
    }

    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    @Override
    protected void onResume() {
        super.onResume();
        bottomNavigation.setSelectedItemId(R.id.nav_charts);
    }
}
