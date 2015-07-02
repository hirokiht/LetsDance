package tw.edu.ncku.letsdance;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.util.ArrayList;


/**
 * A simple {@link PreferenceFragment} subclass.
 */
public class SettingsFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {
    ListPreference[] macs = null;
    EditTextPreference interval = null;
    BluetoothManager btManager = null;
    ArrayList<String> addresses = new ArrayList<>();
    ArrayList<String> names = new ArrayList<>();

    public SettingsFragment() {
        addresses.add("");
        names.add("(none)");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        btManager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        addPreferencesFromResource(R.xml.preferences);
        macs = new ListPreference[]{(ListPreference)findPreference("mac0"),(ListPreference)findPreference("mac1"),
                (ListPreference)findPreference("mac2"),(ListPreference)findPreference("mac3")};
        interval = (EditTextPreference) findPreference("interval");
        interval.setSummary(interval.getText());
        interval.setOnPreferenceChangeListener(this);
        for(ListPreference mac : macs) {
            mac.setOnPreferenceChangeListener(this);
            mac.setOnPreferenceClickListener(this);
            mac.setEntries(names.toArray(new String[names.size()]));
            mac.setEntryValues(addresses.toArray(new String[addresses.size()]));
            if(mac.getValue() == null)
                mac.setValueIndex(0);
        }
        for(BluetoothDevice device : btManager.getConnectedDevices(BluetoothProfile.GATT)){
            if(addresses.contains(device.getAddress()))
                continue;
            addresses.add(device.getAddress());
            names.add(device.getName()+" ("+device.getAddress()+")");
            for(ListPreference mac : macs) {
                mac.setEntries(names.toArray(new String[names.size()]));
                mac.setEntryValues(addresses.toArray(new String[addresses.size()]));
            }
        }
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(new BroadcastReceiver(){
            @Override
            public void onReceive(Context context, Intent intent) {
                if(!intent.getStringExtra("type").equals("device"))
                    return;
                final BluetoothDevice device = intent.getParcelableExtra("device");
                if(addresses.contains(device.getAddress()))
                    return;
                addresses.add(device.getAddress());
                names.add(device.getName()+" ("+device.getAddress()+")");
                for(ListPreference mac : macs) {
                    mac.setEntries(names.toArray(new String[names.size()]));
                    mac.setEntryValues(addresses.toArray(new String[addresses.size()]));
                }
            }
        } ,new IntentFilter("btCb"));
        BleService.discoverDevices(getActivity());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if(preference == interval) {
            preference.setSummary(newValue.toString());
            return true;
        }
        if(newValue == null || newValue.toString().length() == 0)
            return true;
        for(ListPreference mac : macs)
            if(newValue.equals(mac.getValue())) {
                Toast.makeText(getActivity(),"Already selected!",Toast.LENGTH_SHORT).show();
                return false;
            }
        return true;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        return true;
    }
}
