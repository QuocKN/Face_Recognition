package com.atharvakale.facerecognition.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class OvalOverlayView extends View {

    private Paint backgroundPaint;
    private Paint clearPaint;
    private RectF ovalRect;
    private Bitmap bitmap;
    private Canvas tempCanvas;

    public OvalOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // Paint for the semi-transparent background
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(Color.parseColor("#99000000")); // 60% black
        backgroundPaint.setStyle(Paint.Style.FILL);

        // Paint to clear the oval area
        clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        clearPaint.setColor(Color.TRANSPARENT);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        clearPaint.setStyle(Paint.Style.FILL);

        ovalRect = new RectF();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            // Create a bitmap for off-screen drawing
            if (bitmap != null) {
                bitmap.recycle();
            }
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            tempCanvas = new Canvas(bitmap);

            // Define the oval's boundaries as a vertical ellipse
            float horizontalPadding = w * 0.1f; // 10% padding on each side
            float verticalPadding = h * 0.15f; // 15% padding top and bottom
            ovalRect.set(horizontalPadding, verticalPadding, w - horizontalPadding, h - verticalPadding);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap != null) {
            // Clear the temporary canvas
            tempCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

            // Draw the semi-transparent background
            tempCanvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);

            // Punch a hole in the background with the oval shape
            tempCanvas.drawOval(ovalRect, clearPaint);

            // Draw the result to the main canvas
            canvas.drawBitmap(bitmap, 0, 0, null);
        }
    }
}
