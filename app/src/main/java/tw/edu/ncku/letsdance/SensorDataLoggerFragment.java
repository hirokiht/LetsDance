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
import android.support.v4.util.SimpleArrayMap;
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
    private SimpleArrayMap<Sensor,ArrayList<float[]>> data = new SimpleArrayMap<>();
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
                    byte[] rawData = intent.getByteArrayExtra("data");
                    float[] p = (s == Sensor.ACCELEROMETER)? Sensor.ACCELEROMETER4G.convert(rawData) :
                            (s == Sensor.GYROSCOPE)? Sensor.GYROSCOPE_XY.convert(rawData) : s.convert(rawData);
                    if(!data.containsKey(s))
                        data.put(s,new ArrayList<float[]>());
                    data.get(s).add(p);
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
        for(int i = 0 ; i < data.size() ; i++){
            File fp = new File(dir,data.keyAt(i).name()+"-"+mac+".csv");
            BufferedWriter writer = new BufferedWriter(new FileWriter(fp));
            for(float[] d : data.valueAt(i)) {
                writer.write(Arrays.toString(d));
                writer.newLine();
            }
            writer.close();
        }
    }
}
