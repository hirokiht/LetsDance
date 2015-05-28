package tw.edu.ncku.letsdance;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.app.Fragment;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link SensorFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class SensorFragment extends Fragment {
    private ServiceConnection sc;
    private TextView accVal, magVal;
    private BleService.LocalBinder serviceBinder;

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
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(getArguments() == null)
            return;
        setRetainInstance(true);    // retain this fragment
        String mac = getArguments().getString("mac");
        sc = new ServiceConnection(){
            @Override
            public void onServiceConnected(ComponentName name, IBinder service){
                serviceBinder = (BleService.LocalBinder)service;
            }
            @Override
            public void onServiceDisconnected(ComponentName name){
                serviceBinder = null;
            }
        };
        getActivity().getApplication().bindService(new Intent(getActivity(), BleService.class)
                .putExtra("mac", mac), sc, Activity.BIND_AUTO_CREATE);
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(new BroadcastReceiver(){
            @Override
            public void onReceive(Context context, Intent intent) {
                if(!serviceBinder.getDevice().equals(intent.getParcelableExtra("btDevice")))
                    return;
                String type = intent.getStringExtra("type");
                if(type.equals("ready") && intent.getBooleanExtra(type, false)){
                    serviceBinder.enableAccelerometer();
                    serviceBinder.enableMagnetometer();
                    serviceBinder.setMagnetonmeterNotification(true);
                }else if(type.equals("read") || type.equals("notify")){
                    Sensor s = (Sensor) intent.getSerializableExtra(type);
                    byte[] data = intent.getByteArrayExtra("data");
                    float[] p = s.convert(data);
                    if(s == Sensor.ACCELEROMETER)
                        accVal.setText("(" + p[0] + ",\n" + p[1] + ",\n" + p[2]+")");
                    else if(s == Sensor.MAGNETOMETER)
                        magVal.setText("(" + p[0] + ",\n" + p[1] + ",\n" + p[2]+")");
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
        final Button getAccButton = (Button) view.findViewById(R.id.getAccButton),
                getMagButton = (Button) view.findViewById(R.id.getMagButton);
        accVal = (TextView) view.findViewById(R.id.AccValLabel);
        magVal = (TextView) view.findViewById(R.id.MagValLabel);
        getAccButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                serviceBinder.readAccelerometer();
            }
        });
        getMagButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                serviceBinder.readMagnetometer();
            }
        });
        return view;
    }
}
