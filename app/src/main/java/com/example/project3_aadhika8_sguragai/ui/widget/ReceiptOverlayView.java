package com.example.project3_aadhika8_sguragai.ui.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.project3_aadhika8_sguragai.R;
import com.example.project3_aadhika8_sguragai.sense.capture.Box;
import com.example.project3_aadhika8_sguragai.sense.capture.Field;
import com.example.project3_aadhika8_sguragai.sense.capture.ReceiptExtraction;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the parse over the photographed receipt: dims the image, then walks the detected
 * regions in order — merchant, total, date — highlighting each.
 *
 * <p>This exists instead of a spinner. The scan is the moment the user decides whether to
 * trust the app, and showing which line each value came from is more convincing than any
 * progress indicator. Tap to skip.
 */
public class ReceiptOverlayView extends View {

    /** Roughly 1.8s for the full sequence, per the design. */
    private static final long PER_FIELD_MS = 520L;
    private static final long GAP_MS = 80L;

    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint reviewBoxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF scratch = new RectF();

    /** One highlightable region, with how far its reveal has progressed. */
    private static class Region {
        final Box box;
        final boolean needsReview;
        float alpha;

        Region(Box box, boolean needsReview) {
            this.box = box;
            this.needsReview = needsReview;
        }
    }

    private final List<Region> regions = new ArrayList<>();

    /** Size of the OCR coordinate space, so boxes can be mapped onto the displayed bitmap. */
    private int sourceWidth;
    private int sourceHeight;

    @Nullable
    private AnimatorSet sequence;
    @Nullable
    private Runnable onDone;

    public ReceiptOverlayView(Context context) {
        this(context, null);
    }

    public ReceiptOverlayView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ReceiptOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray a = context.obtainStyledAttributes(new int[]{
                androidx.appcompat.R.attr.colorPrimary,
                androidx.appcompat.R.attr.colorError,
                R.attr.senseHairline
        });
        int accent = a.getColor(0, Color.WHITE);
        int alert = a.getColor(1, Color.RED);
        a.recycle();

        scrimPaint.setColor(Color.argb(140, 11, 13, 12));

        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(dp(2));
        boxPaint.setColor(accent);

        // A field the parser is unsure about is ringed in the alert colour from the start,
        // so "check this one" is visible before the form is even read.
        reviewBoxPaint.setStyle(Paint.Style.STROKE);
        reviewBoxPaint.setStrokeWidth(dp(2));
        reviewBoxPaint.setColor(alert);

        setClickable(true);
    }

    /** Tell the overlay the pixel dimensions the bounding boxes were measured in. */
    public void setSourceSize(int width, int height) {
        this.sourceWidth = width;
        this.sourceHeight = height;
    }

    /**
     * Walk the extraction's fields in reading order. {@code onDone} fires when the sequence
     * finishes or is skipped, whichever comes first.
     */
    public void reveal(ReceiptExtraction extraction, @Nullable Runnable onDone) {
        this.onDone = onDone;
        regions.clear();

        addRegion(extraction.merchant);
        addRegion(extraction.total);
        addRegion(extraction.date);

        if (regions.isEmpty()) {
            finish();
            return;
        }

        AnimatorSet set = new AnimatorSet();
        List<Animator> steps = new ArrayList<>();
        for (Region r : regions) {
            ValueAnimator fade = ValueAnimator.ofFloat(0f, 1f);
            fade.setDuration(PER_FIELD_MS);
            fade.setStartDelay(GAP_MS);
            fade.addUpdateListener(a -> {
                r.alpha = (float) a.getAnimatedValue();
                invalidate();
            });
            steps.add(fade);
        }
        set.playSequentially(steps);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                finish();
            }
        });
        sequence = set;
        set.start();
    }

    private void addRegion(Field<?> field) {
        if (field != null && field.boundingBox != null) {
            regions.add(new Region(field.boundingBox, field.needsReview()));
        }
    }

    /** Skip the animation; every region snaps to fully drawn. */
    public void skip() {
        if (sequence != null && sequence.isRunning()) {
            sequence.cancel();
        }
        for (Region r : regions) {
            r.alpha = 1f;
        }
        invalidate();
        finish();
    }

    private void finish() {
        Runnable done = onDone;
        onDone = null;
        if (done != null) {
            done.run();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN
                && sequence != null && sequence.isRunning()) {
            skip();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (regions.isEmpty()) {
            return;
        }

        canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);

        float sx = sourceWidth > 0 ? getWidth() / (float) sourceWidth : 1f;
        float sy = sourceHeight > 0 ? getHeight() / (float) sourceHeight : 1f;
        float radius = dp(4);

        for (Region r : regions) {
            if (r.alpha <= 0f) {
                continue;
            }
            Paint paint = r.needsReview ? reviewBoxPaint : boxPaint;
            paint.setAlpha(Math.round(255 * Math.min(1f, r.alpha)));

            float pad = dp(3);
            scratch.set(
                    r.box.left * sx - pad,
                    r.box.top * sy - pad,
                    r.box.right * sx + pad,
                    r.box.bottom * sy + pad);
            canvas.drawRoundRect(scratch, radius, radius, paint);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (sequence != null) {
            sequence.cancel();
        }
        super.onDetachedFromWindow();
    }
}
