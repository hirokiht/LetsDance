package tw.edu.ncku.letsdance;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.app.Fragment;
import android.os.Environment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link SensorDataLoggerFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class SensorDataLoggerFragment extends Fragment {
    private String mac;
    private float[] magCalib = null;
    private ArrayList<float[]> acc = new ArrayList<>(), gyro = new ArrayList<>();
    private ArrayList<Float> mag = new ArrayList<>();
    private boolean log = false;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param mac Mac address of the Bluetooth Device to monitor.
     * @return A new instance of fragment SensorDataLoggerFragment.
     */
    public static SensorDataLoggerFragment newInstance(String mac) {
        SensorDataLoggerFragment fragment = new SensorDataLoggerFragment();
        Bundle args = new Bundle();
        args.putString("mac", mac);
        fragment.setArguments(args);
        return fragment;
    }

    public SensorDataLoggerFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() == null)
            return;
        mac = getArguments().getString("mac");
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(new BroadcastReceiver(){
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!log || !mac.equals(((BluetoothDevice)intent.getParcelableExtra("btDevice")).getAddress()))
                    return;
                String type = intent.getStringExtra("type");
                if(type.equals("read") || type.equals("notify")){
                    Sensor s = (Sensor) intent.getSerializableExtra(type);
                    byte[] data = intent.getByteArrayExtra("data");
                    float[] p = (s == Sensor.ACCELEROMETER)? Sensor.ACCELEROMETER4G.convert(data) : s.convert(data);
                    if(s == Sensor.ACCELEROMETER) {
                        acc.add(p);
                    }else if(s == Sensor.MAGNETOMETER){
                        if(magCalib == null){
                            magCalib = p.clone();
                            p[0] = p[1] = p[2] = 0f;
                        }else{
                            p[0] -= magCalib[0];
                            p[1] -= magCalib[1];
                            p[2] -= magCalib[2];
                        }
                        float val = (float) Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
                        mag.add(val);
                    }else if(s == Sensor.GYROSCOPE) {
                        gyro.add(p);
                    }
                }else Log.d("onReceive", "broadcast received, type: " + type);
            }
        } ,new IntentFilter("btCb"));
    }

    public void start(){
        log = true;
    }

    public void stop(){
        log = false;
    }

    public void writeToExtStorage() throws IOException{
        if(!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
            throw new IOException("ext storage not mounted! State: "+Environment.getExternalStorageState());
        final File dir = getActivity().getExternalFilesDir(null);
        File accFp = new File(dir,"acc-"+mac+".csv");
        File gyroFp = new File(dir,"gyro-"+mac+".csv");
        File magFp = new File(dir,"mag-"+mac+".csv");
        BufferedWriter accWriter = new BufferedWriter(new FileWriter(accFp));
        BufferedWriter gyroWriter = new BufferedWriter(new FileWriter(gyroFp));
        BufferedWriter magWriter = new BufferedWriter(new FileWriter(magFp));
        for(float[] a : acc){
            accWriter.write(Arrays.toString(a));
            accWriter.newLine();
        }
        for(float[] g : gyro){
            gyroWriter.write(Arrays.toString(g));
            gyroWriter.newLine();
        }
        magWriter.write(mag.toString());
        accWriter.close();
        gyroWriter.close();
        magWriter.close();
    }
}
