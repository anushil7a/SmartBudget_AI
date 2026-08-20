package com.example.project3_aadhika8_sguragai;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BarChartFragment extends Fragment {

    private BarChart barChart;
    private TextView textAverageDaily;

    private ExpenseDao expenseDao;
    private String startDate;
    private String endDate;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bar_chart, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        barChart = view.findViewById(R.id.barChart);
        textAverageDaily = view.findViewById(R.id.textAverageDaily);

        expenseDao = AppDatabase.getInstance(requireContext()).expenseDao();

        setupBarChart();

        if (getActivity() instanceof ChartsActivity) {
            ChartsActivity parent = (ChartsActivity) getActivity();
            startDate = parent.getStartDate();
            endDate = parent.getEndDate();
            if (startDate != null && endDate != null) {
                loadData();
            }
        }
    }

    private void setupBarChart() {
        barChart.getDescription().setEnabled(false);
        barChart.setDrawGridBackground(false);
        barChart.setDrawBarShadow(false);
        barChart.setHighlightFullBarEnabled(false);
        barChart.setDrawValueAboveBar(true);
        barChart.setPinchZoom(false);
        barChart.setScaleEnabled(false);

        barChart.getLegend().setEnabled(false);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(requireContext().getColor(R.color.text_secondary));

        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(requireContext().getColor(R.color.divider));
        leftAxis.setTextColor(requireContext().getColor(R.color.text_secondary));
        leftAxis.setAxisMinimum(0f);

        barChart.getAxisRight().setEnabled(false);
    }

    public void updateData(String startDate, String endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
        if (isAdded()) {
            loadData();
        }
    }

    private void loadData() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat labelFormat = new SimpleDateFormat("MM/dd", Locale.getDefault());

        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        double total = 0;
        int dayCount = 0;

        try {
            Date start = sdf.parse(startDate);
            Date end = sdf.parse(endDate);
            
            if (start == null || end == null) return;

            Calendar cal = Calendar.getInstance();
            cal.setTime(start);

            int index = 0;
            while (!cal.getTime().after(end)) {
                String dayStr = sdf.format(cal.getTime());
                double dayTotal = expenseDao.getTotalForDay(dayStr);

                entries.add(new BarEntry(index, (float) dayTotal));
                labels.add(labelFormat.format(cal.getTime()));
                
                total += dayTotal;
                dayCount++;
                index++;

                cal.add(Calendar.DAY_OF_MONTH, 1);
            }

        } catch (ParseException e) {
            e.printStackTrace();
        }

        // Update average
        double average = dayCount > 0 ? total / dayCount : 0;
        textAverageDaily.setText(String.format(Locale.getDefault(), "Daily Average: $%.2f", average));

        // Update chart
        if (entries.isEmpty()) {
            barChart.setData(null);
            barChart.invalidate();
        } else {
            BarDataSet dataSet = new BarDataSet(entries, "Daily Spending");
            dataSet.setColor(requireContext().getColor(R.color.primary));
            dataSet.setValueTextSize(10f);
            dataSet.setValueTextColor(requireContext().getColor(R.color.text_secondary));

            BarData data = new BarData(dataSet);
            data.setBarWidth(0.7f);

            barChart.setData(data);
            barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
            barChart.getXAxis().setLabelCount(Math.min(labels.size(), 7));
            barChart.animateY(1000);
            barChart.invalidate();
        }
    }
}
