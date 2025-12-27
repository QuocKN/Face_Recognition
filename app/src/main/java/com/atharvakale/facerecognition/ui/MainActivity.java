package com.atharvakale.facerecognition.ui;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;

import com.atharvakale.facerecognition.R;
import com.atharvakale.facerecognition.camera.CameraHelper;
import com.atharvakale.facerecognition.data.FaceStorage;
import com.atharvakale.facerecognition.mqtt.MqttManager;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements MqttManager.MqttListener, CameraHelper.ResultCallback {

    private static final int CAMERA_REQ = 100;
    private static final int STORAGE_REQ = 101;

    // MQTT Configuration
    private static final String MQTT_BROKER_URL = "ssl://71e2c6502603479280eb36c1b5b12bfc.s1.eu.hivemq.cloud:8883";
    private static final String MQTT_TOPIC = "server/device01/face_data";
    private static final String MQTT_USERNAME = "mqttnkq";
    private static final String MQTT_PASSWORD = "Soict2025";

    // Recognition Smoothing Config
    private static final int RECOGNITION_HISTORY_SIZE = 7; // Use last 5 results
    private static final int MIN_CONFIDENT_HITS = 7;     // Need at least 3 same results to be confident

    private PreviewView previewView;
    private FaceAlignmentOverlayView overlayView;
    private ImageView facePreview;
    private TextView recoName;
    private Button recognizeBtn;
    private View rootView;

    private FaceStorage faceStorage;
    private CameraHelper cameraHelper;
    private MqttManager mqttManager;
    private Snackbar alignmentSnackbar;
    private String lastAlignmentReason = "";
    private final Deque<String> recentRecognitions = new ArrayDeque<>();

    // Pending save when requesting storage permission on older devices
    private Bitmap pendingSaveBitmap = null;
    private Bitmap pendingSaveBitmapScaled = null;
    private String pendingSaveName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rootView = findViewById(R.id.coordinatorLayout);
        previewView = findViewById(R.id.previewView);
        overlayView = findViewById(R.id.overlayView);
        facePreview = findViewById(R.id.imageView);
        recoName = findViewById(R.id.textView);
        recognizeBtn = findViewById(R.id.button3);
        Button cameraSwitchBtn = findViewById(R.id.button5);
        Button actionsBtn = findViewById(R.id.button2);

        faceStorage = new FaceStorage(this);
        cameraHelper = new CameraHelper(this, previewView, faceStorage, this);
        mqttManager = new MqttManager(this, MQTT_BROKER_URL, MQTT_TOPIC, MQTT_USERNAME, MQTT_PASSWORD, this);
        mqttManager.connect();

        recognizeBtn.setOnClickListener(v -> {
            cameraHelper.recognize = !cameraHelper.recognize;
            if (cameraHelper.recognize) {
                recognizeBtn.setText("Stop Recognize");
            } else {
                recognizeBtn.setText("Start Recognize");
            }
        });

        cameraSwitchBtn.setOnClickListener(v -> cameraHelper.toggleCamera());

        actionsBtn.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setItems(new String[]{"Clear All"}, (d, i) -> faceStorage.clear())
                        .show()
        );

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQ);
        } else {
            cameraHelper.start();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == CAMERA_REQ && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            cameraHelper.start();
        }

        // Handle storage permission result for legacy devices
        if (requestCode == STORAGE_REQ) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingSaveBitmap != null && pendingSaveName != null) {
                    saveBitmapToGallery(pendingSaveBitmap, pendingSaveName);
                    // also save scaled pending bitmap if present
                    if (pendingSaveBitmapScaled != null) saveBitmapToGallery(pendingSaveBitmapScaled, pendingSaveName + "_scaled");
                    pendingSaveBitmap = null;
                    pendingSaveBitmapScaled = null;
                    pendingSaveName = null;
                }
            } else {
                runOnUiThread(() -> Snackbar.make(rootView, "Storage permission denied. Can't save image.", Snackbar.LENGTH_LONG).show());
                pendingSaveBitmap = null;
                pendingSaveName = null;
            }
        }
    }

    // -- CameraHelper.ResultCallback --
    @Override
    public void onResult(Bitmap visualBitmap, Bitmap modelInputBitmap, float[] embedding, String name) {
        runOnUiThread(() -> {
            // Show the visual crop (larger square) in the ImageView
            facePreview.setImageBitmap(visualBitmap);

            // Add current recognition to history buffer
            if (recentRecognitions.size() >= RECOGNITION_HISTORY_SIZE) {
                recentRecognitions.poll(); // Remove the oldest result
            }
            recentRecognitions.offer(name);

            // Get the confident recognition result
            String confidentName = getConfidentRecognition();

            if (confidentName.equals("Unknown")) {
                recoName.setText("Unknown");
            } else {
                recoName.setText(confidentName);
                // If a known face is confidently recognized, stop recognition
                cameraHelper.recognize = false;
                recognizeBtn.setText("Start Recognize");
                Snackbar.make(rootView, "Đã nhận diện: " + confidentName, Snackbar.LENGTH_LONG).show();
                recentRecognitions.clear(); // Clear history after a confident recognition

                // Save both the visual crop and the scaled model-input bitmap to gallery
                if (visualBitmap != null) {
                    String displayName = confidentName + "_" + System.currentTimeMillis();
                    // On older devices we need WRITE_EXTERNAL_STORAGE permission
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            // Save pending and request permission
                            pendingSaveBitmap = visualBitmap;
                            pendingSaveBitmapScaled = modelInputBitmap;
                            pendingSaveName = displayName;
                            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_REQ);
                        } else {
                            // Save visual crop
                            saveBitmapToGallery(visualBitmap, displayName);
                            // Save scaled model input as separate file with suffix
                            if (modelInputBitmap != null) saveBitmapToGallery(modelInputBitmap, displayName + "_scaled");
                        }
                    } else {
                        // Scoped storage path (Android Q+)
                        saveBitmapToGallery(visualBitmap, displayName);
                        if (modelInputBitmap != null) saveBitmapToGallery(modelInputBitmap, displayName + "_scaled");
                    }
                }
            }
        });
    }

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

    // Save a bitmap to the device's gallery using MediaStore (handles scoped storage)
    private void saveBitmapToGallery(Bitmap bitmap, String displayName) {
        if (bitmap == null) return;

        ContentResolver resolver = getContentResolver();
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
        } catch (IOException e) {
            if (uri != null) resolver.delete(uri, null, null);
            final String msg = e.getMessage();
            runOnUiThread(() -> Snackbar.make(rootView, "Save failed: " + msg, Snackbar.LENGTH_LONG).show());
            return;
        } finally {
            if (out != null) try { out.close(); } catch (IOException ignored) {}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
        }

        runOnUiThread(() -> Snackbar.make(rootView, "Saved image to gallery", Snackbar.LENGTH_LONG).show());
    }

    @Override
    public void onFaceNotAligned(String reason) {
        runOnUiThread(() -> {
            overlayView.setFaceAligned(false);
            recoName.setText(""); // Clear name when face is not aligned

            if (reason.equals(lastAlignmentReason)) {
                return;
            }
            lastAlignmentReason = reason;

            if (alignmentSnackbar == null) {
                alignmentSnackbar = Snackbar.make(rootView, reason, Snackbar.LENGTH_INDEFINITE);
            } else {
                alignmentSnackbar.setText(reason);
            }

            if (!alignmentSnackbar.isShown()) {
                alignmentSnackbar.show();
            }
        });
    }

    @Override
    public void onFaceAligned() {
        runOnUiThread(() -> {
            overlayView.setFaceAligned(true);
            if (alignmentSnackbar != null && alignmentSnackbar.isShown()) {
                alignmentSnackbar.dismiss();
            }
            lastAlignmentReason = ""; // Reset reason when aligned
        });
    }

    // -- MqttManager.MqttListener --
    @Override
    public void onFaceReceived(String fullName, float[] embedding) {
        if (embedding != null) {
            faceStorage.addFace(fullName, embedding);
        }
    }

    @Override
    public void onMqttConnected(String serverURI) {
        runOnUiThread(() -> Snackbar.make(rootView, "Connected to: " + serverURI, Snackbar.LENGTH_LONG).show());
    }

    @Override
    public void onDataReceived(String message) {
        runOnUiThread(() -> Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show());
    }
}
