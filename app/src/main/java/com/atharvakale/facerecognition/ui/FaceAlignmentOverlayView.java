package com.atharvakale.facerecognition.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class FaceAlignmentOverlayView extends View {

    private final Paint backgroundPaint;
    private final Paint borderPaint;
    private final Paint clearPaint;
    private final RectF ovalRect;

    private boolean isFaceAligned = false;

    public FaceAlignmentOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        // Paint for the semi-transparent background
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(Color.parseColor("#99000000")); // 60% black
        backgroundPaint.setStyle(Paint.Style.FILL);

        // Paint for the border
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(10f);

        // Paint to clear the oval area
        clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        clearPaint.setColor(Color.TRANSPARENT);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        clearPaint.setStyle(Paint.Style.FILL);

        ovalRect = new RectF();
    }

    public void setFaceAligned(boolean aligned) {
        if (isFaceAligned != aligned) {
            isFaceAligned = aligned;
            borderPaint.setColor(aligned ? Color.GREEN : Color.WHITE);
            invalidate(); // Redraw the view with the new color
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Define the oval's boundaries as a vertical ellipse
        int horizontalPadding = (int) (w * 0.18f);
        ovalRect.set(horizontalPadding, 0, w - horizontalPadding, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Draw the background
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
        // Clear the oval area from the background
        canvas.drawOval(ovalRect, clearPaint);
        // Draw the border
        canvas.drawOval(ovalRect, borderPaint);
    }
}
