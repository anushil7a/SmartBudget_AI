package com.example.project3_aadhika8_sguragai.sense.capture;

import android.graphics.Bitmap;
import android.graphics.Rect;

import androidx.annotation.NonNull;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The ML Kit half of receipt capture: run recognition, hand the geometry to
 * {@link ReceiptParser}.
 *
 * <p>The split matters. This class needs a device; the parser does not, and the parser is
 * where every decision worth testing lives. This one consumes {@link Text.Line} rather than
 * {@code text.getText()} because the merchant rule reads glyph height and the reveal
 * animation needs each field's bounds.
 */
public class ReceiptScanner {

    public interface Callback {
        void onExtracted(ReceiptExtraction extraction);

        void onError(Exception e);
    }

    private final TextRecognizer recognizer =
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

    private final ReceiptParser parser = new ReceiptParser();

    public void scan(@NonNull Bitmap bitmap, @NonNull Callback callback) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        recognizer.process(image)
                .addOnSuccessListener(text -> {
                    try {
                        callback.onExtracted(parser.parseText(toLines(text)));
                    } catch (Exception e) {
                        callback.onError(e);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    /** Flatten ML Kit's block/line tree into reading order, carrying each line's bounds. */
    private List<ReceiptParser.ParsedLine> toLines(Text text) {
        List<ReceiptParser.ParsedLine> lines = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                Rect r = line.getBoundingBox();
                Box box = r == null ? null : new Box(r.left, r.top, r.right, r.bottom);
                lines.add(new ReceiptParser.ParsedLine(line.getText(), box));
            }
        }
        // ML Kit returns blocks in its own order; the parser's bottom-up walk and its
        // top-of-page merchant rule both assume the page reads top to bottom.
        lines.sort(Comparator.comparingInt(l -> l.box == null ? 0 : l.box.top));
        return lines;
    }

    public void close() {
        recognizer.close();
    }
}
