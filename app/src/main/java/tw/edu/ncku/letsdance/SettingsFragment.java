package tw.edu.ncku.letsdance;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.widget.Toast;

import java.util.ArrayList;


/**
 * A simple {@link PreferenceFragment} subclass.
 */
public class SettingsFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener{
    ListPreference[] macs = null;
    EditTextPreference interval = null;
    ArrayList<String> addresses = new ArrayList<>(), names = new ArrayList<>();

    public SettingsFragment() {
        addresses.add("");
        names.add("(none)");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        macs = new ListPreference[]{(ListPreference)findPreference("mac0"),(ListPreference)findPreference("mac1"),
                (ListPreference)findPreference("mac2"),(ListPreference)findPreference("mac3")};
        interval = (EditTextPreference) findPreference("interval");
        interval.setSummary(interval.getText());
        interval.setOnPreferenceChangeListener(this);
        for(ListPreference mac : macs) {
            mac.setOnPreferenceChangeListener(this);
            mac.setEntries(names.toArray(new String[names.size()]));
            mac.setEntryValues(addresses.toArray(new String[addresses.size()]));
            if(mac.getValue() == null)
                mac.setValueIndex(0);
        }
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

    public void addBluetoothDeviceToList(BluetoothDevice device){
        if(addresses.contains(device.getAddress()))
            return;
        addresses.add(device.getAddress());
        names.add(device.getName() != null? device.getName()+" ("+device.getAddress()+")" : device.getAddress());
        for(ListPreference mac : macs) {
            mac.setEntries(names.toArray(new String[names.size()]));
            mac.setEntryValues(addresses.toArray(new String[addresses.size()]));
        }
    }
}