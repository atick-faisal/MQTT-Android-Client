package ai.atick.mqttandroidclient;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    EditText brokerAddress, txTopic, txMessage, rxTopic;
    Button connectButton, disconnectButton, sendButton, subscribeButton;
    TextView rxMessage;
    LinearLayout commSection;
    ImageView imageIcon;

    MqttAndroidClient client;
    TinyDB tinyDB;
    boolean connectionFlag = false;
    String __broker_address, __tx_topic, __tx_message, __rx_topic, __rx_message;
    int msgCount = 0;
    boolean terminalOverflow = false;

    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            if (msg.what == 0) {
                rxMessage.append(__rx_message + "\n");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        requestPermissions();

        brokerAddress = findViewById(R.id.broker_address);
        txTopic = findViewById(R.id.tx_topic);
        txMessage = findViewById(R.id.tx_message);
        rxTopic = findViewById(R.id.rx_topic);
        connectButton = findViewById(R.id.connect_button);
        disconnectButton = findViewById(R.id.disconnect_button);
        sendButton = findViewById(R.id.send_button);
        subscribeButton = findViewById(R.id.subscribe_button);
        rxMessage = findViewById(R.id.rx_message);
        commSection = findViewById(R.id.comm_section);
        imageIcon = findViewById(R.id.image_icon);

        rxMessage.setMovementMethod(new ScrollingMovementMethod());

        tinyDB = new TinyDB(getApplicationContext());
        __broker_address = tinyDB.getString("BROKER_ADDRESS");
        __tx_topic = tinyDB.getString("TX_TOPIC");
        __rx_topic = tinyDB.getString("RX_TOPIC");
        __tx_message = tinyDB.getString("TX_MSG");
        if (!__broker_address.isEmpty()) {
            brokerAddress.setText(__broker_address);
        }
        if (!__tx_topic.isEmpty()) {
            txTopic.setText(__tx_topic);
        }
        if (!__rx_topic.isEmpty()) {
            rxTopic.setText(__rx_topic);
        }
        if (!__tx_message.isEmpty()) {
            txMessage.setText(__tx_message);
        }

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                __broker_address = brokerAddress.getText().toString();
                if (!__broker_address.isEmpty()) {
                    tinyDB.putString("BROKER_ADDRESS", __broker_address);
                    String brokerURL = "tcp://" + __broker_address + ":1883";
                    connectToBroker(brokerURL);
                } else {
                    Toast.makeText(getApplicationContext(), "Address is Empty", Toast.LENGTH_SHORT).show();
                }
            }
        });

        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                disconnect();
            }
        });

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                __tx_topic = txTopic.getText().toString();
                __tx_message = txMessage.getText().toString();
                if (!__tx_topic.isEmpty()) {
                    tinyDB.putString("TX_MSG", __tx_message);
                    tinyDB.putString("TX_TOPIC", __tx_topic);
                    sendMessage(__tx_topic, __tx_message);
                } else {
                    Toast.makeText(getApplicationContext(), "Topic is Empty", Toast.LENGTH_SHORT).show();
                }
            }
        });

        subscribeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rxMessage.setVisibility(View.VISIBLE);
                __rx_topic = rxTopic.getText().toString();
                if (!__rx_topic.isEmpty()) {
                    tinyDB.putString("RX_TOPIC", __rx_topic);
                    subscribeToTopic(__rx_topic);
                }

            }
        });
    }

    public void connectToBroker(String URL) {
        String clientId = MqttClient.generateClientId();
        client = new MqttAndroidClient(this.getApplicationContext(), URL, clientId);

        try {
            IMqttToken token = client.connect();
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    connectionFlag = true;
                    Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_SHORT).show();
                    connectButton.setEnabled(false);
                    commSection.setVisibility(View.VISIBLE);
                    disconnectButton.setEnabled(true);
                    subscribeButton.setEnabled(true);
                    imageIcon.setVisibility(View.GONE);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Toast.makeText(getApplicationContext(), "Failed to Connect", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (MqttException e) {
            Toast.makeText(getApplicationContext(), "Failed to Connect: " + e, Toast.LENGTH_SHORT).show();
        }
    }

    public void sendMessage(String topic, String payload) {
        byte[] encodedPayload;
        try {
            encodedPayload = payload.getBytes(StandardCharsets.UTF_8);
            MqttMessage message = new MqttMessage(encodedPayload);
            client.publish(topic, message);
            Toast.makeText(getApplicationContext(), "Sent", Toast.LENGTH_SHORT).show();
        } catch (MqttException e) {
            Toast.makeText(getApplicationContext(), "Failed to Send Message: " + e, Toast.LENGTH_SHORT).show();
        }
    }

    public void subscribeToTopic(String topic) {
        try {
            if (client.isConnected()) {
                client.subscribe(topic, 0);
                rxTopic.clearFocus();
                rxMessage.setText("");
                Toast.makeText(getApplicationContext(), "Subscribed", Toast.LENGTH_SHORT).show();
                subscribeButton.setEnabled(false);
                client.setCallback(new MqttCallback() {
                    @SuppressLint("SetTextI18n")
                    @Override
                    public void connectionLost(Throwable cause) {
                        Toast.makeText(getApplicationContext(), "Connection Lost", Toast.LENGTH_SHORT).show();
                        connectionFlag = false;
                    }

                    @Override
                    public void messageArrived(String topic, MqttMessage message) {
                        msgCount++;
                        if (msgCount > 9 && !terminalOverflow) {
                            terminalOverflow = true;
                            rxMessage.setGravity(Gravity.BOTTOM);
                        }
                        __rx_message = message.toString();
                        handler.sendEmptyMessage(0);
                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {
                    }
                });
            }
        } catch (Exception ignored) {
        }
    }

    public void disconnect() {
        if (connectionFlag) {
            try {
                IMqttToken disconnectToken = client.disconnect();
                disconnectToken.setActionCallback(new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        Toast.makeText(getApplicationContext(), "Disconnected", Toast.LENGTH_SHORT).show();
                        connectionFlag = false;
                        msgCount = 0;
                        connectButton.setEnabled(true);
                        disconnectButton.setEnabled(false);
                        commSection.setVisibility(View.GONE);
                        rxMessage.setText("");
                        rxMessage.scrollTo(0, 0);
                        rxMessage.setVisibility(View.GONE);
                        rxMessage.setGravity(Gravity.NO_GRAVITY);
                        imageIcon.setVisibility(View.VISIBLE);
                        terminalOverflow = false;
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    }
                });
            } catch (MqttException e) {
                Toast.makeText(getApplicationContext(), "Failed to Disconnect: " + e, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnect();
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WAKE_LOCK) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.WAKE_LOCK}, 0);
            } else if (checkSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.ACCESS_NETWORK_STATE}, 0);
            } else if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.READ_PHONE_STATE}, 0);
            } else if (checkSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.INTERNET}, 0);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 0) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestPermissions();
            } else {
                Toast.makeText(getApplicationContext(), "Please allow permissions...", Toast.LENGTH_LONG).show();
                requestPermissions();
            }
        }
    }
}
