package com.atharvakale.facerecognition.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;

import com.atharvakale.facerecognition.R;
import com.atharvakale.facerecognition.camera.CameraHelper;
import com.atharvakale.facerecognition.data.FaceStorage;
import com.atharvakale.facerecognition.mqtt.MqttManager;
import com.google.android.material.snackbar.Snackbar;

public class MainActivity extends AppCompatActivity implements MqttManager.MqttListener {

    private static final int CAMERA_REQ = 100;

    // MQTT Configuration
    private static final String MQTT_BROKER_URL = "ssl://71e2c6502603479280eb36c1b5b12bfc.s1.eu.hivemq.cloud:8883";
    private static final String MQTT_TOPIC = "server/device01/face_data";
    private static final String MQTT_USERNAME = "mqttnkq";
    private static final String MQTT_PASSWORD = "Soict2025";

    private PreviewView previewView;
    private ImageView facePreview;
    private TextView recoName;
    private Button recognizeBtn;
    private View rootView;

    private FaceStorage faceStorage;
    private CameraHelper cameraHelper;
    private MqttManager mqttManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rootView = findViewById(R.id.coordinatorLayout);
        previewView = findViewById(R.id.previewView);
        facePreview = findViewById(R.id.imageView);
        recoName = findViewById(R.id.textView);
        recognizeBtn = findViewById(R.id.button3);
        Button cameraSwitchBtn = findViewById(R.id.button5);
        Button actionsBtn = findViewById(R.id.button2);

        faceStorage = new FaceStorage(this);

        cameraHelper = new CameraHelper(
                this,
                previewView,
                faceStorage,
                (bitmap, embedding, name) -> {
                    facePreview.setImageBitmap(bitmap);
                    recoName.setText(name);
                }
        );

        mqttManager = new MqttManager(this, MQTT_BROKER_URL, MQTT_TOPIC, MQTT_USERNAME, MQTT_PASSWORD, this);
        mqttManager.connect();

        recognizeBtn.setOnClickListener(v -> {
            cameraHelper.recognize = !cameraHelper.recognize;
            if (cameraHelper.recognize) {
                recognizeBtn.setText("Stop Recognize");
            } else {
                recognizeBtn.setText("Start Recognize");
            }
        });

        cameraSwitchBtn.setOnClickListener(v -> cameraHelper.toggleCamera());

        actionsBtn.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setItems(new String[]{"Clear All"}, (d, i) -> faceStorage.clear())
                        .show()
        );

        if (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQ);
        } else {
            cameraHelper.start();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == CAMERA_REQ && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            cameraHelper.start();
        }
    }

    @Override
    public void onFaceReceived(String fullName, float[] embedding) {
        if (embedding != null) {
            faceStorage.addFace(fullName, embedding);
        }
    }

    @Override
    public void onMqttConnected(String serverURI) {
        runOnUiThread(() -> {
            Snackbar.make(rootView, "Connected to: " + serverURI, Snackbar.LENGTH_LONG).show();
        });
    }

    @Override
    public void onDataReceived(String message) {
        runOnUiThread(() -> {
            Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show();
        });
    }
}
