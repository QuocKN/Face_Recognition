package com.atharvakale.facerecognition.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.media.Image;
import android.util.Pair;
import android.util.Size;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.atharvakale.facerecognition.data.FaceStorage;
import com.atharvakale.facerecognition.ml.FaceClassifier;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.concurrent.Executors;

public class CameraHelper {

    public interface ResultCallback {
        // changed to pass both the visual crop and the model-input-scaled bitmap
        void onResult(Bitmap visualBitmap, Bitmap modelInputBitmap, float[] embedding, String name);
        void onFaceNotAligned(String reason);
        void onFaceAligned();
    }

    private final Context context;
    private final PreviewView previewView;
    private final FaceStorage storage;
    private final ResultCallback callback;

    private final FaceDetector detector;
    private final FaceClassifier classifier;

    public boolean recognize = true;
    private int camFace = CameraSelector.LENS_FACING_FRONT;

    public CameraHelper(Context ctx,
                        PreviewView pv,
                        FaceStorage storage,
                        ResultCallback cb) {

        this.context = ctx;
        this.previewView = pv;
        this.storage = storage;
        this.callback = cb;

        classifier = new FaceClassifier(ctx);

        FaceDetectorOptions highAccuracyOpts =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                        .build();

        detector = FaceDetection.getClient(highAccuracyOpts);
    }

    public void start() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(context);

        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                bind(provider);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void bind(ProcessCameraProvider provider) {
        provider.unbindAll();

        Preview preview = new Preview.Builder().build();
        CameraSelector selector =
                new CameraSelector.Builder().requireLensFacing(camFace).build();

        ImageAnalysis analysis =
                new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

        analysis.setAnalyzer(Executors.newSingleThreadExecutor(), this::analyze);

        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        provider.bindToLifecycle((LifecycleOwner) context, selector, preview, analysis);
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void analyze(ImageProxy imageProxy) {
        if (imageProxy.getImage() == null) {
            if(imageProxy != null) imageProxy.close();
            return;
        }

        Image mediaImage = imageProxy.getImage();
        InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces.size() > 0) {
                        Face face = faces.get(0);

                        // Check all 3 head angles
                        float eulerX = face.getHeadEulerAngleX(); // Pitch (Up/Down)
                        float eulerY = face.getHeadEulerAngleY(); // Yaw (Left/Right)
                        float eulerZ = face.getHeadEulerAngleZ(); // Roll (Sideways tilt)

                        final float ANGLE_THRESHOLD = 10.0f;
                        boolean isFaceStraight = Math.abs(eulerX) < ANGLE_THRESHOLD &&
                                               Math.abs(eulerY) < ANGLE_THRESHOLD &&
                                               Math.abs(eulerZ) < ANGLE_THRESHOLD;

                        RectF boundingBox = new RectF(face.getBoundingBox());
                        // Check if the face is reasonably centered and sized within the preview
                        float x = boundingBox.centerX();
                        float faceWidth = boundingBox.width();
                        float previewWidth = previewView.getWidth();

                        boolean isCentered = (previewWidth > 0) && (x > previewWidth * 0.25f) && (x < previewWidth * 0.75f);
                        boolean isSized = (previewWidth > 0) && (faceWidth / previewWidth) > 0.4f;

                        String reason = "";
                        if (!isFaceStraight) reason = "Vui lòng nhìn thẳng vào camera!";
                        else if (!isCentered) reason = "Vui lòng căn mặt vào giữa!";
                        else if (!isSized) reason = "Vui lòng đưa mặt lại gần hơn!";

                        if (isFaceStraight && isCentered && isSized) {
                            if (callback != null) {
                                callback.onFaceAligned();
                            }

                            if (recognize) {
                                try {
                                    Bitmap bitmap = previewView.getBitmap();
                                    if (bitmap == null) return;

                                    // Create an elliptical crop of the detected face. Map the detector bounding box
                                    // (which is in the InputImage coordinate space) to the preview bitmap coordinate space
                                    // using the media image dimensions and rotation.
                                    int rotation = imageProxy.getImageInfo().getRotationDegrees();
                                    Bitmap croppedBitmap = createEllipticalFaceCrop(bitmap, boundingBox, mediaImage, rotation);
                                    if (croppedBitmap == null) return;

                                    // --- NEW: resize the elliptical crop to the model input size ---
                                    Bitmap scaledForModel = classifier.resizeToModelInput(croppedBitmap);
                                    if (scaledForModel == null) return;

                                    float[] embedding = classifier.getEmbedding(scaledForModel);
                                    Pair<String, Float> nearest = storage.findNearest(embedding);

                                    if (callback != null) {
                                        // Pass the cropped square bitmap (visual) to the callback for UI/display/save,
                                        // and also pass scaledForModel which is what was given to the model.
                                        callback.onResult(croppedBitmap, scaledForModel, embedding, nearest.first);
                                    }
                                } catch (Exception e) {
                                    // ignore
                                }
                            }
                        } else {
                            if (callback != null) {
                                callback.onFaceNotAligned(reason);
                            }
                        }
                    } else {
                        if (callback != null) {
                            callback.onFaceNotAligned("Không tìm thấy khuôn mặt");
                        }
                    }
                })
                .addOnCompleteListener(task -> imageProxy.close());
    }

    // Map the detected face bounding box from image coordinates to the preview bitmap, then produce
    // an oval (elliptical) crop of that region. Returns a bitmap with ARGB_8888 and transparent corners.
    private Bitmap createEllipticalFaceCrop(Bitmap previewBitmap, RectF faceBox, Image mediaImage, int rotationDegrees) {
        if (previewBitmap == null || faceBox == null || mediaImage == null) return null;

        // Get the source image dimensions (the mediaImage is in sensor orientation before rotation)
        int imgWidth = mediaImage.getWidth();
        int imgHeight = mediaImage.getHeight();

        // The detector's bounding box coordinates are relative to the InputImage after applying rotation.
        // If the image was rotated by 90 or 270, the width/height are swapped in the rotated coordinate space.
        boolean rotated = (rotationDegrees == 90 || rotationDegrees == 270);
        int rotatedImgWidth = rotated ? imgHeight : imgWidth;
        int rotatedImgHeight = rotated ? imgWidth : imgHeight;

        // Scale factors from detector image space to preview bitmap space
        float scaleX = previewBitmap.getWidth() / (float) rotatedImgWidth;
        float scaleY = previewBitmap.getHeight() / (float) rotatedImgHeight;

        // Optionally expand the bounding box slightly to include full head/edges
        float paddingFactor = 0.20f; // 20% padding
        float cx = faceBox.centerX();
        float cy = faceBox.centerY();
        float halfW = faceBox.width() / 2f;
        float halfH = faceBox.height() / 2f;
        float paddedLeft = cx - halfW * (1f + paddingFactor);
        float paddedTop = cy - halfH * (1f + paddingFactor);
        float paddedRight = cx + halfW * (1f + paddingFactor);
        float paddedBottom = cy + halfH * (1f + paddingFactor);

        // Map padded rect to preview bitmap coordinates
        int left = Math.max(0, Math.round(paddedLeft * scaleX));
        int top = Math.max(0, Math.round(paddedTop * scaleY));
        int right = Math.min(previewBitmap.getWidth(), Math.round(paddedRight * scaleX));
        int bottom = Math.min(previewBitmap.getHeight(), Math.round(paddedBottom * scaleY));

        // If using front camera, the preview bitmap may be mirrored horizontally; flip coordinates
        if (camFace == CameraSelector.LENS_FACING_FRONT) {
            int mirroredLeft = previewBitmap.getWidth() - right;
            int mirroredRight = previewBitmap.getWidth() - left;
            left = Math.max(0, mirroredLeft);
            right = Math.min(previewBitmap.getWidth(), mirroredRight);
        }

        int w = right - left;
        int h = bottom - top;
        if (w <= 0 || h <= 0) return null;

        // Make square: use the larger dimension and center the crop around the face
        int size = Math.max(w, h);

        // Compute new left/top to center the square over the original rect
        int centerX = left + w / 2;
        int centerY = top + h / 2;
        int newLeft = centerX - size / 2;
        int newTop = centerY - size / 2;

        // Clamp to bitmap bounds
        if (newLeft < 0) newLeft = 0;
        if (newTop < 0) newTop = 0;
        if (newLeft + size > previewBitmap.getWidth()) newLeft = previewBitmap.getWidth() - size;
        if (newTop + size > previewBitmap.getHeight()) newTop = previewBitmap.getHeight() - size;

        // Final safety checks
        if (size <= 0) return null;
        if (newLeft < 0 || newTop < 0) return null;

        // Crop the square region and return it (no elliptical mask)
        try {
            Bitmap rectCrop = Bitmap.createBitmap(previewBitmap, newLeft, newTop, size, size);
            // Ensure the crop is in ARGB_8888 so further processing is consistent
            if (rectCrop.getConfig() != Bitmap.Config.ARGB_8888) {
                Bitmap converted = rectCrop.copy(Bitmap.Config.ARGB_8888, false);
                rectCrop.recycle();
                return converted;
            }
            return rectCrop;
        } catch (Exception e) {
            return null;
        }
    }

    public void toggleCamera() {
        camFace = (camFace == CameraSelector.LENS_FACING_FRONT) ? CameraSelector.LENS_FACING_BACK : CameraSelector.LENS_FACING_FRONT;
        start();
    }
}
