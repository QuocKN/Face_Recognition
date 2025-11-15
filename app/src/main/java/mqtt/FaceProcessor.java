package mqtt;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.List;

public class FaceProcessor {

    private static final String TAG = "FaceProcessor";
    private final FaceDetector detector;

    public interface OnProcessedListener {
        void onFaceReady(Bitmap faceBitmap);
        void onNoFaceFound();
    }

    public FaceProcessor(Context context) {
        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                        .build();

        detector = FaceDetection.getClient(options);
    }

    public void process(@NonNull Bitmap inputBitmap, boolean flipX, OnProcessedListener listener) {
        try {
            InputImage image = InputImage.fromBitmap(inputBitmap, 0);
            detector.process(image)
                    .addOnSuccessListener(faces -> {
                        if (faces.isEmpty()) {
                            listener.onNoFaceFound();
                            return;
                        }

                        Face face = faces.get(0);
                        RectF boundingBox = new RectF(face.getBoundingBox());

                        Bitmap rotated = rotateBitmap(inputBitmap, 0, flipX, false);
                        Bitmap cropped = getCropBitmapByCPU(rotated, boundingBox);
                        Bitmap scaled = getResizedBitmap(cropped, 112, 112);

                        listener.onFaceReady(scaled);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Face detection failed", e);
                        listener.onNoFaceFound();
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error processing face", e);
            listener.onNoFaceFound();
        }
    }

    // --- Các hàm hỗ trợ (bạn đã có trong MainActivity, có thể move qua đây luôn) ---
    private Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        return Bitmap.createScaledBitmap(bm, newWidth, newHeight, true);
    }

    private Bitmap getCropBitmapByCPU(Bitmap source, RectF rect) {
        int left = Math.max(0, (int) rect.left);
        int top = Math.max(0, (int) rect.top);
        int right = Math.min(source.getWidth(), (int) rect.right);
        int bottom = Math.min(source.getHeight(), (int) rect.bottom);

        if (left >= right || top >= bottom)
            return source;

        return Bitmap.createBitmap(source, left, top, right - left, bottom - top);
    }

    private Bitmap rotateBitmap(Bitmap source, float angle, boolean flipX, boolean flipY) {
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.postRotate(angle);
        matrix.preScale(flipX ? -1.0f : 1.0f, flipY ? -1.0f : 1.0f);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }
}
