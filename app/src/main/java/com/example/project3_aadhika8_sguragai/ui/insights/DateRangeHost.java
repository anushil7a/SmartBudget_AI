package com.example.project3_aadhika8_sguragai.ui.insights;

/**
 * The date window the chart fragments should draw.
 *
 * <p>The fragments used to cast their activity to a concrete {@code ChartsActivity} to read
 * this, which tied them to one host. An interface lets any screen present them.
 */
public interface DateRangeHost {

    /** yyyy-MM-dd */
    String getStartDate();

    /** yyyy-MM-dd */
    String getEndDate();
}
