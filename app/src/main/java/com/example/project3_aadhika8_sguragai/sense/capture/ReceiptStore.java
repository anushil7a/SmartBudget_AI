package com.example.project3_aadhika8_sguragai.sense.capture;

import android.graphics.Bitmap;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * Where receipt images live: {@code filesDir/receipts/<uuid>.jpg}.
 *
 * <p>filesDir is app-private and already excluded from backup, so a photographed receipt does
 * not leak into cloud backups or become visible to other apps. The returned path is relative,
 * because an absolute path baked into a database row breaks the moment the app is reinstalled.
 */
public class ReceiptStore {

    private static final String TAG = "ReceiptStore";
    private static final String DIR = "receipts";
    private static final int MAX_EDGE = 2048;
    private static final int QUALITY = 85;

    private final File filesDir;

    public ReceiptStore(File filesDir) {
        this.filesDir = filesDir;
    }

    /**
     * @return path relative to filesDir, or null if the write failed.
     */
    public String save(Bitmap bitmap) {
        File dir = new File(filesDir, DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "could not create " + dir);
            return null;
        }

        String relative = DIR + "/" + UUID.randomUUID() + ".jpg";
        File out = new File(filesDir, relative);

        Bitmap scaled = downscale(bitmap);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, fos);
            return relative;
        } catch (IOException e) {
            Log.e(TAG, "could not write receipt", e);
            return null;
        } finally {
            if (scaled != bitmap) {
                scaled.recycle();
            }
        }
    }

    public File resolve(String relativePath) {
        return relativePath == null ? null : new File(filesDir, relativePath);
    }

    public boolean delete(String relativePath) {
        File f = resolve(relativePath);
        return f != null && f.exists() && f.delete();
    }

    /** Receipts are read once by OCR; storing a full-resolution photo is wasted space. */
    private Bitmap downscale(Bitmap src) {
        int longest = Math.max(src.getWidth(), src.getHeight());
        if (longest <= MAX_EDGE) {
            return src;
        }
        float ratio = MAX_EDGE / (float) longest;
        int w = Math.round(src.getWidth() * ratio);
        int h = Math.round(src.getHeight() * ratio);
        return Bitmap.createScaledBitmap(src, w, h, true);
    }
}
