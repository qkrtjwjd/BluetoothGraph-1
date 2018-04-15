package nicktxd.bluetoothgraph;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;


public class BluetoothActivity extends Activity {

    public static final UUID MY_UUID = UUID.fromString(
            "00001101-0000-1000-8000-00805F9B34FB");
    protected static final int SUCCESS_CONNECT = 0;
    protected static final int MESSAGE_READ = 1;
    protected static final int DISCONNECTED = 2;
    protected static final int CONNECTION_LOST = 3;
    static Handler mHandler = new Handler();
    static ConnectedThread connectedThread;
    static DebugThread debugThread;
    static BluetoothAdapter btuetoothAdapter;
    private static boolean cancelDebug;
    private static BluetoothDevice selectedDevice;
    ListView listViewDevices;
    SwipeRefreshLayout mSwipeRefreshLayout;
    ArrayAdapter<String> listAdapter;
    ArrayList<BluetoothDevice> devices;
    IntentFilter filter;
    BroadcastReceiver receiver;
    AdapterView.OnItemClickListener
            onItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(
                AdapterView<?> parent, View view, int position, long id) {
            if (btuetoothAdapter.isDiscovering()) {
                btuetoothAdapter.cancelDiscovery();
            }
            if (listAdapter.getItem(position).contains("(Paired)")) {
                Toast.makeText(getApplicationContext(),
                        "Connecting to " + listAdapter.getItem(position),
                        Toast.LENGTH_SHORT).show();
                BluetoothDevice selectedDevice = devices.get(position);
                ConnectThread connect = new ConnectThread(selectedDevice);
                connect.start();

                BluetoothActivity.selectedDevice = selectedDevice;
            } else {
                Toast.makeText(getApplicationContext(),
                        "Device is not paired", Toast.LENGTH_SHORT).show();
            }
        }
    };
    private SaveObject saveState;
    private SaveObject retState;

    public static void disconnect() {
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
    }

    public static void stopDebug() {
        if (debugThread != null)
            cancelDebug = true;
        debugThread = null;
    }

    public static void gethandler(Handler handler) {
        mHandler = handler;
    }

    public static BluetoothDevice getSelectedDevice() {
        return selectedDevice;
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        saveState.listAdapter = listAdapter;
        saveState.devices = devices;
        return saveState;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        saveState = new SaveObject();
        retState = (SaveObject) getLastNonConfigurationInstance();

        init();

        if (btuetoothAdapter == null) {
            Toast.makeText(getApplicationContext(),
                    "No bluetooth detected", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            if (!btuetoothAdapter.isEnabled()) {
                turnOnBT();
            }
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            startDiscovery();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();

        }
        return super.onOptionsItemSelected(item);
    }

    private void startDiscovery() {
        btuetoothAdapter.cancelDiscovery();
        btuetoothAdapter.startDiscovery();
    }

    private void turnOnBT() {
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(intent, 1);
    }

    private void init() {
        ActionBar actionBar = getActionBar();
        if (actionBar != null)
            actionBar.setDisplayHomeAsUpEnabled(true);

        mSwipeRefreshLayout =
                (SwipeRefreshLayout) findViewById(R.id.swiperefresh);
        final SwipeRefreshLayout.OnRefreshListener
                swipeListener = new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (btuetoothAdapter == null)
                    return;
                listAdapter.clear();
                devices.clear();
                startDiscovery();
                mSwipeRefreshLayout.setRefreshing(false);

            }
        };
        mSwipeRefreshLayout.setOnRefreshListener(swipeListener);

        listViewDevices = (ListView) findViewById(R.id.listOfPairedDevices);
        listViewDevices.setOnItemClickListener(onItemClickListener);

        if (retState == null) {
            listAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_list_item_1, 0);
            devices = new ArrayList<>();
        } else {
            listAdapter = retState.listAdapter;
            devices = retState.devices;
        }

        listViewDevices.setAdapter(listAdapter);

        btuetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE);
                    if (isUnique(device)) {
                        devices.add(device);
                        String stringPaired;
                        if (isPaired(device))
                            stringPaired = "(Paired)";
                        else
                            stringPaired = "(Not paired)";
                        String deviceName = device.getName();
                        if (deviceName == null)
                            deviceName = "No name";
                        listAdapter.add(deviceName + "\n"
                                + stringPaired + "\n" + device.getAddress());
                    }
                } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)
                        && (btuetoothAdapter.getState()
                        == BluetoothAdapter.STATE_OFF))
                    turnOnBT();
            }
        };
        filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);
        filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(receiver, filter);
        filter = new IntentFilter(
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        registerReceiver(receiver, filter);
    }

    boolean isUnique(BluetoothDevice device) {
        for (int i = 0; i < devices.size(); i++)
            if (devices.get(i).equals(device))
                return false;
        return true;
    }

    boolean isPaired(BluetoothDevice device) {
        return device.getBondState() == 12;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (btuetoothAdapter != null)
            btuetoothAdapter.cancelDiscovery();

        this.unregisterReceiver(receiver);
    }

    protected void onActivityResult(int requestCode,
                                    int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_CANCELED) {
            Toast.makeText(getApplicationContext(),
                    "Bluetooth must be enabled to continue",
                    Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    static class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException ignored) {
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[512];
            int bytes;
            StringBuilder readMessage = new StringBuilder();
            while (true) {
                try {
                    bytes = mmInStream.read(buffer);

                    String readed = new String(buffer, 0, bytes);
                    readMessage.append(readed);

                    if (readed.contains("\n")) {
                        mHandler.obtainMessage(MESSAGE_READ, bytes,
                                -1, readMessage.toString()).sendToTarget();
                        readMessage.setLength(0);
                    }

                }
                catch (IOException e) {
                    mHandler.obtainMessage(CONNECTION_LOST).sendToTarget();
                    break;
                }
            }
        }

        public void write(String income) {

            try {
                mmOutStream.write(income.getBytes());
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } catch (IOException ignored) {
            }
        }

        void cancel() {
            try {
                mHandler.obtainMessage(DISCONNECTED, mmSocket).sendToTarget();
                mmSocket.close();
            } catch (IOException ignored) {
                ignored.printStackTrace();
            }
        }
    }

    static class DebugThread extends Thread {
        public void run() {
            int temp;
            cancelDebug = false;
            Random random = new Random();
            while (!cancelDebug) {
                try {
                    try {
                        sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    temp = 2220 + random.nextInt(2250 - 2220);
                    //temp = (int) (Math.random() * );
                    mHandler.obtainMessage(MESSAGE_READ,
                            "s" + temp + "\n").sendToTarget();

                } catch (Exception e) {
                    break;
                }
            }
        }
    }

    private class SaveObject {
        private ArrayAdapter<String> listAdapter;
        private ArrayList<BluetoothDevice> devices;
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        ConnectThread(BluetoothDevice device) {
            BluetoothSocket tmp = null;
            mmDevice = device;
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException ignored) {
            }
            mmSocket = tmp;
        }

        public void run() {
            btuetoothAdapter.cancelDiscovery();

            try {
                if (!mmSocket.isConnected())
                    mmSocket.connect();
                connectedThread = new ConnectedThread(mmSocket);
            } catch (IOException connectException) {
                try {
                    mmSocket.close();
                } catch (IOException ignored) {
                }
                return;
            }

            mHandler.obtainMessage(SUCCESS_CONNECT, mmSocket).sendToTarget();
            finish();
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException ignored) {
        }
        }
    }

}

