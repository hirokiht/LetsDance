package tw.edu.ncku.letsdance;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.Arrays;

import static tw.edu.ncku.letsdance.Sensor.*;

public class BleService extends Service {
    private BluetoothManager btManager = null;

    private final BluetoothGattCallback btGattCb = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED)
                gatt.discoverServices();
            super.onConnectionStateChange(gatt, status, newState);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            enableAccelerometer(gatt);
            super.onServicesDiscovered(gatt, status);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d("onCharRead","read: "+characteristic.getUuid());
            byte[] val = characteristic.getValue();
            Log.d("onCharRead", "read: " + Arrays.toString(val));
            Point3D p = Sensor.getFromDataUuid(characteristic.getUuid()).convert(val);
            Log.d("onCharRead","read: ("+p.x+','+p.y+','+p.z+')');
            super.onCharacteristicRead(gatt, characteristic, status);
        }
    };

    public BleService() {
    }

    public class LocalBinder extends Binder {
        private BluetoothGatt btGatt = null;

        public LocalBinder(BluetoothGatt bg){
            super();
            btGatt = bg;
        }

        BleService getService() {
            return BleService.this;
        }

        void enableAccelerometer(){
            BleService.this.enableAccelerometer(btGatt);
        }

        boolean readAccelerometer(){
            return BleService.this.readAccelerometer(btGatt);
        }
    }

    public void enableAccelerometer(BluetoothGatt btGatt) {
        BluetoothGattService service = btGatt.getService(ACCELEROMETER.getService());
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(ACCELEROMETER.getConfig());
        characteristic.setValue((int) ACCELEROMETER.getEnableSensorCode(), BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        btGatt.writeCharacteristic(characteristic);
    }

    public boolean readAccelerometer(BluetoothGatt btGatt){
        if(btManager.getConnectionState(btGatt.getDevice(), BluetoothProfile.GATT) != BluetoothProfile.STATE_CONNECTED)
            return false;
        BluetoothGattService service = btGatt.getService(ACCELEROMETER.getService());
        if(service == null)
            return false;
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(ACCELEROMETER.getData());
        return characteristic!=null && btGatt.readCharacteristic(characteristic);
    }

    @Override
    public void onCreate(){
        btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        BluetoothAdapter btAdapter = btManager.getAdapter();
        if(btAdapter == null || !btAdapter.isEnabled())
            throw new UnsupportedOperationException("Bluetooth adapter is not enabled!");    //the Activity should enable adapter first!
        BluetoothDevice device;
        if(intent.getStringExtra("mac") != null)
            device = btAdapter.getRemoteDevice(intent.getStringExtra("mac"));
        else if(intent.getByteArrayExtra("mac") != null)
            device = btAdapter.getRemoteDevice(intent.getByteArrayExtra("mac"));
        else throw new UnsupportedOperationException("No MAC address specified!");
        return new LocalBinder(device.connectGatt(this,false,btGattCb));
    }
}
