package com.atharvakale.facerecognition.ui;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.atharvakale.facerecognition.data.FaceStorage;
import com.atharvakale.facerecognition.processing.FaceProcessor;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * FaceRecognition class handles face recognition logic with smoothing and confidence checking.
 * Similar to FaceRegistrar but for recognition instead of registration.
 */
public class FaceRecognition {
    private static final String TAG = "FaceRecognition";

    // Recognition Smoothing Config
    private static final int RECOGNITION_HISTORY_SIZE = 7; // Use last 7 results
    private static final int MIN_CONFIDENT_HITS = 7;       // Need at least 7 same results to be confident

    public static final int REQUEST_WRITE_STORAGE = 102;

    private final Activity activity;
    private final FaceStorage faceStorage;
    private final FaceProcessor faceProcessor;

    private final Deque<String> recentRecognitions = new ArrayDeque<>();
    private Bitmap pendingSaveBitmap = null;
    private String pendingSaveName = null;

    // Callback interface for recognition results
    public interface RecognitionCallback {
        void onRecognized(String name, float confidence);
        void onUnknown();
        void onConfidentRecognition(String name, Bitmap croppedBitmap, Bitmap scaledBitmap);
        void onFaceNotAligned(String reason);
        void onRecognitionStopped();
    }

    private RecognitionCallback callback;
    private boolean isRecognizing = false; // Recognition state

    public FaceRecognition(Activity activity, FaceStorage faceStorage, FaceProcessor faceProcessor) {
        this.activity = activity;
        this.faceStorage = faceStorage;
        this.faceProcessor = faceProcessor;
    }

    public void setRecognitionCallback(RecognitionCallback callback) {
        this.callback = callback;
    }

    /**
     * Start recognition process
     */
    public void startRecognition() {
        isRecognizing = true;
        recentRecognitions.clear();
        Log.d(TAG, "Recognition started");
    }

    /**
     * Stop recognition process
     */
    public void stopRecognition() {
        isRecognizing = false;
        recentRecognitions.clear();
        Log.d(TAG, "Recognition stopped");
    }

    /**
     * Check if recognition is currently active
     */
    public boolean isRecognizing() {
        return isRecognizing;
    }

    /**
     * Process a face detection result and perform recognition
     * @param cropped The cropped face bitmap
     * @param scaled The scaled bitmap (model input size)
     * @param embedding The face embedding vector
     */
    public void processRecognition(Bitmap cropped, Bitmap scaled, float[] embedding) {
        // Skip if recognition is not active
        if (!isRecognizing) {
            return;
        }

        if (embedding == null || faceStorage == null) {
            if (callback != null) {
                callback.onUnknown();
            }
            return;
        }

        // Find nearest match in storage
        Pair<String, Float> result = faceStorage.findNearest(embedding);
        String name = result.first;
        float distance = result.second;

        // Add current recognition to history buffer
        if (recentRecognitions.size() >= RECOGNITION_HISTORY_SIZE) {
            recentRecognitions.poll(); // Remove the oldest result
        }
        recentRecognitions.offer(name);

        // Calculate confidence (inverse of distance, normalized)
        float confidence = 1.0f - Math.min(distance, 1.0f);

        // Notify callback with current result
        if (callback != null) {
            if (name.equals("Unknown")) {
                callback.onUnknown();
            } else {
                callback.onRecognized(name, confidence);
            }
        }

        // Check if we have a confident recognition
        String confidentName = getConfidentRecognition();
        if (!confidentName.equals("Unknown") && callback != null) {
            // Stop recognition after confident result
            stopRecognition();

            callback.onConfidentRecognition(confidentName, cropped, scaled);

            // Save the recognized face image to gallery
            saveRecognizedFace(cropped, confidentName);
            saveRecognizedFace(scaled, confidentName);

            // Notify that recognition has stopped
            callback.onRecognitionStopped();

            // Clear history after confident recognition
            recentRecognitions.clear();
        }
    }

    /**
     * Get the most confident recognition result from recent history
     */
    private String getConfidentRecognition() {
        if (recentRecognitions.size() < RECOGNITION_HISTORY_SIZE) {
            return "Unknown"; // Not enough data to be confident
        }

        // Count occurrences of each name
        Map<String, Integer> counts = new HashMap<>();
        for (String recognition : recentRecognitions) {
            counts.put(recognition, counts.getOrDefault(recognition, 0) + 1);
        }

        // Find the name with the highest count
        String mostFrequentName = "Unknown";
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (!entry.getKey().equals("Unknown")) {
                if (entry.getValue() > maxCount) {
                    maxCount = entry.getValue();
                    mostFrequentName = entry.getKey();
                }
            }
        }

        // Check if the count meets our confidence threshold
        if (maxCount >= MIN_CONFIDENT_HITS) {
            return mostFrequentName;
        }

        return "Unknown";
    }

    /**
     * Save recognized face bitmap to gallery
     */
    private void saveRecognizedFace(Bitmap bitmap, String name) {
        if (bitmap == null) return;

        String filename = "recognized_" + name + "_" + System.currentTimeMillis();

        // Check storage permission for older Android versions
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                pendingSaveBitmap = bitmap;
                pendingSaveName = filename;
                activity.requestPermissions(
                    new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE
                );
                Toast.makeText(activity, "Cần quyền ghi bộ nhớ để lưu ảnh", Toast.LENGTH_LONG).show();
                return;
            }
        }

        saveBitmapToGallery(bitmap, filename);
    }

    /**
     * Save bitmap to gallery using MediaStore (handles scoped storage)
     */
    private void saveBitmapToGallery(Bitmap bitmap, String displayName) {
        if (bitmap == null) return;

        ContentResolver resolver = activity.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FaceRecognition");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        Uri uri = null;
        OutputStream out = null;
        try {
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Failed to create new MediaStore record.");
            out = resolver.openOutputStream(uri);
            if (out == null) throw new IOException("Failed to get output stream.");
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) {
                throw new IOException("Failed to compress bitmap.");
            }

            Log.d(TAG, "Saved recognized face: " + displayName);
            activity.runOnUiThread(() ->
                Toast.makeText(activity, "Đã lưu ảnh nhận diện: " + displayName, Toast.LENGTH_SHORT).show()
            );
        } catch (IOException e) {
            if (uri != null) resolver.delete(uri, null, null);
            Log.e(TAG, "Failed to save image", e);
            activity.runOnUiThread(() ->
                Toast.makeText(activity, "Lưu ảnh thất bại: " + e.getMessage(), Toast.LENGTH_SHORT).show()
            );
            return;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {}
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
        }
    }

    /**
     * Handle storage permission result
     */
    public boolean onRequestPermissionsResult(int requestCode, int[] grantResults) {
        if (requestCode == REQUEST_WRITE_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingSaveBitmap != null && pendingSaveName != null) {
                    saveBitmapToGallery(pendingSaveBitmap, pendingSaveName);
                }
            } else {
                Toast.makeText(activity, "Không thể lưu ảnh do thiếu quyền", Toast.LENGTH_SHORT).show();
            }
            pendingSaveBitmap = null;
            pendingSaveName = null;
            return true;
        }
        return false;
    }

    /**
     * Process bitmap from gallery and perform recognition
     */
    public void processBitmapAndRecognize(Bitmap bitmap, boolean flipX) {
        if (faceProcessor == null) {
            Toast.makeText(activity, "Processor unavailable", Toast.LENGTH_SHORT).show();
            return;
        }

        faceProcessor.processBitmap(bitmap, java.util.concurrent.Executors.newSingleThreadExecutor(),
            new FaceProcessor.Callback() {
                @Override
                public void onNoFace() {
                    activity.runOnUiThread(() ->
                        Toast.makeText(activity, "Không phát hiện khuôn mặt trong ảnh", Toast.LENGTH_SHORT).show()
                    );
                }

                @Override
                public void onFaceDetected(Bitmap cropped, Bitmap scaled, float[] embedding) {
                    activity.runOnUiThread(() -> processRecognition(cropped, scaled, embedding));
                }

                @Override
                public void onError(Exception e) {
                    activity.runOnUiThread(() ->
                        Toast.makeText(activity, "Xử lý ảnh thất bại: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
                }
            },
            flipX
        );
    }

    /**
     * Clear recognition history
     */
    public void clearHistory() {
        recentRecognitions.clear();
    }

    /**
     * Get current recognition history size
     */
    public int getHistorySize() {
        return recentRecognitions.size();
    }
}
