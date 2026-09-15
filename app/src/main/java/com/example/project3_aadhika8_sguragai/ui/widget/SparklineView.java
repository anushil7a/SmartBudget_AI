package com.example.project3_aadhika8_sguragai.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.project3_aadhika8_sguragai.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The 30-day spending trend as a single unlabelled line.
 *
 * <p>Drawn with a {@link PathMeasure} trim so it appears to be written left to right — the
 * shape is the information, so it earns its space without axes or a legend.
 */
public class SparklineView extends View {

    private static final long DRAW_MS = 1100L;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baselinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path fullPath = new Path();
    private final Path visiblePath = new Path();
    private final PathMeasure measure = new PathMeasure();

    private List<Double> points = Collections.emptyList();
    private float progress = 1f;

    @Nullable
    private ValueAnimator animator;

    public SparklineView(Context context) {
        this(context, null);
    }

    public SparklineView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SparklineView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray a = context.obtainStyledAttributes(new int[]{
                androidx.appcompat.R.attr.colorPrimary,
                R.attr.senseHairline
        });
        int accent = a.getColor(0, Color.WHITE);
        int hairline = a.getColor(1, Color.DKGRAY);
        a.recycle();

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setColor(accent);
        linePaint.setStrokeWidth(dp(2));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        baselinePaint.setStyle(Paint.Style.STROKE);
        baselinePaint.setColor(hairline);
        baselinePaint.setStrokeWidth(dp(1));
        baselinePaint.setPathEffect(new DashPathEffect(new float[]{dp(3), dp(3)}, 0));
    }

    public void setPoints(List<Double> last30Days) {
        this.points = last30Days == null ? Collections.<Double>emptyList() : new ArrayList<>(last30Days);
        rebuildPath();
        animateIn();
    }

    private void animateIn() {
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DRAW_MS);
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildPath();
    }

    private void rebuildPath() {
        fullPath.reset();
        if (points.size() < 2 || getWidth() == 0 || getHeight() == 0) {
            return;
        }

        double max = Double.NEGATIVE_INFINITY;
        for (double v : points) {
            max = Math.max(max, v);
        }
        if (max <= 0) {
            max = 1;   // a flat month still draws a baseline rather than collapsing
        }

        float inset = dp(2);
        float usableH = getHeight() - inset * 2;
        float stepX = getWidth() / (float) (points.size() - 1);

        for (int i = 0; i < points.size(); i++) {
            float x = i * stepX;
            float y = inset + (float) (usableH * (1 - points.get(i) / max));
            if (i == 0) {
                fullPath.moveTo(x, y);
            } else {
                fullPath.lineTo(x, y);
            }
        }
        measure.setPath(fullPath, false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float baseY = getHeight() - dp(1);
        Path base = new Path();
        base.moveTo(0, baseY);
        base.lineTo(getWidth(), baseY);
        canvas.drawPath(base, baselinePaint);

        if (fullPath.isEmpty()) {
            return;
        }

        visiblePath.reset();
        measure.setPath(fullPath, false);
        measure.getSegment(0, measure.getLength() * progress, visiblePath, true);
        canvas.drawPath(visiblePath, linePaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
        }
        super.onDetachedFromWindow();
    }
}
