package com.atharvakale.facerecognition.mqtt;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import org.json.JSONArray;
import org.json.JSONObject;

import info.mqtt.android.service.MqttAndroidClient;

public class MqttManager {
    private final String TAG = "MqttManager";
    private final MqttAndroidClient mqttClient;
    private final MqttListener listener;
    private final String topic;

    public interface MqttListener {
        void onFaceReceived(String fullName,  float[] embedding );
        void onMqttConnected(String serverURI);
        void onDataReceived(String message);
    }

    public MqttManager(Context context, String brokerUrl, String topic,
                       String username, String password, MqttListener listener) {
        this.listener = listener;
        this.topic = topic;

        String clientId = MqttClient.generateClientId();
        mqttClient = new MqttAndroidClient(context, brokerUrl, clientId);

        mqttClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                Log.d(TAG, "✅ MQTT Connected");
                if (listener != null) {
                    listener.onMqttConnected(serverURI);
                }
                subscribe(topic);
            }

            @Override
            public void connectionLost(Throwable cause) {
                Log.e(TAG, "⚠️ MQTT connection lost", cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                handleMessage(new String(message.getPayload()));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {}
        });

        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setUserName(username);
            options.setPassword(password.toCharArray());
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(30);

            mqttClient.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "✅ MQTT connect action successful");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "❌ MQTT connection failed", exception);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "❌ Error connecting MQTT", e);
        }
    }

    public void connect() {
        // Connection is now handled in the constructor
    }

    private void subscribe(String topic) {
        try {
            mqttClient.subscribe(topic, 1, (t, message) -> handleMessage(new String(message.getPayload())));
            Log.d(TAG, "📡 Subscribed to " + topic);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error subscribing MQTT", e);
        }
    }

    private void handleMessage(String json) {
        try {
            // Try to parse as JSON object first (single face data)
            if (json.trim().startsWith("{")) {
                JSONObject obj = new JSONObject(json);
                processFaceData(obj);
            }
            // Try to parse as JSON array (multiple faces)
            else if (json.trim().startsWith("[")) {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    processFaceData(obj);
                }
            } else {
                Log.e(TAG, "❌ Invalid JSON format (not object or array): " + json);
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error parsing MQTT message: " + json, e);
        }
    }

    private void processFaceData(JSONObject obj) {
        try {
            String fullName = obj.getString("fullName");
            String employeeCode = obj.getString("employeeCode");
            String name_code = fullName + " - " + employeeCode;

            float[] embedding = null;
            if (obj.has("embedding")) {
                embedding = jsonArrayToFloatArray(obj.getJSONArray("embedding"));
            }

            if (listener != null) {
                listener.onFaceReceived(name_code, embedding);
                String notificationMessage = "📥 Nhận data từ MQTT: " + fullName + " | code: " + employeeCode;
                listener.onDataReceived(notificationMessage);
            }

            Log.d(TAG, "📥 Nhận data từ MQTT: " + fullName + " | code: " + employeeCode);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error processing face data", e);
        }
    }

    private float[] jsonArrayToFloatArray(JSONArray jsonArray) throws Exception {
        float[] arr = new float[jsonArray.length()];
        for (int i = 0; i < jsonArray.length(); i++) {
            arr[i] = (float) jsonArray.getDouble(i);
        }
        return arr;
    }
}
