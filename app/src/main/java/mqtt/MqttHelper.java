//package mqtt;
//
//import android.content.Context;
//import android.util.Log;
//
//import org.eclipse.paho.android.service.MqttAndroidClient;
//import org.eclipse.paho.client.mqttv3.IMqttActionListener;
//import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
//import org.eclipse.paho.client.mqttv3.IMqttToken;
//import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
//import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
//import org.eclipse.paho.client.mqttv3.MqttMessage;
//
//public class MqttHelper {
//    public MqttAndroidClient mqttAndroidClient;
//    final String serverUri = "tcp://broker.hivemq.com:1883"; // đổi sang broker của bạn
//    final String clientId = "AndroidFaceApp";
//    final String subscriptionTopic = "iot/face/image";
//
//    public MqttHelper(Context context) {
//        mqttAndroidClient = new MqttAndroidClient(context, serverUri, clientId);
//        mqttAndroidClient.setCallback(new MqttCallbackExtended() {
//            @Override
//            public void connectComplete(boolean reconnect, String serverURI) {
//                Log.d("MQTT", "Connected to " + serverURI);
//            }
//
//            @Override
//            public void connectionLost(Throwable cause) {
//                Log.e("MQTT", "Connection lost", cause);
//            }
//
//            @Override
//            public void messageArrived(String topic, MqttMessage message) throws Exception {
//                Log.d("MQTT", "Message received: " + topic);
//                ImageRepository.saveImage(context, message.getPayload());
//            }
//
//            @Override
//            public void deliveryComplete(IMqttDeliveryToken token) {}
//        });
//
//        connect();
//    }
//
//    private void connect() {
//        try {
//            MqttConnectOptions options = new MqttConnectOptions();
//            options.setAutomaticReconnect(true);
//            options.setCleanSession(true);
//            IMqttToken token = mqttAndroidClient.connect(options);
//            token.setActionCallback(new IMqttActionListener() {
//                @Override
//                public void onSuccess(IMqttToken asyncActionToken) {
//                    subscribeToTopic();
//                }
//
//                @Override
//                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
//                    Log.e("MQTT", "Failed to connect", exception);
//                }
//            });
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    private void subscribeToTopic() {
//        try {
//            mqttAndroidClient.subscribe(subscriptionTopic, 0, null, new IMqttActionListener() {
//                @Override
//                public void onSuccess(IMqttToken asyncActionToken) {
//                    Log.d("MQTT", "Subscribed to topic!");
//                }
//
//                @Override
//                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
//                    Log.e("MQTT", "Failed to subscribe", exception);
//                }
//            });
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//
//}
