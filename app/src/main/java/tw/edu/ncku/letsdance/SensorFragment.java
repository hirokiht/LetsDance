package tw.edu.ncku.letsdance;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.app.Fragment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.Arrays;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link SensorFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class SensorFragment extends Fragment {
    private BluetoothDevice device;
    private String mac = "";
    private short interval = 500;
    private SharedPreferences.OnSharedPreferenceChangeListener listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if(!key.equals("interval"))
                return;
            String intStr = sharedPreferences.getString("interval",null);
            try{
                interval = Short.parseShort(intStr);
            }catch(NumberFormatException nfe){
                Log.e("onCreateSensorFragment", "Unable to parse interval string into short!");
            }
            BleService.setSensorNotificationPeriod(device, Sensor.ACCELEROMETER2G, interval);
            BleService.setSensorNotificationPeriod(device, Sensor.GYROSCOPE_XY, interval);
        }
    };
    private float[] acc = null, gyro = null, lpfAcc = null, deg = {0.0f,0.0f,0.0f};
    private float aAcc = 0.90f, alpha = 0.96f;              //alpha for lpf and complimentary filter

    private ServiceConnection sc = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            device = BleService.connectGattDevice(getActivity(), mac);
            BleService.enableSensor(device,Sensor.ACCELEROMETER2G);
            BleService.enableSensor(device, Sensor.GYROSCOPE_XY);
            BleService.setSensorNotificationPeriod(device, Sensor.ACCELEROMETER2G, interval);
            BleService.setSensorNotificationPeriod(device, Sensor.GYROSCOPE_XY, interval);
            BleService.setSensorNotification(device, Sensor.ACCELEROMETER2G, true);
            BleService.setSensorNotification(device, Sensor.GYROSCOPE_XY, true);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };
    private LineChart sensorChart;
    private LineData sensorData = new LineData(new ArrayList<String>(), Arrays.asList(new LineDataSet[]{
            new LineDataSet(null, "degX"), new LineDataSet(null, "degY"), new LineDataSet(null, "degZ")}));

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment SensorFragment.
     */
    public static SensorFragment newInstance(String mac) {
        SensorFragment fragment = new SensorFragment();
        Bundle args = new Bundle();
        args.putString("mac", mac);
        fragment.setArguments(args);
        return fragment;
    }

    public SensorFragment() {
        for(int i = 0 ; i < sensorData.getDataSetCount() ; i++) {
            sensorData.getDataSetByIndex(i).setColor( 0xff << (8 * i) |0xff000000);
            sensorData.getDataSetByIndex(i).setCircleColor(0xff<<(8*i)|0xff000000);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(getArguments() == null)
            return;
        setRetainInstance(true);    // retain this fragment
        mac = getArguments().getString("mac");
        getActivity().getApplication().bindService(new Intent(getActivity(), BleService.class), sc, Activity.BIND_AUTO_CREATE);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener);
        String intervalStr = sharedPreferences.getString("interval","");
        try{
            interval = Short.parseShort(intervalStr);
        }catch(NumberFormatException nfe){
            Log.e("onCreateSensorFragment", "Unable to parse interval string into short!");
        }
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(new BroadcastReceiver(){
            @Override
            public void onReceive(Context context, Intent intent) {
                if (device == null || !device.equals(intent.getParcelableExtra("btDevice")))
                    return;
                String type = intent.getStringExtra("type");
                if(type.equals("read") || type.equals("notify")){
                    Sensor s = (Sensor) intent.getSerializableExtra(type);
                    byte[] data = intent.getByteArrayExtra("data");
                    float[] p = (s == Sensor.ACCELEROMETER)? Sensor.ACCELEROMETER2G.convert(data) : s.convert(data);
                    if(s == Sensor.ACCELEROMETER) {
                        lpfAcc = lpfAcc == null? p.clone() : new float[]{lpfAcc[0]+aAcc*(p[0]-acc[0]),
                                lpfAcc[1]+aAcc*(p[1]-acc[1]), lpfAcc[2]+aAcc*(p[2]-acc[2])};
                        acc = p.clone();
                    }else if(s == Sensor.GYROSCOPE)
                        gyro = p.clone();
                    if(gyro == null || acc == null)
                        return;
                    final float t = interval/1000.0f; //convert from ms to s
                    final float[] accDeg = {(float)Math.atan2(-lpfAcc   [1],lpfAcc[2])*180.0f/(float)Math.PI,
                            (float)Math.atan2(-lpfAcc[0],lpfAcc[2])*180.0f/(float)Math.PI,
                            (float)Math.atan2(lpfAcc[1],lpfAcc[0])*180.0f/(float)Math.PI};
                    final float[] gyroDeg = {deg[0]+gyro[0]*t,deg[1]+gyro[1]*t};
                    deg = new float[]{alpha*gyroDeg[0]+(1.0f-alpha)*accDeg[0],
                        alpha*gyroDeg[1]+(1.0f-alpha)*accDeg[1], accDeg[2]};
                    addEntry(deg);
                } else Log.d("onReceive", "broadcast received, type: " + type);
            }
        } ,new IntentFilter("btCb"));
    }

    @Override
    public void onDestroy(){
        getActivity().getApplication().unbindService(sc);
        super.onDestroy();
    }

    public void addEntry(float[] entries){
        if(entries == null || entries.length > 3)
            return;
        sensorData.addXValue(String.valueOf(sensorData.getXValCount()));
        for(int i = 0 ; i < entries.length ; i++)
            sensorData.addEntry(new Entry(entries[i], sensorData.getXValCount()),i);
        sensorChart.notifyDataSetChanged();
        sensorChart.setVisibleXRange(25);
        if(sensorData.getXValCount() > 25) {
            sensorChart.moveViewToX(sensorData.getXValCount() - 25);
        }
        sensorChart.invalidate();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_sensor, container, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sensorData.setDrawValues(false);
        sensorData.getDataSetByIndex(0).clear();
        sensorData.getDataSetByIndex(1).clear();
        sensorData.getDataSetByIndex(2).clear();
        sensorChart = (LineChart) view.findViewById(R.id.sensorChart);
        sensorChart.setData(sensorData);
        sensorChart.setDescription(mac+" Degree");
        sensorChart.getAxisLeft().setStartAtZero(false);
        sensorChart.getAxisLeft().setAxisMinValue(-450);
        sensorChart.getAxisLeft().setAxisMaxValue(450);
        view.setLayoutParams(params);
        return view;
    }
}
