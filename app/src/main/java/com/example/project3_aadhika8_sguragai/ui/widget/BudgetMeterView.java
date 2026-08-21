package com.example.project3_aadhika8_sguragai.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

import com.example.project3_aadhika8_sguragai.R;

/**
 * A single horizontal bar: how much of the month's budget is gone.
 *
 * <p>Sweeps from zero on load, and shifts from the accent toward the alert colour as it
 * crosses 80% and again at 100% — so the colour itself carries the warning rather than
 * relying on the user reading a number.
 */
public class BudgetMeterView extends View {

    private static final float WARN_AT = 0.80f;
    private static final long SWEEP_MS = 900L;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private int accentColor;
    private int alertColor;
    private int trackColor;

    /** The value being drawn right now — animated toward {@link #targetFraction}. */
    private float drawnFraction;
    private float targetFraction;

    @Nullable
    private ValueAnimator animator;

    public BudgetMeterView(Context context) {
        this(context, null);
    }

    public BudgetMeterView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BudgetMeterView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        resolveThemeColors(context);
        trackPaint.setColor(trackColor);
        fillPaint.setColor(accentColor);
    }

    private void resolveThemeColors(Context context) {
        TypedArray a = context.obtainStyledAttributes(new int[]{
                androidx.appcompat.R.attr.colorPrimary,
                androidx.appcompat.R.attr.colorError,
                R.attr.senseHairline
        });
        accentColor = a.getColor(0, Color.WHITE);
        alertColor = a.getColor(1, Color.RED);
        trackColor = a.getColor(2, Color.DKGRAY);
        a.recycle();
    }

    /**
     * @param spent  amount spent this period
     * @param budget the budget for the period; zero or negative renders an empty meter
     */
    public void setValue(double spent, double budget) {
        float fraction = budget <= 0 ? 0f : (float) (spent / budget);
        targetFraction = Math.max(0f, fraction);
        animateTo(targetFraction);
    }

    private void animateTo(float target) {
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(drawnFraction, target);
        animator.setDuration(SWEEP_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            drawnFraction = (float) a.getAnimatedValue();
            fillPaint.setColor(colorFor(drawnFraction));
            invalidate();
        });
        animator.start();
    }

    /**
     * Accent below 80%, blending toward alert across 80–100%, fully alert once over budget.
     */
    private int colorFor(float fraction) {
        if (fraction <= WARN_AT) {
            return accentColor;
        }
        if (fraction >= 1f) {
            return alertColor;
        }
        float t = (fraction - WARN_AT) / (1f - WARN_AT);
        return blend(accentColor, alertColor, t);
    }

    private int blend(int from, int to, float t) {
        return Color.rgb(
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float h = getHeight();
        float w = getWidth();
        float radius = h / 2f;

        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, radius, radius, trackPaint);

        // Over budget still fills the bar completely; the colour says the rest.
        float filled = Math.min(1f, drawnFraction) * w;
        if (filled > 0) {
            rect.set(0, 0, filled, h);
            canvas.drawRoundRect(rect, radius, radius, fillPaint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
        }
        super.onDetachedFromWindow();
    }
}
