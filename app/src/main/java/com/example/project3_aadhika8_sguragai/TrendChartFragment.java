package com.example.project3_aadhika8_sguragai;

import com.example.project3_aadhika8_sguragai.data.*;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TrendChartFragment extends Fragment {

    private LineChart lineChart;
    private TextView textHighest, textAverage, textLowest;

    private ExpenseDao expenseDao;
    private String startDate;
    private String endDate;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_trend_chart, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        lineChart = view.findViewById(R.id.lineChart);
        textHighest = view.findViewById(R.id.textHighest);
        textAverage = view.findViewById(R.id.textAverage);
        textLowest = view.findViewById(R.id.textLowest);

        expenseDao = AppDatabase.getInstance(requireContext()).expenseDao();

        setupLineChart();

        if (getActivity() instanceof ChartsActivity) {
            ChartsActivity parent = (ChartsActivity) getActivity();
            startDate = parent.getStartDate();
            endDate = parent.getEndDate();
            if (startDate != null && endDate != null) {
                loadData();
            }
        }
    }

    private void setupLineChart() {
        lineChart.getDescription().setEnabled(false);
        lineChart.setDrawGridBackground(false);
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(false);
        lineChart.setPinchZoom(false);

        lineChart.getLegend().setEnabled(false);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(requireContext().getColor(R.color.text_secondary));

        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(requireContext().getColor(R.color.divider));
        leftAxis.setTextColor(requireContext().getColor(R.color.text_secondary));
        leftAxis.setAxisMinimum(0f);

        lineChart.getAxisRight().setEnabled(false);
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

        List<Entry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Double> dailyTotals = new ArrayList<>();

        double cumulativeTotal = 0;

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

                cumulativeTotal += dayTotal;
                dailyTotals.add(dayTotal);

                // For trend line, show cumulative spending
                entries.add(new Entry(index, (float) cumulativeTotal));
                labels.add(labelFormat.format(cal.getTime()));

                index++;
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }

        } catch (ParseException e) {
            e.printStackTrace();
        }

        // Calculate stats
        double highest = 0, lowest = Double.MAX_VALUE, total = 0;
        for (double val : dailyTotals) {
            if (val > highest) highest = val;
            if (val < lowest && val > 0) lowest = val;
            total += val;
        }
        if (lowest == Double.MAX_VALUE) lowest = 0;
        double average = dailyTotals.size() > 0 ? total / dailyTotals.size() : 0;

        textHighest.setText(String.format(Locale.getDefault(), "$%.2f", highest));
        textAverage.setText(String.format(Locale.getDefault(), "$%.2f", average));
        textLowest.setText(String.format(Locale.getDefault(), "$%.2f", lowest));

        // Update chart
        if (entries.isEmpty()) {
            lineChart.setData(null);
            lineChart.invalidate();
        } else {
            LineDataSet dataSet = new LineDataSet(entries, "Cumulative Spending");
            dataSet.setColor(requireContext().getColor(R.color.primary));
            dataSet.setLineWidth(2.5f);
            dataSet.setCircleColor(requireContext().getColor(R.color.primary));
            dataSet.setCircleRadius(4f);
            dataSet.setDrawCircleHole(true);
            dataSet.setCircleHoleRadius(2f);
            dataSet.setDrawValues(false);
            dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            dataSet.setDrawFilled(true);
            dataSet.setFillColor(requireContext().getColor(R.color.primary_light));
            dataSet.setFillAlpha(50);

            LineData data = new LineData(dataSet);
            lineChart.setData(data);
            lineChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
            lineChart.getXAxis().setLabelCount(Math.min(labels.size(), 7));
            lineChart.animateX(1000);
            lineChart.invalidate();
        }
    }
}
