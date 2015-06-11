package tw.edu.ncku.letsdance;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;


public class MainActivity extends AppCompatActivity{
    private final static int REQUEST_ENABLE_BT = 1;
    private BluetoothManager btManager = null;
    private ProgressDialogFragment waitForBt  = null;
    private String[] mac = new String[] {"5C:31:3E:C0:20:85", "78:A5:04:19:59:A3", "B4:99:4C:34:DB:57"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null && savedInstanceState.getBoolean("noBt"))
            return;
        setContentView(R.layout.activity_main);
        btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
    }

    @Override
    protected void onStart(){
        if(btManager == null){
            super.onStart();
            AlertDialogFragment.newInstance(R.string.need_bt, R.string.bt_not_found)
                    .show(getFragmentManager(), "dialog");
            return;
        }
        final BluetoothAdapter btAdapter = btManager.getAdapter();
        if(btAdapter == null || !btAdapter.isEnabled()) {
            Log.d("MainActivity.onStart", "Trying to enable bt!");
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP), REQUEST_ENABLE_BT);
            waitForBt = ProgressDialogFragment.newInstance(R.string.bt_not_enabled, R.string.wait_for_bt_enable);
            waitForBt.show(getFragmentManager(), "dialog");
        }else{
            Log.d("MainActivity.onCreate", "Bt already enabled!");
            addSensorFragment(mac[1]);
            addSensorFragment(mac[2]);
        }
        super.onStart();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState){
        outState.putBoolean("noBt", btManager == null);
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
            if(resultCode == RESULT_OK || (btManager !=null && btManager.getAdapter() != null && btManager.getAdapter().isEnabled())){
                Log.d("onActivityResult", "Bt enabled!");
                addSensorFragment(mac[1]);
                addSensorFragment(mac[2]);
            }else{
                btManager = null;
                AlertDialogFragment.newInstance(R.string.need_bt, R.string.bt_not_found)
                        .show(getFragmentManager(),"dialog");
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

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void addSensorFragment(String mac){
        FragmentManager fm = getFragmentManager();
        SensorFragment sf = (SensorFragment) fm.findFragmentByTag(mac);
        if(sf == null) {
            FragmentTransaction ft = fm.beginTransaction();
            sf = SensorFragment.newInstance(mac);
            ft.add(R.id.MainLayout, sf, mac).commit();
        }

    }
}
