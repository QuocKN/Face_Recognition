package com.atharvakale.facerecognition.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.media.Image;
import android.util.Pair;
import android.util.Size;

import androidx.annotation.NonNull;
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

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CameraHelper {

    public interface ResultCallback {
        void onResult(Bitmap bitmap, float[] embedding, String name);
    }

    public interface AlignmentCallback {
        void onFaceAligned();
        void onFaceNotAligned(String reason);
    }

    private final Context context;
    private final PreviewView previewView;
    private final FaceStorage storage;
    private final ResultCallback callback;
    private AlignmentCallback alignmentCallback;

    private final FaceDetector detector;
    private final FaceClassifier classifier;
    public boolean recognize = true;
    private int camFace = CameraSelector.LENS_FACING_FRONT;
    public interface FrameListener {
        void onFrame(@NonNull ImageProxy imageProxy);
    }
    private ProcessCameraProvider cameraProvider;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private CameraSelector cameraSelector;
    private int camFacing = CameraSelector.LENS_FACING_FRONT;
    private FrameListener frameListener;
    private Executor listenerExecutor;
    private Executor analysisExecutor = Executors.newSingleThreadExecutor();
    private boolean flipX = false;
    public CameraHelper(Context ctx,
                        PreviewView pv,
                        FaceStorage storage,
                        ResultCallback cb) {

        this.context = ctx;
        this.previewView = pv;
        this.storage = storage;
        this.callback = cb;

        classifier = new FaceClassifier(ctx);
        detector = FaceDetection.getClient(
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .build()
        );
    }


    private void bindPreview(LifecycleOwner owner) {
        Preview preview = new Preview.Builder().build();

        cameraSelector = new CameraSelector.Builder().requireLensFacing(camFacing).build();

        // Bind preview to PreviewView - this is the key to display camera feed
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(analysisExecutor, new ImageAnalysis.Analyzer() {
            @androidx.camera.core.ExperimentalGetImage
            @Override
            public void analyze(@NonNull ImageProxy imageProxy) {
                if (frameListener != null) {
                    if (listenerExecutor != null) {
                        listenerExecutor.execute(new Runnable() {
                            @Override
                            public void run() {
                                frameListener.onFrame(imageProxy);
                            }
                        });
                    } else {
                        frameListener.onFrame(imageProxy);
                    }
                } else {
                    imageProxy.close();
                }
            }
        });

        // Bind both preview and imageAnalysis to lifecycle
        cameraProvider.bindToLifecycle(owner, cameraSelector, imageAnalysis, preview);
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void analyze(ImageProxy imageProxy) {
        if (imageProxy == null || imageProxy.getImage() == null) {
            if(imageProxy != null) imageProxy.close();
            return;
        }

        Image mediaImage = imageProxy.getImage();
        InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces.size() > 0) {
                        Face face = faces.get(0);

                        Bitmap bitmap = previewView.getBitmap();
                        if (bitmap == null) {
                            return;
                        }

                        // Check face alignment
                        String alignmentIssue = checkFaceAlignment(face, bitmap);
                        if (alignmentIssue != null) {
                            // Face not aligned
                            if (alignmentCallback != null) {
                                alignmentCallback.onFaceNotAligned(alignmentIssue);
                            }
                            return;
                        }

                        // Face is aligned
                        if (alignmentCallback != null) {
                            alignmentCallback.onFaceAligned();
                        }

                        try {
                            RectF boundingBox = new RectF(face.getBoundingBox());
                            Bitmap croppedBitmap = Bitmap.createBitmap(bitmap,
                                    (int) boundingBox.left,
                                    (int) boundingBox.top,
                                    (int) boundingBox.width(),
                                    (int) boundingBox.height());

                            if (recognize) {
                                float[] embedding = classifier.getEmbedding(croppedBitmap);
                                Pair<String, Float> nearest = storage.findNearest(embedding);
                                String name = nearest.first;

                                if (callback != null) {
                                    callback.onResult(croppedBitmap, embedding, name);
                                }
                            }
                        } catch(Exception e) {
                            // ignore errors related to bitmap cropping
                        }
                    } else {
                        // No face detected
                        if (alignmentCallback != null) {
                            alignmentCallback.onFaceNotAligned("Không phát hiện khuôn mặt");
                        }
                    }
                })
                .addOnCompleteListener(task -> imageProxy.close());
    }
    public void setFrameListener(FrameListener listener, Executor executor) {
        this.frameListener = listener;
        this.listenerExecutor = executor;
    }

    public void setAlignmentCallback(AlignmentCallback callback) {
        this.alignmentCallback = callback;
    }

    public boolean isFrontCamera() {
        return camFacing == CameraSelector.LENS_FACING_FRONT;
    }
    public void start(final LifecycleOwner owner) {
        cameraProviderFuture = ProcessCameraProvider.getInstance(context);
        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    cameraProvider = cameraProviderFuture.get();
                    bindPreview(owner);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, ContextCompat.getMainExecutor(context));
    }
    public void stop() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }
    public void switchCamera(LifecycleOwner owner) {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            if (camFacing == CameraSelector.LENS_FACING_BACK) {
                camFacing = CameraSelector.LENS_FACING_FRONT;
                // Do not mirror front camera output by default. Keep flipX=false.
                flipX = false;
            } else {
                camFacing = CameraSelector.LENS_FACING_BACK;
                flipX = false;
            }
            bindPreview(owner);
        }
    }

    /**
     * Check if face is properly aligned for recognition
     * @return null if aligned, error message if not aligned
     */
    public String checkFaceAlignment(Face face, Bitmap bitmap) {
        RectF boundingBox = new RectF(face.getBoundingBox());

        // Check 1: Face must be large enough (at least 30% of image width)
        float faceWidth = boundingBox.width();
        float imageWidth = bitmap.getWidth();
        float faceWidthRatio = faceWidth / imageWidth;

        if (faceWidthRatio < 0.3f) {
            return "Đưa mặt lại gần hơn";
        }

        if (faceWidthRatio > 0.85f) {
            return "Lùi ra xa một chút";
        }

        // Check 2: Face must be centered horizontally (within 30% from center)
        float faceCenterX = boundingBox.centerX();
        float imageCenterX = bitmap.getWidth() / 2f;
        float horizontalOffset = Math.abs(faceCenterX - imageCenterX) / imageWidth;

        if (horizontalOffset > 0.25f) {
            if (faceCenterX < imageCenterX) {
                return "Di chuyển sang phải";
            } else {
                return "Di chuyển sang trái";
            }
        }

        // Check 3: Face must be centered vertically (within 30% from center)
        float faceCenterY = boundingBox.centerY();
        float imageCenterY = bitmap.getHeight() / 2f;
        float verticalOffset = Math.abs(faceCenterY - imageCenterY) / bitmap.getHeight();

        if (verticalOffset > 0.25f) {
            if (faceCenterY < imageCenterY) {
                return "Di chuyển xuống";
            } else {
                return "Di chuyển lên";
            }
        }

        // Check 4: Head rotation angles (Euler Y for left-right, Euler Z for tilt)
        Float eulerY = face.getHeadEulerAngleY(); // Left-right rotation
        Float eulerZ = face.getHeadEulerAngleZ(); // Tilt rotation

        if (eulerY != null && Math.abs(eulerY) > 15f) {
            return "Nhìn thẳng vào camera";
        }

        if (eulerZ != null && Math.abs(eulerZ) > 15f) {
            return "Giữ đầu thẳng, không nghiêng";
        }

        // All checks passed
        return null;
    }
}
