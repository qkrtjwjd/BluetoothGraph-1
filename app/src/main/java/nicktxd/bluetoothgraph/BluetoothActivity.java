package nicktxd.bluetoothgraph;

import android.Manifest;
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
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;


public class BluetoothActivity extends Activity implements AdapterView.OnItemClickListener {

    public static void disconnect(){
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
    }

    public static void gethandler(Handler handler){//Bluetooth handler
        mHandler = handler;
    }
    static Handler mHandler = new Handler();

    static ConnectedThread connectedThread;
    static DEBUGThread debugThread;
    public static final  UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    protected static final int SUCCESS_CONNECT = 0;
    protected static final int MESSAGE_READ = 1;

    private static BluetoothDevice selDevice;

    ListView listOfPairedDevices;
    static BluetoothAdapter btAdapter;
    Set<BluetoothDevice> devicesArray;
    ArrayAdapter<String> listAdapter;
    ArrayList<String> pairedDevices;
    ArrayList<BluetoothDevice> devices;
    IntentFilter filter;
    BroadcastReceiver receiver;
    SwipeRefreshLayout mSwipeRefreshLayout;



    private class objToSave{
        private ArrayAdapter<String> listAdapter;
        private ArrayList<BluetoothDevice> devices;
    }

    private objToSave saveState;
    private objToSave retState;

    @Override
    public Object onRetainNonConfigurationInstance() {
        //Сохранить данные
        saveState.listAdapter = listAdapter;
        saveState.devices = devices;
        return saveState;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);


        saveState = new objToSave();
        retState = (objToSave) getLastNonConfigurationInstance();

        init();
        if (btAdapter == null) {
            Toast.makeText(getApplicationContext(), "No bluetooth detected", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            if (!btAdapter.isEnabled()) {
                turnOnBT();
            }
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            // int permissionCheck = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION);

            getPairedDevices();
            startDiscovery();

        }
    }

    private void startDiscovery() {
        btAdapter.cancelDiscovery();
        btAdapter.startDiscovery();
    }

    private void turnOnBT() {
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(intent, 1);
    }

    private void getPairedDevices() {
        devicesArray = btAdapter.getBondedDevices();
        if (devicesArray.size()>0){
            for(BluetoothDevice device:devicesArray){
                pairedDevices.add(device.getName());
            }
        }
    }

    private void init(){
        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);
        final SwipeRefreshLayout.OnRefreshListener swipeListener = new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (btAdapter != null)
                    btAdapter.cancelDiscovery();
                listAdapter.clear();
                startDiscovery();
                mSwipeRefreshLayout.setRefreshing(false);
            }
        };
        mSwipeRefreshLayout.setOnRefreshListener(swipeListener);
        listOfPairedDevices = (ListView)findViewById(R.id.listOfPairedDevices);
        listOfPairedDevices.setOnItemClickListener(this);
        if (retState == null) {
            listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, 0);
            devices = new ArrayList<>();
        }
        else {
            listAdapter = retState.listAdapter;
            devices = retState.devices;
        }
        listOfPairedDevices.setAdapter(listAdapter);
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        pairedDevices = new ArrayList<>();
        receiver = new BroadcastReceiver(){
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)){
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    boolean unique = true;
                    for(int i=0; i<devices.size(); i++)
                        if(devices.get(i).equals(device))
                            unique = false;
                    if (unique) {
                        devices.add(device);
                        String s = "";
                        for (int a = 0; a < pairedDevices.size(); a++) {
                            if (device.getName().equals(pairedDevices.get(a))) {
                                s = "(Paired)";
                                break;
                            }
                        }
                        listAdapter.add(device.getName() + " " + s + " " + "\n" + device.getAddress());
                    }
                }else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)){
                    if (btAdapter.getState() == BluetoothAdapter.STATE_OFF){
                        turnOnBT();
                    }
                }
            }

        };
        filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);
        filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(receiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(btAdapter != null)
            btAdapter.cancelDiscovery();

        this.unregisterReceiver(receiver);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_CANCELED){
            Toast.makeText(getApplicationContext(), "Bluetooth must be enabled to continue", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        if (btAdapter.isDiscovering()){
            btAdapter.cancelDiscovery();
        }
        if (listAdapter.getItem(arg2).contains("(Paired)")){
            Toast.makeText(getApplicationContext(), "Connecting to "+listAdapter.getItem(arg2), Toast.LENGTH_SHORT).show();
            BluetoothDevice selectedDevice = devices.get(arg2);
            ConnectThread connect = new ConnectThread(selectedDevice);
            connect.start();
            selDevice = selectedDevice;
        }else {
            Toast.makeText(getApplicationContext(), "device is not paired", Toast.LENGTH_SHORT).show();
        }
    }

    public static BluetoothDevice getSelDevice(){
        return selDevice;
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        ConnectThread(BluetoothDevice device) {
            BluetoothSocket tmp = null;
            mmDevice = device;
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException ignored) { }
            mmSocket = tmp;
        }

        public void run() {
            btAdapter.cancelDiscovery();

            try {
                if(!mmSocket.isConnected())
                    mmSocket.connect();
                connectedThread = new ConnectedThread(mmSocket);
            } catch (IOException connectException) {
                try {
                    mmSocket.close();
                } catch (IOException closeException) { }
                return;
            }

            mHandler.obtainMessage(SUCCESS_CONNECT, mmSocket).sendToTarget();
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException ignored) { }
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
            } catch (IOException ignored) { }

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
                        mHandler.obtainMessage(MESSAGE_READ, bytes, -1, readMessage.toString()).sendToTarget();
                        readMessage.setLength(0);
                    }

                } catch (IOException ignored) {
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
            } catch (IOException ignored) { }
        }

        void cancel() {
            try {
                mmSocket.close();
            } catch (IOException ignored) { }
        }
    }

    static class DEBUGThread extends Thread {
        public void run() {
            while (true) {
                try {
                    try {
                        sleep(300);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    int temp = (int)((Math.random()*50+1));
                    mHandler.obtainMessage(MESSAGE_READ, "s"+temp+"\n").sendToTarget();

                } catch (Exception e) {
                    break;
                }
            }
        }
    }

}

