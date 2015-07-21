package tw.edu.ncku.letsdance;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.IOException;


public class MainActivity extends AppCompatActivity{
    private final static int REQUEST_ENABLE_BT = 1;
    private BluetoothManager btManager = null;
    private FragmentManager fragmentManager = null;
    private ProgressDialogFragment waitForBt  = null;
    private String[] macs = null;
    private boolean addedFragments = false;
    private SensorDataLoggerFragment[] loggerFragments = null;
    private SharedPreferences preferences;
    private boolean log = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null && savedInstanceState.getBoolean("noBt"))
            return;
        if(savedInstanceState != null)
            addedFragments = savedInstanceState.getBoolean("addedFragments");
        setContentView(R.layout.activity_main);
        btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        fragmentManager = getFragmentManager();
    }

    @Override
    protected void onStart(){
        if(btManager == null){
            super.onStart();
            AlertDialogFragment.newInstance(R.string.need_bt, R.string.bt_not_found)
                    .show(fragmentManager, "dialog");
            return;
        }
        final BluetoothAdapter btAdapter = btManager.getAdapter();
        if(btAdapter == null || !btAdapter.isEnabled()) {
            Log.d("MainActivity.onStart", "Trying to enable bt!");
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP), REQUEST_ENABLE_BT);
            waitForBt = ProgressDialogFragment.newInstance(R.string.bt_not_enabled, R.string.wait_for_bt_enable);
            waitForBt.show(fragmentManager, "dialog");
        }else Log.d("MainActivity.onStart", "Bt already enabled!");
        super.onStart();
    }

    @Override
    protected void onResume(){  //this will occur after onStart hence will be called when bt is enabled
        super.onResume();
        if(addedFragments || btManager == null || btManager.getAdapter() == null || !btManager.getAdapter().isEnabled())
            return;
        addedFragments = true;
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        macs = new String[]{ preferences.getString("mac0",null),preferences.getString("mac1",null),
                preferences.getString("mac2",null), preferences.getString("mac3",null) };
        for(String mac : macs)
            if(mac != null && mac.length() == 17)
                addSensorFragment(mac);
        loggerFragments = new SensorDataLoggerFragment[macs.length];
        FragmentTransaction ft = fragmentManager.beginTransaction();
        for(int i = 0 ; i < loggerFragments.length ; i++) {
            if(macs[i] == null || macs[i].length() != 17)
                continue;
            loggerFragments[i] = SensorDataLoggerFragment.newInstance(macs[i]);
            ft.add(loggerFragments[i], null);
        }
        ft.commit();
        LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String type = intent.getStringExtra("type");
                if (!type.equals("read") && !type.equals("notify"))
                    return;
                updateDeviceSensorData((BluetoothDevice)intent.getParcelableExtra("btDevice"),
                        (Sensor)intent.getSerializableExtra(type),intent.getByteArrayExtra("data"));
            }
        }, new IntentFilter("btCb"));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState){
        outState.putBoolean("noBt", btManager == null);
        outState.putBoolean("addedFragments", addedFragments);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onStop(){
        finishActivity(REQUEST_ENABLE_BT);
        super.onStop();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        if(requestCode == REQUEST_ENABLE_BT){
            waitForBt.dismiss();
            if(resultCode == RESULT_OK || (btManager !=null && btManager.getAdapter() != null && btManager.getAdapter().isEnabled()))
                Log.d("onActivityResult", "Bt enabled!");
            else{
                btManager = null;
                AlertDialogFragment.newInstance(R.string.need_bt, R.string.bt_not_found)
                        .show(fragmentManager,"dialog");
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        ActionBar ab = getSupportActionBar();
        FragmentTransaction ft = fragmentManager.beginTransaction();
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            for(String mac : macs)
                if(mac != null && mac.length() == 17)
                    ft.hide(fragmentManager.findFragmentByTag(mac));
            ft.add(R.id.MainLayout, new SettingsFragment()).addToBackStack(null).commit();
            findViewById(R.id.action_settings).setVisibility(View.GONE);
            findViewById(R.id.BtnLyt).setVisibility(View.GONE);
            if(ab != null) {
                ab.setDisplayHomeAsUpEnabled(true);
                ab.setSubtitle("Settings");
            }
            return true;
        }else if(id == android.R.id.home){
            if(fragmentManager.getBackStackEntryCount() == 0)
                return super.onOptionsItemSelected(item);
            onBackPressed();
            return true;
        }else Log.d("onOptionsItemSelected","id: "+id);

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (!fragmentManager.popBackStackImmediate()){
            super.onBackPressed();
            return;
        }
        findViewById(R.id.action_settings).setVisibility(View.VISIBLE);
        findViewById(R.id.BtnLyt).setVisibility(View.VISIBLE);
        ActionBar ab = getSupportActionBar();
        if(ab != null) {
            ab.setDisplayHomeAsUpEnabled(false);
            ab.setSubtitle(null);
        }
        FragmentTransaction ft = fragmentManager.beginTransaction();
        for(int i = 0 ; i < 4 ; i++) {
            String newMac = preferences.getString("mac" + (char)('0'+i), null);
            if(macs[i] != null && macs[i].length() == 17 && !macs[i].equals(newMac)) {
                ft.remove(fragmentManager.findFragmentByTag(macs[i]));
                ft.remove(loggerFragments[i]);
                macs[i] = null;
            }
            if(newMac != null && newMac.length() == 17 && !newMac.equals(macs[i])){
                macs[i] = newMac;
                ft.add(R.id.MainLayout, SensorFragment.newInstance(macs[i]), macs[i]);
                loggerFragments[i] = SensorDataLoggerFragment.newInstance(macs[i]);
                ft.add(loggerFragments[i],null);
            }
        }
        ft.commit();
    }

    private void addSensorFragment(String mac){
        if(fragmentManager.findFragmentByTag(mac) == null) {
            FragmentTransaction ft = fragmentManager.beginTransaction();
            ft.add(R.id.MainLayout, SensorFragment.newInstance(mac), mac).commit();
        }
    }

    private void updateDeviceSensorData(BluetoothDevice device, Sensor sensor, byte[] data){
        float[] p = (sensor == Sensor.ACCELEROMETER) ? Sensor.ACCELEROMETER4G.convert(data) :
                (sensor == Sensor.GYROSCOPE) ? Sensor.GYROSCOPE_XY.convert(data) : sensor.convert(data);
        for (int i = 0; i < macs.length; i++)
            if (macs[i] != null && macs[i].equals(device.getAddress()))
                if (log && loggerFragments[i] != null)
                    loggerFragments[i].logData(sensor.name(), p);
    }

    public void onToggleClicked(View view){
        ToggleButton btn = (ToggleButton) view;
        log = btn.isChecked();
    }

    public void onSaveClicked(View view){
        for(SensorDataLoggerFragment logFrag : loggerFragments)
            if(logFrag != null)
                try{
                    logFrag.writeToExtStorage();
                }catch(IOException ioe){
                    Toast.makeText(this,ioe.getMessage(),Toast.LENGTH_SHORT).show();
                    ioe.printStackTrace();
                }
    }
}
