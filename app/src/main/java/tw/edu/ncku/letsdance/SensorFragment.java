package tw.edu.ncku.letsdance;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.app.Fragment;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link SensorFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class SensorFragment extends Fragment {
    private ServiceConnection sc;
    private BleService.LocalBinder sensor;

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
                sensor = (BleService.LocalBinder)service;
                Toast.makeText(getActivity(), "Connected to BLE Service!", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onServiceDisconnected(ComponentName name){
                Toast.makeText(getActivity(), "Disconnected from BLE Service!", Toast.LENGTH_SHORT).show();
            }
        };
        getActivity().getApplication().bindService(new Intent(getActivity(), BleService.class).putExtra("mac", mac), sc, Activity.BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy(){
        getActivity().getApplication().unbindService(sc);
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_sensor, container, false);
        final Button button = (Button) view.findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sensor.readAccelerometer();
            }
        });
        return view;
    }

}
