package com.atharvakale.facerecognition.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
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

import java.util.List;
import java.util.concurrent.Executors;

public class CameraHelper {

    public interface ResultCallback {
        void onResult(Bitmap bitmap, float[] embedding, String name);
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
        detector = FaceDetection.getClient(
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .build()
        );
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
                    }
                })
                .addOnCompleteListener(task -> imageProxy.close());
    }

    public void toggleCamera() {
        if (camFace == CameraSelector.LENS_FACING_FRONT) {
            camFace = CameraSelector.LENS_FACING_BACK;
        } else {
            camFace = CameraSelector.LENS_FACING_FRONT;
        }
        start();
    }
}
