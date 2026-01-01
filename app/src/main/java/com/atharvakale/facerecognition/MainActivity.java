package com.atharvakale.facerecognition;

import android.content.Intent;
import android.net.Uri;
import java.io.InputStream;
import android.os.Bundle;
import android.util.Log;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import android.widget.Button;

import com.atharvakale.facerecognition.mqtt.MqttManager;
import com.atharvakale.facerecognition.ui.MainController;

public class MainActivity extends AppCompatActivity implements MqttManager.MqttListener {
    private MainController controller;

    // Keep small constants needed for result/permission handling
    private static final int SELECT_PICTURE = 1;

    @OptIn(markerClass = ExperimentalGetImage.class)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Minimal wiring: create MQTT manager and controller
        MqttManager mqttManager = new MqttManager(this, "ssl://71e2c6502603479280eb36c1b5b12bfc.s1.eu.hivemq.cloud:8883", "server/device01/face_data", "mqttnkq", "Soict2025", this);
        controller = new MainController(this, "mobile_face_net.tflite", 112, 192, mqttManager);
        controller.start();

        // Wire UI buttons
        Button recognizeBtn = findViewById(R.id.button3);
        Button switchCamBtn = findViewById(R.id.button5);
        Button actionBtn = findViewById(R.id.button2);

        // Recognition button - Start/Stop recognition
        if (recognizeBtn != null) {
            recognizeBtn.setText("Start Recognition");
            recognizeBtn.bringToFront();
            recognizeBtn.requestLayout();
            recognizeBtn.invalidate();
            recognizeBtn.setClickable(true);
            recognizeBtn.setFocusable(true);
            recognizeBtn.setOnClickListener(v -> {
                if (controller != null) {
                    controller.toggleRecognition();
                }
            });
        }

        if (switchCamBtn != null) {
            // ensure button is above preview view
            switchCamBtn.bringToFront();
            switchCamBtn.requestLayout();
            switchCamBtn.invalidate();
            switchCamBtn.setClickable(true);
            switchCamBtn.setFocusable(true);
            switchCamBtn.setOnClickListener(v -> {
                if (controller != null) controller.toggleCamera();
            });
        }

        // Action button - Show stored faces
        if (actionBtn != null) {
            actionBtn.bringToFront();
            actionBtn.requestLayout();
            actionBtn.invalidate();
            actionBtn.setClickable(true);
            actionBtn.setFocusable(true);
            actionBtn.setOnClickListener(v -> {
                if (controller != null) {
                    controller.showStoredFaces();
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Delegate permission handling to controller (for FaceRecognition storage permission)
        if (controller != null) {
            controller.onRequestPermissionsResult(requestCode, grantResults);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == SELECT_PICTURE && data != null) {
            Uri selectedImageUri = data.getData();
            if (selectedImageUri != null) {
                try (InputStream is = getContentResolver().openInputStream(selectedImageUri)) {
                    Bitmap b = null;
                    if (is != null) {
                        b = android.graphics.BitmapFactory.decodeStream(is);
                    }
//                    if (b != null && controller != null) controller.processBitmapAndShowDialog(b, false);
                } catch (Exception e) {
                    Log.e("MainActivity", "Failed to read selected image", e);
                }
            } else {
                Log.w("MainActivity", "selectedImageUri is null");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (controller != null) controller.stop();
    }

    // MqttManager.MqttListener implementation
    @Override
    public void onFaceReceived(String fullName, float[] embedding) {
        Log.d("MainActivity", "Face received: " + fullName);
        // Save face data to storage for recognition
        if (controller != null) {
            controller.addFaceToStorage(fullName, embedding);
        }
    }

    @Override
    public void onMqttConnected(String serverURI) {
        Log.d("MainActivity", "MQTT connected to: " + serverURI);
        runOnUiThread(() -> {
            android.widget.Toast.makeText(this, "MQTT Connected", android.widget.Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onDataReceived(String message) {
        Log.d("MainActivity", "MQTT data received: " + message);
    }

}
