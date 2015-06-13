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
import android.view.View;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.IOException;


public class MainActivity extends AppCompatActivity{
    private final static int REQUEST_ENABLE_BT = 1;
    private BluetoothManager btManager = null;
    private ProgressDialogFragment waitForBt  = null;
    private String[] mac = new String[] {"5C:31:3E:C0:20:85", "78:A5:04:19:59:A3",
            "B4:99:4C:34:DB:57", "B4:99:4C:64:AF:D1"};
    private boolean addedFragments = false;
    private SensorDataLoggerFragment[] loggerFragments = new SensorDataLoggerFragment[4];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null && savedInstanceState.getBoolean("noBt"))
            return;
        if(savedInstanceState != null)
            addedFragments = savedInstanceState.getBoolean("addedFragments");
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
        }else Log.d("MainActivity.onStart", "Bt already enabled!");
        super.onStart();
    }

    @Override
    protected void onResume(){  //this will occur after onStart hence will be called when bt is enabled
        super.onResume();
        if(addedFragments || btManager == null || btManager.getAdapter() == null || !btManager.getAdapter().isEnabled())
            return;
        addedFragments = true;
      addSensorFragment(mac[0]);
        addSensorFragment(mac[1]);
        addSensorFragment(mac[2]);
        addSensorFragment(mac[3]);
        FragmentManager fm = getFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        for(int i = 0 ; i < loggerFragments.length ; i++) {
            loggerFragments[i] = SensorDataLoggerFragment.newInstance(mac[i]);
            ft.add(loggerFragments[i], null);
        }
        ft.commit();
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

    public void onToggleClicked(View view){
        ToggleButton btn = (ToggleButton) view;
        for(SensorDataLoggerFragment logFrag : loggerFragments)
            if(logFrag != null)
                if(btn.isChecked())
                    logFrag.start();
                else
                    logFrag.stop();
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
