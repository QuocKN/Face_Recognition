package com.atharvakale.facerecognition.ui;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

public class OvalPreviewViewOutlineProvider extends ViewOutlineProvider {
    @Override
    public void getOutline(View view, Outline outline) {
        int width = view.getWidth();
        int height = view.getHeight();
        outline.setOval(0, 0, width, height); // Tạo khung trái xoan
    }
}