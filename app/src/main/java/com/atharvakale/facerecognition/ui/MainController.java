package com.atharvakale.facerecognition.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.camera.view.PreviewView;
import androidx.lifecycle.LifecycleOwner;

import com.atharvakale.facerecognition.camera.CameraHelper;
import com.atharvakale.facerecognition.data.FaceStorage;
import com.atharvakale.facerecognition.processing.FaceProcessor;
import com.atharvakale.facerecognition.mqtt.MqttManager;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainController {
    private final Activity activity;
    private final String modelFile;
    private final int inputSize;
    private final int outputSize;
    private final MqttManager mqttManager;

    // UI
    private PreviewView previewView;
    private ImageView facePreview;
    private TextView recoName;
    private android.widget.Button recognizeBtn;

    private CameraHelper cameraManager;
    private FaceProcessor faceProcessor;
    private FaceRecognition faceRecognition;

    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();

    // Recognition state
    private boolean isRecognitionActive = false;
    private boolean isFaceAligned = false;

    // Last captured
    private Bitmap lastCroppedBitmap;
    private Bitmap lastScaledBitmap;
    private float[] embeddingGlobal;
    private FaceStorage faceStorage;

    public MainController(Activity activity, String modelFile, int inputSize, int outputSize, MqttManager mqttManager) {
        this.activity = activity;
        this.modelFile = modelFile;
        this.inputSize = inputSize;
        this.outputSize = outputSize;
        this.mqttManager = mqttManager;
    }

    @androidx.camera.core.ExperimentalGetImage
    public void start() {
        // bind views
        previewView = activity.findViewById(com.atharvakale.facerecognition.R.id.previewView);
        // Ensure preview view is not mirrored
        if (previewView != null) previewView.setScaleX(1f);
        facePreview = activity.findViewById(com.atharvakale.facerecognition.R.id.imageView);
        if (facePreview != null) facePreview.setScaleX(1f);
        recoName = activity.findViewById(com.atharvakale.facerecognition.R.id.textView);
        recognizeBtn = activity.findViewById(com.atharvakale.facerecognition.R.id.button3);

        // Initialize FaceStorage
        faceStorage = new FaceStorage(activity);

        cameraManager = new CameraHelper(
                this.activity,
                previewView,
                faceStorage,
                (bitmap, embedding, name) -> {
                    facePreview.setImageBitmap(bitmap);
                    recoName.setText(name);
                }
        );

        try {
            faceProcessor = new FaceProcessor(activity, modelFile, inputSize, outputSize, false);
        } catch (IOException e) {
            Log.e("MainController", "Failed to create FaceProcessor", e);
        }

        // Initialize FaceRecognition
        faceRecognition = new FaceRecognition(activity, faceStorage, faceProcessor);

        // Setup recognition callback
        faceRecognition.setRecognitionCallback(new FaceRecognition.RecognitionCallback() {
            @Override
            public void onRecognized(String name, float confidence) {
                // Update UI with current recognition result
                activity.runOnUiThread(() ->
                    recoName.setText(name + " (" + String.format("%.2f", confidence) + ")")
                );
            }

            @Override
            public void onUnknown() {
                activity.runOnUiThread(() ->
                    recoName.setText("Unknown")
                );
            }

            @Override
            public void onConfidentRecognition(String name, Bitmap croppedBitmap, Bitmap scaledBitmap) {
                // Confident recognition - show result and stop
                activity.runOnUiThread(() -> {
                    recoName.setText("✓ " + name);
                    facePreview.setImageBitmap(croppedBitmap);
                    android.widget.Toast.makeText(activity, "Đã nhận diện: " + name, android.widget.Toast.LENGTH_LONG).show();
                    Log.d("MainController", "Confident recognition: " + name);
                });
            }

            @Override
            public void onFaceNotAligned(String reason) {
                // Show alignment warning
                activity.runOnUiThread(() -> {
                    recoName.setText(reason);
                });
            }

            @Override
            public void onRecognitionStopped() {
                // Update button state when recognition stops
                activity.runOnUiThread(() -> {
                    isRecognitionActive = false;
                    if (recognizeBtn != null) {
                        recognizeBtn.setText("Start Recognition");
                    }
                });
            }
        });

        final FaceProcessor fp = faceProcessor;

        cameraManager.setFrameListener(imageProxy -> {
            if (fp == null) {
                imageProxy.close();
                return;
            }
            fp.processFrameWithFace(imageProxy, bgExecutor, new FaceProcessor.CallbackWithFace() {
                @Override
                public void onNoFace() {
                    isFaceAligned = false;
                    activity.runOnUiThread(() -> {
                        if (isRecognitionActive) {
                            recoName.setText("Không phát hiện khuôn mặt");
                        } else if (embeddingGlobal == null) {
                            recoName.setText("Add Face");
                        } else {
                            recoName.setText("Ấn Start để nhận diện");
                        }
                    });
                }

                @Override
                public void onFaceDetected(com.google.mlkit.vision.face.Face face, android.graphics.Bitmap frameBitmap, android.graphics.Bitmap cropped, android.graphics.Bitmap scaled, float[] embedding) {
                    // Check face alignment first
                    String alignmentIssue = cameraManager.checkFaceAlignment(face, frameBitmap);

                    if (alignmentIssue != null) {
                        // Face not aligned
                        isFaceAligned = false;
                        if (isRecognitionActive) {
                            activity.runOnUiThread(() -> recoName.setText(alignmentIssue));
                        }
                        // Still show the preview but don't recognize
                        activity.runOnUiThread(() -> {
                            facePreview.setImageBitmap(scaled);
                            try {
                                if (cameraManager != null && cameraManager.isFrontCamera()) {
                                    facePreview.setScaleX(-1f);
                                } else {
                                    facePreview.setScaleX(1f);
                                }
                            } catch (Exception e) {
                                // ignore
                            }

                            if (!isRecognitionActive) {
                                recoName.setText("Ấn Start để nhận diện");
                            }
                        });
                        return;
                    }

                    // Face is aligned
                    isFaceAligned = true;

                    // keep references (copy crop to avoid recycle bugs)
                    try {
                        if (cameraManager != null && cameraManager.isFrontCamera()) {
                            Matrix matrix = new Matrix();
                            matrix.preScale(-1.0f, 1.0f);
                            lastCroppedBitmap = Bitmap.createBitmap(cropped, 0, 0, cropped.getWidth(), cropped.getHeight(), matrix, true);
                        } else {
                            lastCroppedBitmap = cropped.copy(Bitmap.Config.ARGB_8888, true);
                        }
                    } catch (Exception e) {
                        lastCroppedBitmap = cropped;
                    }
                    lastScaledBitmap = scaled;
                    embeddingGlobal = embedding;

                    // Only process recognition if both conditions are met
                    if (isRecognitionActive && isFaceAligned) {
                        faceRecognition.processRecognition(cropped, scaled, embedding);
                    }

                    // Display the scaled bitmap; if front camera is active, mirror the ImageView so the face looks correct
                    activity.runOnUiThread(() -> {
                        facePreview.setImageBitmap(scaled);
                        try {
                            if (cameraManager != null && cameraManager.isFrontCamera()) {
                                facePreview.setScaleX(-1f);
                            } else {
                                facePreview.setScaleX(1f);
                            }
                        } catch (Exception e) {
                            // ignore
                        }

                        // Show appropriate message based on state
                        if (!isRecognitionActive) {
                            recoName.setText("Ấn Start để nhận diện");
                        }
                        // If recognizing and aligned, let FaceRecognition callbacks handle the text
                    });
                }

                @Override
                public void onError(Exception e) {
                    Log.e("MainController", "Processor error", e);
                }
            }, false);
        }, bgExecutor);

        cameraManager.start((LifecycleOwner) activity);
    }

    public void stop() {
        if (cameraManager != null) cameraManager.stop();
        if (faceProcessor != null) faceProcessor.close();
        if (faceRecognition != null) faceRecognition.clearHistory();
        bgExecutor.shutdownNow();
    }

    // Add face data to storage (called from MainActivity when receiving MQTT data)
    public void addFaceToStorage(String name, float[] embedding) {
        if (faceStorage != null && embedding != null) {
            faceStorage.addFace(name, embedding);
            Log.d("MainController", "Added face to storage: " + name);
        }
    }

    // Handle permission results (delegate to FaceRecognition)
    public void onRequestPermissionsResult(int requestCode, int[] grantResults) {
        if (faceRecognition != null) {
            faceRecognition.onRequestPermissionsResult(requestCode, grantResults);
        }
    }

    // Toggle recognition on/off. Called from UI button.
    public void toggleRecognition() {
        if (faceRecognition != null) {
            if (isRecognitionActive) {
                // Stop recognition
                faceRecognition.stopRecognition();
                isRecognitionActive = false;
                if (recognizeBtn != null) {
                    recognizeBtn.setText("Start Recognition");
                }
                activity.runOnUiThread(() -> {
                    recoName.setText("Đã dừng nhận diện");
                });
                Log.d("MainController", "Recognition stopped by user");
            } else {
                // Start recognition
                faceRecognition.startRecognition();
                isRecognitionActive = true;
                if (recognizeBtn != null) {
                    recognizeBtn.setText("Stop Recognition");
                }
                activity.runOnUiThread(() -> {
                    recoName.setText("Đang nhận diện...");
                });
                Log.d("MainController", "Recognition started by user");
            }
        }
    }

    // Toggle camera (front/back). Called from UI.
    public void toggleCamera() {
        if (cameraManager != null) {
            try {
                cameraManager.switchCamera((LifecycleOwner) activity);
                // Clear recognition history when switching cameras
                if (faceRecognition != null) {
                    faceRecognition.clearHistory();
                }
                // After switching camera, make sure views are not mirrored
                if (previewView != null) previewView.setScaleX(1f);
                if (facePreview != null) facePreview.setScaleX(1f);
            } catch (Exception e) {
                Log.e("MainController", "Failed to switch camera", e);
            }
        }
    }

    // Show all stored faces in an alert dialog
    public void showStoredFaces() {
        if (faceStorage == null) {
            android.widget.Toast.makeText(activity, "Storage not initialized", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        java.util.HashMap<String, Integer> allFaces = faceStorage.getAllFaces();
        int totalFaces = faceStorage.getFaceCount();
        int totalEmbeddings = faceStorage.getTotalEmbeddingCount();

        if (totalFaces == 0) {
            new androidx.appcompat.app.AlertDialog.Builder(activity)
                    .setTitle("Dữ liệu trong bộ nhớ")
                    .setMessage("Chưa có dữ liệu khuôn mặt nào được lưu.\n\nVui lòng nhận dữ liệu từ MQTT hoặc đăng ký khuôn mặt mới.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        // Build the message with all faces
        StringBuilder message = new StringBuilder();
        message.append("📊 Tổng số người: ").append(totalFaces).append("\n");
        message.append("📦 Tổng số embeddings: ").append(totalEmbeddings).append("\n\n");
        message.append("Danh sách:\n");
        message.append("─────────────────────\n");

        // Sort by name for easier reading
        java.util.List<java.util.Map.Entry<String, Integer>> sortedList = new java.util.ArrayList<>(allFaces.entrySet());
        sortedList.sort(java.util.Map.Entry.comparingByKey());

        int index = 1;
        for (java.util.Map.Entry<String, Integer> entry : sortedList) {
            message.append(index++).append(". ");
            message.append(entry.getKey());
            message.append(" (").append(entry.getValue()).append(" embedding");
            if (entry.getValue() > 1) {
                message.append("s");
            }
            message.append(")\n");
        }

        // Show dialog
        new androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle("Dữ liệu trong bộ nhớ")
                .setMessage(message.toString())
                .setPositiveButton("OK", null)
                .setNeutralButton("Clear All", (dialog, which) -> {
                    // Show confirmation dialog before clearing
                    new androidx.appcompat.app.AlertDialog.Builder(activity)
                            .setTitle("Xác nhận xóa")
                            .setMessage("Bạn có chắc chắn muốn xóa TẤT CẢ dữ liệu khuôn mặt?\n\nHành động này không thể hoàn tác!")
                            .setPositiveButton("Xóa", (d, w) -> {
                                faceStorage.clear();
                                android.widget.Toast.makeText(activity, "Đã xóa tất cả dữ liệu", android.widget.Toast.LENGTH_SHORT).show();
                                Log.d("MainController", "All face data cleared");
                            })
                            .setNegativeButton("Hủy", null)
                            .show();
                })
                .show();
    }

    /**
     * Check if face is properly aligned for recognition
     * @return null if aligned, error message if not aligned
     */

}
