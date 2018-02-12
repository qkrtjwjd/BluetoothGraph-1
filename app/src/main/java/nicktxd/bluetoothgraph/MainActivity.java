package nicktxd.bluetoothgraph;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.ToggleButton;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.DataPointInterface;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.OnDataPointTapListener;
import com.jjoe64.graphview.series.Series;

public class MainActivity extends Activity {

    private int zero;
    private int sensetivity;
    private double acceleration;

    private int lastType = 0;
    private int curType = 0;
    private int typeOfData = 0;

    private SharedPreferences sharedPref;

    private double graphLastXValue = 0;
    private int maxCountValues;
    private LineGraphSeries<DataPoint> series;
    private ToggleButton tbtScroll;
    private ToggleButton tbtPause;
    private boolean connected = false;
    private boolean debug = false;
    private boolean autoScroll = true;
    private boolean pause = false;

    private class objToSave{
        private LineGraphSeries<DataPoint> series;
        private double graphLastXValue;
        private boolean autoScroll;
        private boolean pause;
        private boolean connected;
        private boolean debug;
        private int typeOfData;
    }
    private objToSave saveState;
    private objToSave retState;

    @Override
    public Object onRetainNonConfigurationInstance() {
        //Сохранить данные
        saveState.series = series;
        saveState.graphLastXValue = graphLastXValue;
        saveState.autoScroll = autoScroll;
        saveState.pause = pause;
        saveState.connected = connected;
        saveState.debug = debug;
        saveState.typeOfData = typeOfData;

        return saveState;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        BluetoothActivity.gethandler(mHandler);
        saveState = new objToSave();
        retState = (objToSave) getLastNonConfigurationInstance();
        graphInit();
        Init();

    }

    void graphInit() {

        GraphView graph = (GraphView) findViewById(R.id.graph);

        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setYAxisBoundsManual(true);

        graph.getViewport().setScalable(true);
        graph.getViewport().setScalableY(true);
        graph.getViewport().setScrollable(true);
        graph.getViewport().setScrollableY(true);

        graph.getViewport().setMinY(-1000);
        graph.getViewport().setMaxY(1000);
        graph.getViewport().setMaxX(50);




        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(5);
        paint.setPathEffect(new DashPathEffect(new float[]{8, 5}, 0));

        //graph.getGridLabelRenderer().setVerticalAxisTitle("Data");
        //graph.getGridLabelRenderer().setHorizontalAxisTitle("Number of data point");
        graph.getGridLabelRenderer().setVerticalAxisTitleTextSize(35);
        graph.getGridLabelRenderer().setHorizontalAxisTitleTextSize(35);
        graph.getGridLabelRenderer().setLabelVerticalWidth(80);
        graph.getGridLabelRenderer().setTextSize(30);


        if (retState == null) {
            series = new LineGraphSeries<>();
            series.setTitle("Graph");
            series.setBackgroundColor(Color.BLACK);
            series.setDrawDataPoints(true);
            series.setDataPointsRadius(5);
            series.setThickness(4);
            series.setCustomPaint(paint);
        }else {
            series = retState.series;
            graphLastXValue = retState.graphLastXValue;
        }

        graph.addSeries(series);
        series.setOnDataPointTapListener(new OnDataPointTapListener() {
            @Override
            public void onTap(Series series, DataPointInterface dataPoint) {
                Toast.makeText(getApplicationContext(), "On Data Point clicked: " + dataPoint, Toast.LENGTH_SHORT).show();
            }
        });
    }

    void Init() {
        Spinner typeOfDataList = (Spinner) findViewById(R.id.spinner);
        String[] typeOfDataStrings = {"RAW", "G", "M/S²"};
        ArrayAdapter<String> typeOfDataAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, typeOfDataStrings);
        typeOfDataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeOfDataList.setAdapter(typeOfDataAdapter);
        typeOfDataList.setOnItemSelectedListener(itemSelectedListener);

        Button btReset = (Button) findViewById(R.id.bReset);
        btReset.setOnClickListener(onClickListener);
        tbtScroll = (ToggleButton) findViewById(R.id.tbScroll);
        tbtScroll.setChecked(true);
        tbtScroll.setOnClickListener(onClickListener);
        tbtPause = (ToggleButton) findViewById(R.id.tbPause);
        tbtPause.setChecked(false);
        tbtPause.setOnClickListener(onClickListener);

        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        try {
            maxCountValues = Integer.parseInt(sharedPref.getString("max_count_values", ""));
            zero = Integer.parseInt(sharedPref.getString("zero", ""));
            sensetivity = Integer.parseInt(sharedPref.getString("sensetivity", ""));
            acceleration = Double.parseDouble(sharedPref.getString("acceleration", ""));
        }catch (Exception ignored){}

        sharedPref.registerOnSharedPreferenceChangeListener(settingsChangedListener);

        if(retState != null){
            typeOfData = retState.typeOfData;
            autoScroll = retState.autoScroll;
            tbtScroll.setChecked(autoScroll);
            pause = retState.pause;
            tbtPause.setChecked(pause);
            connected = retState.connected;
            debug = retState.debug;
            if(connected) {
                setTitle(BluetoothActivity.getSelDevice().getName());
            }
            if (debug){
                setTitle("Debug");
            }
        }
    }

    void resetGraph(){
        series.resetData(new DataPoint[]{new DataPoint(0,0)});
        graphLastXValue = 0;
        series.appendData(new DataPoint(0, 0), autoScroll, maxCountValues);
    }

    double rawToG(double rawData){
        return (rawData - zero)/sensetivity;
    }

    double rawToAc(double rawData){
        return (rawData - zero)*acceleration/sensetivity;
    }

    OnSharedPreferenceChangeListener settingsChangedListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
            try {
                maxCountValues = Integer.parseInt(sharedPref.getString("max_count_values", ""));
                zero = Integer.parseInt(sharedPref.getString("zero", ""));
                sensetivity = Integer.parseInt(sharedPref.getString("sensetivity", ""));
                acceleration = Double.parseDouble(sharedPref.getString("acceleration", ""));
            }catch (Exception ignored){}
        }
    };

    View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            switch (view.getId()){
                case R.id.tbScroll:
                    autoScroll = tbtScroll.isChecked();
                    break;
                case R.id.tbPause:
                    pause = tbtPause.isChecked();
                    break;
                case R.id.bReset:
                    resetGraph();
                    break;
            }
        }
    };

    AdapterView.OnItemSelectedListener itemSelectedListener = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
            typeOfData = i;
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {

        }
    };

    @SuppressLint("HandlerLeak")
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {

                case BluetoothActivity.SUCCESS_CONNECT:
                    BluetoothActivity.connectedThread = new BluetoothActivity.ConnectedThread((BluetoothSocket) msg.obj);
                    BluetoothActivity.connectedThread.start();
                    connected = true;
                    setTitle(BluetoothActivity.getSelDevice().getName());
                    break;

                case BluetoothActivity.MESSAGE_READ:
                    String strIncom = (String) msg.obj;
                    if (strIncom == null)
                        break;
                    if (strIncom.indexOf('s') == 0) {
                        boolean flag = true;
                        try {
                            strIncom = strIncom.replace("s", "");
                            strIncom = strIncom.substring(0, strIncom.indexOf('\n'));
                        } catch (Exception e) {
                            flag = false;
                        }
                        if (isFloatNumber(strIncom) && flag && !pause) {
                            double rawData = Double.parseDouble(strIncom);
                            double procData = 0;
                            switch (typeOfData){
                                case 0:
                                    procData = rawData;
                                    curType = 0;
                                    break;
                                case 1:
                                    procData = rawToG(rawData);
                                    curType = 1;
                                    break;
                                case 2:
                                    procData = rawToAc(rawData);
                                    curType = 2;
                                    break;
                            }
                            if (curType != lastType){
                                resetGraph();
                                lastType = curType;
                            }

                            try {
                                series.appendData(new DataPoint(graphLastXValue, procData), autoScroll, maxCountValues);
                            }catch (Exception ignored){}

                            graphLastXValue+=1;
                        }
                    }
                    break;
            }
        }
        boolean isFloatNumber(String num){
            try{
                Double.parseDouble(num);
            } catch(NumberFormatException nfe) {
                return false;
            }
            return true;
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.bluetooth_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.menu_bluetooth:
                if(connected) {
                    Toast.makeText(getApplicationContext(), "Connection already established", Toast.LENGTH_SHORT).show();
                    break;
                }
                if(debug){
                    Toast.makeText(getApplicationContext(), "Debug already started", Toast.LENGTH_SHORT).show();
                    break;
                }
                startActivity(new Intent("android.intent.action.BT"));
                break;
            case R.id.settings:
                startActivity(new Intent("android.intent.action.SET"));
                break;
            case R.id.Disconnect:
                if(connected){
                    BluetoothActivity.disconnect();
                    connected = false;
                    setTitle(getString(R.string.app_name));
                }
                if(debug){
                    BluetoothActivity.stopDebug();
                    debug = false;
                    setTitle(getString(R.string.app_name));

                }
                break;
            case R.id.Debug:
                if(connected) {
                    Toast.makeText(getApplicationContext(), "Connection already established", Toast.LENGTH_SHORT).show();
                    break;
                }
                if(debug){
                    Toast.makeText(getApplicationContext(), "Debug already started", Toast.LENGTH_SHORT).show();
                    break;
                }
                BluetoothActivity.debugThread = new BluetoothActivity.DEBUGThread();
                BluetoothActivity.debugThread.start();
                setTitle(getString(R.string.debug));
                debug = true;
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}