package com.atharvakale.facerecognition.ml;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.Executor;

public class EmbeddingExtractor {
    private Interpreter tfLite;
    private int inputSize;
    private boolean isModelQuantized;
    private float IMAGE_MEAN = 128.0f;
    private float IMAGE_STD = 128.0f;
    private int OUTPUT_SIZE;

    private EmbeddingExtractor(Interpreter interpreter, int inputSize, int outputSize, boolean isQuantized) {
        this.tfLite = interpreter;
        this.inputSize = inputSize;
        this.OUTPUT_SIZE = outputSize;
        this.isModelQuantized = isQuantized;
    }

    public static EmbeddingExtractor createFromAsset(Activity activity, String modelAssetName, int inputSize, int outputSize, boolean isQuantized) throws IOException {
        MappedByteBuffer modelFile = loadModelFile(activity, modelAssetName);
        Interpreter interpreter = new Interpreter(modelFile);
        return new EmbeddingExtractor(interpreter, inputSize, outputSize, isQuantized);
    }

    private static MappedByteBuffer loadModelFile(Activity activity, String MODEL_FILE) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public float[] extract(Bitmap bitmap) {
        // Prepare ByteBuffer
        ByteBuffer imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4);
        imgData.order(ByteOrder.nativeOrder());

        int[] intValues = new int[inputSize * inputSize];
        try {
            bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        } catch (Exception e) {
            Log.e("EmbeddingExtractor", "getPixels failed: " + e.getMessage());
            return null;
        }

        imgData.rewind();

        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                if (isModelQuantized) {
                    imgData.put((byte) ((pixelValue >> 16) & 0xFF));
                    imgData.put((byte) ((pixelValue >> 8) & 0xFF));
                    imgData.put((byte) (pixelValue & 0xFF));
                } else {
                    imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                }
            }
        }

        Object[] inputArray = {imgData};
        float[][] embeddings = new float[1][OUTPUT_SIZE];
        java.util.Map<Integer, Object> outputMap = new java.util.HashMap<>();
        outputMap.put(0, embeddings);

        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);

        return embeddings[0];
    }

    public void extractAsync(final Bitmap bitmap, Executor executor, final java.util.function.Consumer<float[]> onComplete, final java.util.function.Consumer<Exception> onError) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    float[] emb = extract(bitmap);
                    onComplete.accept(emb);
                } catch (Exception e) {
                    onError.accept(e);
                }
            }
        });
    }

    public void close() {
        if (tfLite != null) {
            tfLite.close();
            tfLite = null;
        }
    }
}

