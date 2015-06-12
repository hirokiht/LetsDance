package tw.edu.ncku.letsdance;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.app.Fragment;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.github.mikephil.charting.components.YAxis;
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
    private float[] magCalib = null;
    private ServiceConnection sc = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            device = BleService.connectGattDevice(getActivity(), mac);
            BleService.enableSensor(device,Sensor.ACCELEROMETER4G);
            BleService.enableSensor(device, Sensor.MAGNETOMETER);
            BleService.enableSensor(device, Sensor.GYROSCOPE);
            BleService.setSensorNotificationPeriod(device, Sensor.ACCELEROMETER4G, 500);
            BleService.setSensorNotificationPeriod(device, Sensor.MAGNETOMETER, 500);
            BleService.setSensorNotificationPeriod(device, Sensor.GYROSCOPE, 500);
            BleService.setSensorNotification(device, Sensor.ACCELEROMETER4G, true);
            BleService.setSensorNotification(device, Sensor.MAGNETOMETER, true);
            BleService.setSensorNotification(device, Sensor.GYROSCOPE, true);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };
    private LineChart sensorChart;
    private LineDataSet[] sensorDataSet = new LineDataSet[]{
        new LineDataSet(new ArrayList<Entry>(), "accX"),
        new LineDataSet(new ArrayList<Entry>(), "accY"),
        new LineDataSet(new ArrayList<Entry>(), "accZ"),
        new LineDataSet(new ArrayList<Entry>(), "gyroX"),
        new LineDataSet(new ArrayList<Entry>(), "gyroY"),
        new LineDataSet(new ArrayList<Entry>(), "gyroZ"),
        new LineDataSet(new ArrayList<Entry>(), "mag")};
    private LineData sensorData = new LineData(new ArrayList<String>(), Arrays.asList(sensorDataSet));

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
        int color[] = {Color.RED, Color.GREEN, Color.BLUE, Color.MAGENTA, Color.CYAN, Color.YELLOW, Color.GRAY};
        for(int i = 0 ; i < sensorDataSet.length ; i++){
            sensorDataSet[i].setDrawValues(false);
            sensorDataSet[i].setColor(color[i]);
            sensorDataSet[i].setCircleColor(color[i]);
            if(i >= 3)
                sensorDataSet[i].setAxisDependency(YAxis.AxisDependency.RIGHT);
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
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(new BroadcastReceiver(){
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!device.equals(intent.getParcelableExtra("btDevice")))
                    return;
                String type = intent.getStringExtra("type");
                if(type.equals("read") || type.equals("notify")){
                    Sensor s = (Sensor) intent.getSerializableExtra(type);
                    byte[] data = intent.getByteArrayExtra("data");
                    float[] p = (s == Sensor.ACCELEROMETER)? Sensor.ACCELEROMETER4G.convert(data) : s.convert(data);
                    if(s == Sensor.ACCELEROMETER) {
                        if(sensorDataSet[0].getEntryCount() >= sensorData.getXValCount())
                            sensorData.addXValue(String.valueOf(sensorData.getXValCount()));
                        sensorDataSet[0].addEntry(new Entry(p[0], sensorData.getXValCount()));
                        sensorDataSet[1].addEntry(new Entry(p[1], sensorData.getXValCount()));
                        sensorDataSet[2].addEntry(new Entry(p[2], sensorData.getXValCount()));
                        sensorChart.notifyDataSetChanged();
                        sensorChart.setVisibleXRange(25);
                        if(sensorData.getXValCount() > 25)
                            sensorChart.moveViewToX(sensorData.getXValCount()-25);
                        sensorChart.invalidate();
                    }else if(s == Sensor.MAGNETOMETER) {
                        if(magCalib == null){
                            magCalib = p.clone();
                            p[0] = p[1] = p[2] = 0f;
                        }else{
                            p[0] -= magCalib[0];
                            p[1] -= magCalib[1];
                            p[2] -= magCalib[2];
                        }
                        float val = (float) Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
                        if(sensorDataSet[6].getEntryCount() >= sensorData.getXValCount())
                            sensorData.addXValue(String.valueOf(sensorData.getXValCount()));
                        sensorDataSet[6].addEntry(new Entry(val, sensorData.getXValCount()));
                        sensorChart.notifyDataSetChanged();
                        sensorChart.setVisibleXRange(25);
                        if(sensorData.getXValCount() > 25)
                            sensorChart.moveViewToX(sensorData.getXValCount()-25);
                        sensorChart.invalidate();
                    }else if(s == Sensor.GYROSCOPE) {
                        if(sensorDataSet[3].getEntryCount() >= sensorData.getXValCount())
                            sensorData.addXValue(String.valueOf(sensorData.getXValCount()));
                        sensorDataSet[3].addEntry(new Entry(p[0], sensorData.getXValCount()));
                        sensorDataSet[4].addEntry(new Entry(p[1], sensorData.getXValCount()));
                        sensorDataSet[5].addEntry(new Entry(p[2], sensorData.getXValCount()));
                        sensorChart.notifyDataSetChanged();
                        sensorChart.setVisibleXRange(25);
                        if(sensorData.getXValCount() > 25)
                            sensorChart.moveViewToX(sensorData.getXValCount()-25);
                        sensorChart.invalidate();
                    }
                }else Log.d("onReceive","broadcast received, type: " + type);
            }
        } ,new IntentFilter("btCb"));
    }

    @Override
    public void onDestroy(){
        getActivity().getApplication().unbindService(sc);
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_sensor, container, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sensorChart = (LineChart) view.findViewById(R.id.sensorChart);
        sensorChart.setData(sensorData);
        sensorChart.setDescription(mac+" Accelerometer/Gyroscope/Magnetometer");
        sensorChart.getAxisLeft().setStartAtZero(false);
        sensorChart.getAxisLeft().setAxisMinValue(-4);
        sensorChart.getAxisLeft().setAxisMaxValue(4);
        sensorChart.getAxisRight().setStartAtZero(false);
        sensorChart.getAxisRight().setAxisMinValue(-250);
        sensorChart.getAxisRight().setAxisMaxValue(250);
        view.setLayoutParams(params);
        return view;
    }
}
