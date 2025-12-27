package com.atharvakale.facerecognition.ml;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.*;
import android.media.Image;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.nio.*;
import java.nio.channels.FileChannel;

public class FaceClassifier {

    private static final int INPUT = 112;
    private static final int OUTPUT = 192;

    Interpreter tflite;

    public FaceClassifier(Context ctx) {
        try {
            tflite = new Interpreter(loadModel(ctx));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private MappedByteBuffer loadModel(Context ctx) throws Exception {
        AssetFileDescriptor fd = ctx.getAssets().openFd("mobile_face_net.tflite");
        FileChannel fc = new FileInputStream(fd.getFileDescriptor()).getChannel();
        return fc.map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
    }

    public float[] getEmbedding(Bitmap bmp) {
        ByteBuffer buf =
                ByteBuffer.allocateDirect(1 * INPUT * INPUT * 3 * 4)
                        .order(ByteOrder.nativeOrder());

        int[] pixels = new int[INPUT * INPUT];
        bmp.getPixels(pixels, 0, INPUT, 0, 0, INPUT, INPUT);

        for (int p : pixels) {
            buf.putFloat((((p >> 16) & 0xff) - 128) / 128f);
            buf.putFloat((((p >> 8) & 0xff) - 128) / 128f);
            buf.putFloat(((p & 0xff) - 128) / 128f);
        }

        float[][] out = new float[1][OUTPUT];
        tflite.run(buf, out);
        return out[0];
    }

    public Bitmap cropAndResize(Bitmap src, RectF box) {
        Bitmap crop = Bitmap.createBitmap(
                src,
                (int) box.left,
                (int) box.top,
                (int) box.width(),
                (int) box.height()
        );
        return Bitmap.createScaledBitmap(crop, INPUT, INPUT, false);
    }

    public Bitmap toBitmap(Image img) {
        ByteBuffer y = img.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[y.remaining()];
        y.get(bytes);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    // --- Added helpers ---
    // Returns the model input size (width==height)
    public int getInputSize() {
        return INPUT;
    }

    // Resize the provided face bitmap to the model input size. If the source has transparent
    // corners (e.g. an elliptical crop), this method renders the source onto a solid white
    // background before resizing so the model receives consistent RGB input.
    public Bitmap resizeToModelInput(Bitmap src) {
        if (src == null) return null;

        // Create a square bitmap at model input size with white background
        Bitmap out = Bitmap.createBitmap(INPUT, INPUT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.WHITE);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        // Draw the source bitmap scaled to fill the model input square
        Rect srcRect = new Rect(0, 0, src.getWidth(), src.getHeight());
        Rect dstRect = new Rect(0, 0, INPUT, INPUT);
        canvas.drawBitmap(src, srcRect, dstRect, paint);

        return out;
    }

}
