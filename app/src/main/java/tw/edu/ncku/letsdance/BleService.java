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
import java.util.UUID;

import static tw.edu.ncku.letsdance.Sensor.*;

public class BleService extends Service {
    private final IBinder mBinder = new LocalBinder();
    private BluetoothGatt btGatt = null;
    private int connectionState = BluetoothProfile.STATE_DISCONNECTED;

    private final BluetoothGattCallback btGattCb = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            connectionState = newState;
            if (newState == BluetoothProfile.STATE_CONNECTED)
                gatt.discoverServices();
            super.onConnectionStateChange(gatt, status, newState);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            enableAccelerometer();
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
        BleService getService() {
            return BleService.this;
        }
    }

    public void enableAccelerometer() {
        BluetoothGattService service = btGatt.getService(ACCELEROMETER.getService());
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(ACCELEROMETER.getConfig());
        characteristic.setValue((int) ACCELEROMETER.getEnableSensorCode(), BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        btGatt.writeCharacteristic(characteristic);
    }

    public boolean readAccelerometer(){
        if(connectionState != BluetoothProfile.STATE_CONNECTED)
            return false;
        BluetoothGattService service = btGatt.getService(ACCELEROMETER.getService());
        Log.d("readAccelerometer", "service: " + service);
        if(service == null)
            return false;
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(ACCELEROMETER.getData());
        return characteristic!=null && btGatt.readCharacteristic(characteristic);
    }

    @Override
    public IBinder onBind(Intent intent) {
        final BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter btAdapter = btManager.getAdapter();
        if(btAdapter == null || !btAdapter.isEnabled())
            throw new UnsupportedOperationException("Bluetooth adapter is not enabled!");    //the Activity should enable adapter first!
        BluetoothDevice device;
        if(intent.getStringExtra("mac") != null)
            device = btAdapter.getRemoteDevice(intent.getStringExtra("mac"));
        else if(intent.getByteArrayExtra("mac") != null)
            device = btAdapter.getRemoteDevice(intent.getByteArrayExtra("mac"));
        else throw new UnsupportedOperationException("No MAC address specified!");
        btGatt = device.connectGatt(this,false,btGattCb);
        connectionState = btManager.getConnectionState(device,BluetoothProfile.GATT);
        return mBinder;
    }
}
