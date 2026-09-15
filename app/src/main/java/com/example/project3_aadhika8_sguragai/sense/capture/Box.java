package com.example.project3_aadhika8_sguragai.sense.capture;

/**
 * A line's bounds on the receipt.
 *
 * <p>Deliberately not {@code android.graphics.Rect}: the parser's merchant and date rules are
 * geometric (glyph height, position on the page), and Rect is a stub in JVM unit tests whose
 * methods throw. A plain value type keeps {@link ReceiptParser} testable without an emulator.
 * ML Kit's Rect is converted to this at the boundary.
 */
public class Box {

    public final int left;
    public final int top;
    public final int right;
    public final int bottom;

    public Box(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public int height() {
        return bottom - top;
    }

    public int width() {
        return right - left;
    }

    @Override
    public String toString() {
        return "Box(" + left + "," + top + "," + right + "," + bottom + ")";
    }
}
