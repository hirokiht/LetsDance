package tw.edu.ncku.letsdance;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.ToggleButton;

import java.io.IOException;


public class MainActivity extends AppCompatActivity implements SensorFragment.GestureListener, GameControlFragment.NotifyListener, BleFragment.BleEventListener{
    private final static int REQUEST_ENABLE_BT = 1;
    private Boolean btEnable = null;
    private FragmentManager fragmentManager = null;
    private String[] macs = null;
    private short interval = 500;
    private SensorDataLoggerFragment loggerFragment = SensorDataLoggerFragment.newInstance();
    private SharedPreferences preferences;
    private ToggleButton logBtn = null;
    private Menu optionMenu;
    private static final int sensorFragmentContainerID = 0;//0 to hide, R.id.MainLayout to show;
    private GameControlFragment gameControl = new GameControlFragment();
    private static BleFragment bleFragment = new BleFragment();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fragmentManager = getFragmentManager();
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        macs = new String[]{ preferences.getString("mac0",null),preferences.getString("mac1",null),
                preferences.getString("mac2",null), preferences.getString("mac3",null) };
        try{
            String intervalStr = preferences.getString("interval",null);
            if(intervalStr != null)
                interval = Short.parseShort(intervalStr);
        }catch(NumberFormatException nfe){
            Log.e("onCreateSensorFragment", "Unable to parse interval string into short!");
        }
        fragmentManager.beginTransaction().add(loggerFragment, null).add(gameControl,null).add(bleFragment,null).commit();
        setContentView(R.layout.activity_main);
        if(savedInstanceState != null && savedInstanceState.containsKey("btEnable") &&
                !savedInstanceState.getBoolean("btEnable") ) {  //previously denied bt or no bt
            btEnable = false;
        }else if(BluetoothAdapter.getDefaultAdapter() == null) {    //currently no bluetooth
            btEnable = false;
            onActivityResult(REQUEST_ENABLE_BT,RESULT_CANCELED,null);   //show error
        }else if(BluetoothAdapter.getDefaultAdapter().isEnabled()) {    //bt enabled
            btEnable = true;
            addBtDependentComponents();
        }else{  //bt not enabled but have bt
            if(fragmentManager.findFragmentByTag("waitForBt") == null) {
                new DialogFragment() {
                    @Override
                    public Dialog onCreateDialog(Bundle savedInstanceState) {
                        this.setRetainInstance(true);
                        return ProgressDialog.show(getActivity(), getString(R.string.bt_not_enabled), getString(R.string.wait_for_bt_enable), true, true);
                    }
                }.show(fragmentManager, "waitForBt");
            }
            if(savedInstanceState == null)  //request bt if it is the first time starting this app
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP), REQUEST_ENABLE_BT);
            Log.d("MainActivity.onCreate", "Waiting for enabling bt!");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        finishActivity(REQUEST_ENABLE_BT);
        if(btEnable != null)
            outState.putBoolean("btEnable",btEnable);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause(){
        gameControl.stop();
        super.onPause();
    }

    @Override
    protected void onResume(){
        gameControl.start();
        super.onResume();
    }

    @Override
    protected void onStop() {
//        finishActivity(REQUEST_ENABLE_BT);
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
                ((DialogFragment)fragmentManager.findFragmentByTag("waitForBt")).dismiss();
            if(!btEnable)
                new DialogFragment(){
                    @Override
                    public Dialog onCreateDialog(Bundle savedInstanceState) {
                        this.setRetainInstance(true);
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
            else addBtDependentComponents();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        optionMenu = menu;
        logBtn = (ToggleButton) menu.findItem(R.id.logBtn).getActionView();
        logBtn.setTextOn("Stop Log");
        logBtn.setTextOff("Start Log");
        logBtn.setChecked(false);
        logBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked)
                    return;
                try{    //save when stop logging
                    loggerFragment.writeToExtStorage();
                }catch(IOException ioe){
                    Log.d("onCheckedChanged","IOException: "+ioe.getMessage());
                    ioe.printStackTrace();
                }
            }
        });
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
                if(mac != null && mac.length() == 17 && fragmentManager.findFragmentByTag(mac) != null)
                    ft.hide(fragmentManager.findFragmentByTag(mac));
            SettingsFragment sf = new SettingsFragment();
            ft.add(R.id.MainLayout, sf, "settingsFragment").addToBackStack(null).commit();
            bleFragment.discoverDevices();
            BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            for(BluetoothDevice device : btManager.getConnectedDevices(BluetoothProfile.GATT))
                sf.addBluetoothDeviceToList(device);
            for(int i = 0 ; i < optionMenu.size() ; i++)
                optionMenu.getItem(i).setVisible(false).collapseActionView();
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
        for(int i = 0 ; i < optionMenu.size() ; i++)
            optionMenu.getItem(i).setVisible(true);
        ActionBar ab = getSupportActionBar();
        if(ab != null) {
            ab.setDisplayHomeAsUpEnabled(false);
            ab.setSubtitle(null);
        }
        try{
            String intervalStr = preferences.getString("interval", null);
            short newInterval = intervalStr==null? interval : Short.parseShort(intervalStr);
            if(newInterval != interval){
                interval = newInterval;
                for(String mac : macs)
                    if(mac != null && mac.length() == 17) {
                        BleFragment.setSensorNotificationPeriod(mac, Sensor.ACCELEROMETER2G, interval);
                        BleFragment.setSensorNotificationPeriod(mac, Sensor.GYROSCOPE_XY, interval);
                        if(fragmentManager.findFragmentByTag(mac) != null)
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
                if(fragmentManager.findFragmentByTag(macs[i]) != null)
                    ft.remove(fragmentManager.findFragmentByTag(macs[i]));
                macs[i] = null;
            }
            if(newMac != null && newMac.length() == 17 && !newMac.equals(macs[i])){
                macs[i] = newMac;
                ft.add(sensorFragmentContainerID, SensorFragment.newInstance(macs[i], interval), macs[i]);
            }
        }
        ft.commit();
    }

    private void addBtDependentComponents(){
        for(String mac : macs)
            if(mac != null && mac.length() == 17 && fragmentManager.findFragmentByTag(mac) == null)
                fragmentManager.beginTransaction().add(sensorFragmentContainerID, SensorFragment.newInstance(mac,interval), mac).commit();
        for(String mac : macs) {
            if(mac == null || mac.length() != 17)
                continue;
            BluetoothDevice device = bleFragment.connectGattDevice( getApplicationContext(), mac );
            BleFragment.enableSensor(device, Sensor.ACCELEROMETER2G);
            BleFragment.enableSensor(device, Sensor.GYROSCOPE_XY);
            BleFragment.setSensorNotificationPeriod(device, Sensor.ACCELEROMETER2G, interval);
            BleFragment.setSensorNotificationPeriod(device, Sensor.GYROSCOPE_XY, interval);
            BleFragment.setSensorNotification(device, Sensor.ACCELEROMETER2G, true);
            BleFragment.setSensorNotification(device, Sensor.GYROSCOPE_XY, true);
        }
        gameControl.start();
    }

    private void updateDeviceSensorData(BluetoothDevice device, Sensor sensor, byte[] data){
        float[] p = (sensor == Sensor.ACCELEROMETER) ? Sensor.ACCELEROMETER2G.convert(data) :
                (sensor == Sensor.GYROSCOPE) ? Sensor.GYROSCOPE_XY.convert(data) : sensor.convert(data);
        for (String mac : macs)
            if (logBtn != null && logBtn.isChecked() && mac != null && mac.equals(device.getAddress()))
                loggerFragment.logData(device.getAddress() + "-" + sensor.name(), p);
        SensorFragment sf = (SensorFragment) fragmentManager.findFragmentByTag(device.getAddress());
        if(sf != null)
            sf.addSensorData(sensor,p);
    }

    public void onNotifyDevices(byte devices){
        if(devices < 0 || devices > 0xF)
            return;
        for(int i = 0 ; i < macs.length ; i++)
            if((devices&(1<<i)) != 0)
                BleFragment.notifyDevice(macs[i]);
    }

    public void onGestureDetected(byte gesture, String mac){
        int index = -1;
        for(int i = 0 ; i < macs.length ; i++)
            if(macs[i].equals(mac))
                index = i;
        if(index == -1) {
            Log.e("onGestureDetected","Unrecognized device!");
            return;
        }
        if(gameControl.processGesture((byte)(1<<index),gesture))
            Log.d("onGestureDetected","Correct Gesture "+gesture+" detected!");
        Log.d("onGestureDetected","Gesture "+gesture+" detected from "+mac+" at "+gameControl.getMusicTime());
    }

    public void onBleEvent(Intent intent){
        String type = intent.getStringExtra("type");
        if (fragmentManager.findFragmentByTag("settingsFragment") != null && type.equals("device"))
            ((SettingsFragment) fragmentManager.findFragmentByTag("settingsFragment"))
                    .addBluetoothDeviceToList((BluetoothDevice) intent.getParcelableExtra("device"));
        if ((type.equals("read") || type.equals("notify")) && intent.getSerializableExtra(type) != null)
            updateDeviceSensorData((BluetoothDevice) intent.getParcelableExtra("btDevice"),
                    (Sensor) intent.getSerializableExtra(type), intent.getByteArrayExtra("data"));
    }
}
