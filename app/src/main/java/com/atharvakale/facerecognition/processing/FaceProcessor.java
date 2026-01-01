package com.atharvakale.facerecognition.processing;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.media.Image;

import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import com.atharvakale.facerecognition.ml.EmbeddingExtractor;
import com.atharvakale.facerecognition.ml.FaceDetectorManager;
import com.atharvakale.facerecognition.utils.ImageUtils;
import com.google.mlkit.vision.face.Face;

import java.io.IOException;
import java.util.concurrent.Executor;

public class FaceProcessor {
    private static final String TAG = "FaceProcessor";
    private final FaceDetectorManager detectorManager;
    private final EmbeddingExtractor embeddingExtractor;

    public interface Callback {
        void onNoFace();
        void onFaceDetected(Bitmap cropped, Bitmap scaled, float[] embedding);
        void onError(Exception e);
    }

    public interface CallbackWithFace {
        void onNoFace();
        void onFaceDetected(Face face, Bitmap frameBitmap, Bitmap cropped, Bitmap scaled, float[] embedding);
        void onError(Exception e);
    }

    public FaceProcessor(Activity activity, String modelFile, int inputSize, int outputSize, boolean useGpu) throws IOException {
        detectorManager = new FaceDetectorManager(activity);
        embeddingExtractor = EmbeddingExtractor.createFromAsset(activity, modelFile, inputSize, outputSize, useGpu);
    }

    // Process an ImageProxy (camera frame). This method closes imageProxy in all flows.
    @ExperimentalGetImage
    public void processFrame(ImageProxy imageProxy, Executor bgExecutor, Callback callback, boolean flipX) {
        try {
            Image mediaImage = imageProxy.getImage();
            if (mediaImage == null) {
                imageProxy.close();
                return;
            }

            int rotation = imageProxy.getImageInfo().getRotationDegrees();
            com.google.mlkit.vision.common.InputImage inputImage = com.google.mlkit.vision.common.InputImage.fromMediaImage(mediaImage, rotation);

            detectorManager.detect(inputImage, bgExecutor, faces -> {
                if (faces.size() == 0) {
                    try { callback.onNoFace(); } catch (Exception ignored) {}
                    imageProxy.close();
                    return;
                }

                Face face = faces.get(0);
                Bitmap frameBmp = ImageUtils.toBitmap(mediaImage);
                Bitmap frameBmp1 = ImageUtils.rotateBitmap(frameBmp, rotation, flipX, false);
                RectF boundingBox = new RectF(face.getBoundingBox());
                Bitmap croppedFace = ImageUtils.getCropBitmapByCPU(frameBmp1, boundingBox);
                Bitmap scaled = ImageUtils.getResizedBitmap(croppedFace, 112, 112);

                embeddingExtractor.extractAsync(scaled, bgExecutor, embedding -> {
                    try { callback.onFaceDetected(croppedFace, scaled, embedding); } catch (Exception ignored) {}
                    imageProxy.close();
                }, err -> {
                    try { callback.onError(err); } catch (Exception ignored) {}
                    imageProxy.close();
                });

            }, err -> {
                try { callback.onError(err); } catch (Exception ignored) {}
                imageProxy.close();
            });
        } catch (Exception e) {
            try { callback.onError(e); } catch (Exception ignored) {}
            try { imageProxy.close(); } catch (Exception ignored) {}
        }
    }

    public void close() {
        try { detectorManager.close(); } catch (Exception ignored) {}
        try { embeddingExtractor.close(); } catch (Exception ignored) {}
    }

    // Process frame with Face object included in callback for alignment checking
    @ExperimentalGetImage
    public void processFrameWithFace(ImageProxy imageProxy, Executor bgExecutor, CallbackWithFace callback, boolean flipX) {
        try {
            Image mediaImage = imageProxy.getImage();
            if (mediaImage == null) {
                imageProxy.close();
                return;
            }

            int rotation = imageProxy.getImageInfo().getRotationDegrees();
            com.google.mlkit.vision.common.InputImage inputImage = com.google.mlkit.vision.common.InputImage.fromMediaImage(mediaImage, rotation);

            detectorManager.detect(inputImage, bgExecutor, faces -> {
                if (faces.size() == 0) {
                    try { callback.onNoFace(); } catch (Exception ignored) {}
                    imageProxy.close();
                    return;
                }

                Face face = faces.get(0);
                Bitmap frameBmp = ImageUtils.toBitmap(mediaImage);
                Bitmap frameBmp1 = ImageUtils.rotateBitmap(frameBmp, rotation, flipX, false);
                RectF boundingBox = new RectF(face.getBoundingBox());
                Bitmap croppedFace = ImageUtils.getCropBitmapByCPU(frameBmp1, boundingBox);
                Bitmap scaled = ImageUtils.getResizedBitmap(croppedFace, 112, 112);

                embeddingExtractor.extractAsync(scaled, bgExecutor, embedding -> {
                    try { callback.onFaceDetected(face, frameBmp1, croppedFace, scaled, embedding); } catch (Exception ignored) {}
                    imageProxy.close();
                }, err -> {
                    try { callback.onError(err); } catch (Exception ignored) {}
                    imageProxy.close();
                });

            }, err -> {
                try { callback.onError(err); } catch (Exception ignored) {}
                imageProxy.close();
            });
        } catch (Exception e) {
            try { callback.onError(e); } catch (Exception ignored) {}
            try { imageProxy.close(); } catch (Exception ignored) {}
        }
    }

    // Process a Bitmap (e.g., from gallery): detect face, crop, scale, extract embedding.
    public void processBitmap(final Bitmap bitmap, final Executor bgExecutor, final Callback callback, boolean flipX) {
        if (bitmap == null) {
            try { callback.onNoFace(); } catch (Exception ignored) {}
            return;
        }

        com.google.mlkit.vision.common.InputImage inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0);
        detectorManager.detect(inputImage, bgExecutor, faces -> {
            if (faces.size() == 0) {
                try { callback.onNoFace(); } catch (Exception ignored) {}
                return;
            }
            Face face = faces.get(0);
            Bitmap frameBmp1 = ImageUtils.rotateBitmap(bitmap, 0, flipX, false);
            RectF boundingBox = new RectF(face.getBoundingBox());
            Bitmap croppedFace = ImageUtils.getCropBitmapByCPU(frameBmp1, boundingBox);
            Bitmap scaled = ImageUtils.getResizedBitmap(croppedFace, 112, 112);

            embeddingExtractor.extractAsync(scaled, bgExecutor, embedding -> {
                try { callback.onFaceDetected(croppedFace, scaled, embedding); } catch (Exception ignored) {}
            }, err -> {
                try { callback.onError(err); } catch (Exception ignored) {}
            });
        }, err -> {
            try { callback.onError(err); } catch (Exception ignored) {}
        });
    }
}
