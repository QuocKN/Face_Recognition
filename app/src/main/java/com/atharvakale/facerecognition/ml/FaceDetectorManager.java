package com.atharvakale.facerecognition.ml;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class FaceDetectorManager {
    private FaceDetector detector;

    public FaceDetectorManager(Context context) {
        FaceDetectorOptions highAccuracyOpts =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .build();
        detector = FaceDetection.getClient(highAccuracyOpts);
    }

    public void detect(InputImage image, final Executor callbackExecutor, final Consumer<List<Face>> onSuccess, final Consumer<Exception> onFailure) {
        Task<List<Face>> task = detector.process(image);
        task.addOnSuccessListener(callbackExecutor, new OnSuccessListener<List<Face>>() {
            @Override
            public void onSuccess(List<Face> faces) {
                onSuccess.accept(faces);
            }
        }).addOnFailureListener(callbackExecutor, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                onFailure.accept(e);
            }
        });
    }

    public void close() {
        if (detector != null) {
            detector.close();
        }
    }
}

