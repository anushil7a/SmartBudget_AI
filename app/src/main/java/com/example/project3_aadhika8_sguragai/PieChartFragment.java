package com.example.project3_aadhika8_sguragai;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PieChartFragment extends Fragment {

    private PieChart pieChart;
    private TextView textTotalSpending;
    private RecyclerView recyclerLegend;

    private ExpenseDao expenseDao;
    private String startDate;
    private String endDate;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_pie_chart, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        pieChart = view.findViewById(R.id.pieChart);
        textTotalSpending = view.findViewById(R.id.textTotalSpending);
        recyclerLegend = view.findViewById(R.id.recyclerLegend);

        expenseDao = AppDatabase.getInstance(requireContext()).expenseDao();
        recyclerLegend.setLayoutManager(new LinearLayoutManager(requireContext()));

        setupPieChart();

        // Get initial dates from parent activity
        if (getActivity() instanceof ChartsActivity) {
            ChartsActivity parent = (ChartsActivity) getActivity();
            startDate = parent.getStartDate();
            endDate = parent.getEndDate();
            if (startDate != null && endDate != null) {
                loadData();
            }
        }
    }

    private void setupPieChart() {
        pieChart.setUsePercentValues(true);
        pieChart.getDescription().setEnabled(false);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.TRANSPARENT);
        pieChart.setHoleRadius(45f);
        pieChart.setTransparentCircleRadius(50f);
        pieChart.setDrawEntryLabels(false);
        pieChart.setRotationEnabled(true);
        pieChart.setHighlightPerTapEnabled(true);

        Legend legend = pieChart.getLegend();
        legend.setEnabled(false); // We use custom legend
    }

    public void updateData(String startDate, String endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
        if (isAdded()) {
            loadData();
        }
    }

    private void loadData() {
        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        List<LegendItem> legendItems = new ArrayList<>();
        double total = 0;

        int[] categoryColors = {
            requireContext().getColor(R.color.category_food),
            requireContext().getColor(R.color.category_transport),
            requireContext().getColor(R.color.category_entertainment),
            requireContext().getColor(R.color.category_groceries),
            requireContext().getColor(R.color.category_bills),
            requireContext().getColor(R.color.category_shopping),
            requireContext().getColor(R.color.category_other)
        };

        ExpenseCategory[] categories = ExpenseCategory.values();
        for (int i = 0; i < categories.length; i++) {
            ExpenseCategory cat = categories[i];
            double amount = expenseDao.getTotalByCategory(cat, startDate, endDate);
            
            if (amount > 0) {
                entries.add(new PieEntry((float) amount, formatCategoryName(cat.name())));
                colors.add(categoryColors[i]);
                total += amount;
            }
            
            legendItems.add(new LegendItem(
                formatCategoryName(cat.name()),
                amount,
                categoryColors[i]
            ));
        }

        // Update total text
        textTotalSpending.setText(String.format(Locale.getDefault(), "Total: $%.2f", total));

        // Update chart
        if (entries.isEmpty()) {
            pieChart.setData(null);
            pieChart.invalidate();
        } else {
            PieDataSet dataSet = new PieDataSet(entries, "Categories");
            dataSet.setColors(colors);
            dataSet.setSliceSpace(2f);
            dataSet.setValueTextSize(12f);
            dataSet.setValueTextColor(Color.WHITE);
            dataSet.setValueFormatter(new PercentFormatter(pieChart));

            PieData data = new PieData(dataSet);
            pieChart.setData(data);
            pieChart.animateY(1000);
            pieChart.invalidate();
        }

        // Update legend
        double finalTotal = total;
        recyclerLegend.setAdapter(new LegendAdapter(legendItems, finalTotal));
    }

    private String formatCategoryName(String name) {
        if (name == null || name.isEmpty()) return "";
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.getDefault());
    }

    // Legend Item class
    static class LegendItem {
        String category;
        double amount;
        int color;

        LegendItem(String category, double amount, int color) {
            this.category = category;
            this.amount = amount;
            this.color = color;
        }
    }

    // Legend Adapter
    class LegendAdapter extends RecyclerView.Adapter<LegendAdapter.ViewHolder> {
        private final List<LegendItem> items;
        private final double total;

        LegendAdapter(List<LegendItem> items, double total) {
            this.items = items;
            this.total = total;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chart_legend, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            LegendItem item = items.get(position);
            holder.textCategory.setText(item.category);
            holder.textAmount.setText(String.format(Locale.getDefault(), "$%.2f", item.amount));
            
            double percent = total > 0 ? (item.amount / total) * 100 : 0;
            holder.textPercent.setText(String.format(Locale.getDefault(), "%.1f%%", percent));
            
            GradientDrawable bg = (GradientDrawable) holder.viewColor.getBackground();
            bg.setColor(item.color);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            View viewColor;
            TextView textCategory, textAmount, textPercent;

            ViewHolder(View itemView) {
                super(itemView);
                viewColor = itemView.findViewById(R.id.viewColor);
                textCategory = itemView.findViewById(R.id.textCategory);
                textAmount = itemView.findViewById(R.id.textAmount);
                textPercent = itemView.findViewById(R.id.textPercent);
            }
        }
    }
}
