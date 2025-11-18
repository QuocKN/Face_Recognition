package mqtt;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

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
    private final Context context;
    private final MqttListener listener;
    private final String topic;
    private final String username;
    private final String password;

    public interface MqttListener {
        void onFaceReceived(String fullName,  float[] embedding );
    }

    public MqttManager(Context context, String brokerUrl, String topic,
                       String username, String password, MqttListener listener) {
        this.context = context;
        this.listener = listener;
        this.topic = topic;
        this.username = username;
        this.password = password;

        String clientId = MqttClient.generateClientId();
        mqttClient = new MqttAndroidClient(context, brokerUrl, clientId);

        mqttClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                Log.d(TAG, "✅ MQTT Connected to " + serverURI);
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
    }

    // 🔹 kết nối MQTT qua SSL (port 8883)
    public void connect() {
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
                    Log.d(TAG, "✅ MQTT connected successfully");
                    subscribe(topic);
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

    private void subscribe(String topic) {
        try {
            mqttClient.subscribe(topic, 1, new IMqttMessageListener() {
                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    handleMessage(message.toString());
                }
            });
            Log.d(TAG, "📡 Subscribed to " + topic);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error subscribing MQTT", e);
        }
    }

    private void handleMessage(String json) {
        try {
            JSONArray arr = new JSONArray(json); // vì dữ liệu là mảng
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String fullName = obj.getString("fullName");
                String employeeCode = obj.getString("employeeCode");
                String name_code = fullName + " - " + employeeCode;


                float[] embedding = null;
                if (obj.has("embedding")) {
                    embedding = jsonArrayToFloatArray(obj.getJSONArray("embedding"));
                }

                if (listener != null) {
                    listener.onFaceReceived(name_code, embedding);
                }

                Log.d(TAG, "📥 Nhận data từ MQTT: " + fullName + " | code: " + employeeCode);
            }


        } catch (Exception e) {
            Log.e(TAG, "❌ Invalid MQTT message: " + json, e);
        }
    }


    private float[] jsonArrayToFloatArray(org.json.JSONArray jsonArray) throws Exception {
        float[] arr = new float[jsonArray.length()];
        for (int i = 0; i < jsonArray.length(); i++) {
            arr[i] = (float) jsonArray.getDouble(i);
        }
        return arr;
    }

}
