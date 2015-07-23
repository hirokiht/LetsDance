package tw.edu.ncku.letsdance;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
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
    private Boolean btEnable = null;
    private FragmentManager fragmentManager = null;
    private String[] macs = null;
    private short interval = 500;
    private boolean addedFragments = false;
    private SensorDataLoggerFragment loggerFragment = SensorDataLoggerFragment.newInstance();
    private SharedPreferences preferences;
    private ToggleButton logBtn = null;

    private ServiceConnection sc = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            for(String mac : macs) {
                if(mac == null || mac.length() != 17)
                    continue;
                BluetoothDevice device = BleService.connectGattDevice(MainActivity.this, mac);
                BleService.enableSensor(device, Sensor.ACCELEROMETER2G);
                BleService.enableSensor(device, Sensor.GYROSCOPE_XY);
                BleService.setSensorNotificationPeriod(device, Sensor.ACCELEROMETER2G, interval);
                BleService.setSensorNotificationPeriod(device, Sensor.GYROSCOPE_XY, interval);
                BleService.setSensorNotification(device, Sensor.ACCELEROMETER2G, true);
                BleService.setSensorNotification(device, Sensor.GYROSCOPE_XY, true);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fragmentManager = getFragmentManager();
        if(savedInstanceState != null && savedInstanceState.containsKey("btEnable") &&
                !savedInstanceState.getBoolean("btEnable") ) {
            btEnable = false;
            return;
        }
        if(savedInstanceState != null)
            addedFragments = savedInstanceState.getBoolean("addedFragments");
        setContentView(R.layout.activity_main);
        if(BluetoothAdapter.getDefaultAdapter() == null) {
            btEnable = false;
            onActivityResult(REQUEST_ENABLE_BT,RESULT_CANCELED,null);   //show error
        }else if(BluetoothAdapter.getDefaultAdapter().isEnabled())
            btEnable = true;
        else{
            if(fragmentManager.findFragmentByTag("waitForBt") == null)
                new DialogFragment(){
                    @Override
                    public Dialog onCreateDialog(Bundle savedInstanceState) {
                        return ProgressDialog.show(getActivity(), getString(R.string.bt_not_enabled), getString(R.string.wait_for_bt_enable), true, true);
                    }
                }.show(fragmentManager, "waitForBt");
            if(savedInstanceState == null)
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP), REQUEST_ENABLE_BT);
            Log.d("MainActivity.onCreate", "Waiting for enabling bt!");
        }
    }

    @Override
    protected void onResume(){  //this will occur after onStart hence will be called when bt is enabled
        super.onResume();
        logBtn = (ToggleButton) findViewById(R.id.logBtn);
        if(addedFragments || BluetoothAdapter.getDefaultAdapter() == null || !BluetoothAdapter.getDefaultAdapter().isEnabled())
            return;
        addedFragments = true;
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        macs = new String[]{ preferences.getString("mac0",null),preferences.getString("mac1",null),
                preferences.getString("mac2",null), preferences.getString("mac3",null) };
        String intStr = preferences.getString("interval",null);
        try{
            interval = Short.parseShort(intStr);
        }catch(NumberFormatException nfe){
            Log.e("onCreateSensorFragment", "Unable to parse interval string into short!");
        }
        for(String mac : macs)
            if(mac != null && mac.length() == 17)
                addSensorFragment(mac);
        FragmentTransaction ft = fragmentManager.beginTransaction();
        ft.add(loggerFragment, null);
        ft.commit();
        LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String type = intent.getStringExtra("type");
                if (!type.equals("read") && !type.equals("notify"))
                    return;
                updateDeviceSensorData((BluetoothDevice) intent.getParcelableExtra("btDevice"),
                        (Sensor) intent.getSerializableExtra(type), intent.getByteArrayExtra("data"));
            }
        }, new IntentFilter("btCb"));
        getApplication().bindService(new Intent(this, BleService.class), sc, BIND_AUTO_CREATE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        finishActivity(REQUEST_ENABLE_BT);
        outState.putBoolean("addedFragments", addedFragments);
        if(btEnable != null)
            outState.putBoolean("btEnable",btEnable);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onStop() {
        finishActivity(REQUEST_ENABLE_BT);
        super.onStop();
    }

    @Override
    protected void onActivityResult(int requestCode, final int resultCode, Intent data){
        if(requestCode == REQUEST_ENABLE_BT){
            if(btEnable == null) {
                btEnable = (resultCode == RESULT_OK || BluetoothAdapter.getDefaultAdapter().isEnabled());
                if (!btEnable){ //wait for a while for BT to be enabled, resultCode may not be accurate
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            btEnable = BluetoothAdapter.getDefaultAdapter().isEnabled();
                            onActivityResult(REQUEST_ENABLE_BT, resultCode, null);
                        }
                    }, 2000);
                    return;
                }
            }
            if (fragmentManager.findFragmentByTag("waitForBt") != null)
                ((DialogFragment)fragmentManager.findFragmentByTag("waitForBt")).dismissAllowingStateLoss();
            if(!btEnable)
                new DialogFragment(){
                    @Override
                    public Dialog onCreateDialog(Bundle savedInstanceState) {
                        return new AlertDialog.Builder(getActivity()).setTitle(R.string.need_bt).setMessage(R.string.bt_not_found)
                                .setNeutralButton("OK", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dismiss();
                                        getActivity().finish();
                                    }
                                }).create();
                    }
                }.show(fragmentManager, "NoBtAlertDialog");
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
        String intStr = preferences.getString("interval",null);
        try{
            if(Short.parseShort(intStr) != interval){
                interval = Short.parseShort(intStr);
                for(String mac : macs)
                    if(mac != null && mac.length() == 17) {
                        BleService.setSensorNotificationPeriod(mac, Sensor.ACCELEROMETER2G, interval);
                        BleService.setSensorNotificationPeriod(mac, Sensor.GYROSCOPE_XY, interval);
                        ((SensorFragment) fragmentManager.findFragmentByTag(mac)).setInterval(interval);
                    }
            }
        }catch(NumberFormatException nfe){
            Log.e("onCreateSensorFragment", "Unable to parse interval string into short!");
        }
        FragmentTransaction ft = fragmentManager.beginTransaction();
        for(int i = 0 ; i < 4 ; i++) {
            String newMac = preferences.getString("mac" + (char)('0'+i), null);
            if(macs[i] != null && macs[i].length() == 17 && !macs[i].equals(newMac)) {
                ft.remove(fragmentManager.findFragmentByTag(macs[i]));
                macs[i] = null;
            }
            if(newMac != null && newMac.length() == 17 && !newMac.equals(macs[i])){
                macs[i] = newMac;
                ft.add(R.id.MainLayout, SensorFragment.newInstance(macs[i], interval), macs[i]);
            }
        }
        ft.commit();
    }

    private void addSensorFragment(String mac){
        if(fragmentManager.findFragmentByTag(mac) == null) {
            FragmentTransaction ft = fragmentManager.beginTransaction();
            ft.add(R.id.MainLayout, SensorFragment.newInstance(mac,interval), mac).commit();
        }
    }

    private void updateDeviceSensorData(BluetoothDevice device, Sensor sensor, byte[] data){
        float[] p = (sensor == Sensor.ACCELEROMETER) ? Sensor.ACCELEROMETER2G.convert(data) :
                (sensor == Sensor.GYROSCOPE) ? Sensor.GYROSCOPE_XY.convert(data) : sensor.convert(data);
        for (String mac : macs)
            if (logBtn.isChecked() && mac != null && mac.equals(device.getAddress()))
                loggerFragment.logData(device.getAddress()+"-"+sensor.name(), p);
        SensorFragment sf = (SensorFragment) fragmentManager.findFragmentByTag(device.getAddress());
        if(sf != null)
            sf.addSensorData(sensor,p);
    }

    public void onSaveClicked(View view){
        try{
            loggerFragment.writeToExtStorage();
        }catch(IOException ioe){
            Toast.makeText(this,ioe.getMessage(),Toast.LENGTH_SHORT).show();
            ioe.printStackTrace();
        }
    }
}
