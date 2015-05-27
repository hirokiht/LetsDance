package tw.edu.ncku.letsdance;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;

import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

import static tw.edu.ncku.letsdance.Sensor.*;

public class BleService extends Service {
    private BluetoothManager btManager = null;
    private LocalBroadcastManager bcastManager = null;

    private final BluetoothGattCallback btGattCb = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED)
                gatt.discoverServices();
            super.onConnectionStateChange(gatt, status, newState);
            bcastManager.sendBroadcast(new Intent("btCb").putExtra("btDevice",gatt.getDevice())
                    .putExtra("type","connection").putExtra("connection", status));
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            bcastManager.sendBroadcast(new Intent("btCb").putExtra("btDevice", gatt.getDevice())
                    .putExtra("type", "ready").putExtra("ready", status == BluetoothGatt.GATT_SUCCESS));
            super.onServicesDiscovered(gatt, status);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            byte[] val = characteristic.getValue();
            Sensor sensor = Sensor.getFromDataUuid(characteristic.getUuid());
            bcastManager.sendBroadcast(new Intent("btCb").putExtra("btDevice", gatt.getDevice())
                    .putExtra("type", "read").putExtra("read", sensor).putExtra("data", val));
            super.onCharacteristicRead(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status){
            try{
                Sensor sensor = Sensor.getFromDataUuid(characteristic.getUuid());
                bcastManager.sendBroadcast(new Intent("btCb").putExtra("btDevice", gatt.getDevice())
                        .putExtra("type", "write").putExtra("write", sensor)
                        .putExtra("status", status == BluetoothGatt.GATT_SUCCESS));
            }catch(RuntimeException re){
                bcastManager.sendBroadcast(new Intent("btCb").putExtra("btDevice", gatt.getDevice())
                        .putExtra("type", "write").putExtra("write", (Sensor) null)
                        .putExtra("status", status == BluetoothGatt.GATT_SUCCESS));
            }finally {
                super.onCharacteristicRead(gatt, characteristic, status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] val = characteristic.getValue();
            Sensor sensor = Sensor.getFromDataUuid(characteristic.getUuid());
            bcastManager.sendBroadcast(new Intent("btCb").putExtra("btDevice", gatt.getDevice())
                    .putExtra("type", "notify").putExtra("notify", sensor).putExtra("data", val));
            super.onCharacteristicChanged(gatt, characteristic);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            byte[] val = descriptor.getValue();
            Sensor sensor = Sensor.getFromDataUuid(descriptor.getUuid());
            bcastManager.sendBroadcast(new Intent("btCb").putExtra("btDevice", gatt.getDevice())
                    .putExtra("type", "readDesc").putExtra("readDesc", sensor).putExtra("data", val));
            super.onDescriptorRead(gatt, descriptor, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Sensor sensor = Sensor.getFromDataUuid(descriptor.getUuid());
            bcastManager.sendBroadcast(new Intent("btCb").putExtra("btDevice", gatt.getDevice())
                    .putExtra("type", "writeDesc").putExtra("writeDesc", sensor)
                    .putExtra("status", status == BluetoothGatt.GATT_SUCCESS));
            super.onDescriptorWrite(gatt, descriptor, status);
        }
    };

    public BleService() {
    }

    private abstract class BtRequest{
        public String action;
        public BtRequest(String action){
            this.action = action;
        }
        abstract void execute();
    }

    public class LocalBinder extends Binder {
        private BluetoothGatt btGatt = null;
        private boolean busy = true;    //wait for "ready"
        private Queue<BtRequest> requests = new LinkedList<>();

        public LocalBinder(BluetoothGatt bg){
            super();
            btGatt = bg;
            bcastManager.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if(btGatt.getDevice().equals(intent.getParcelableExtra("device")))
                        return;
                    String type = intent.getStringExtra("type");
                    if(type.startsWith("read") || type.startsWith("write")) //ready starts with "read"
                        if(requests.isEmpty())
                            busy = false;
                        else requests.poll().execute();
                }
            }, new IntentFilter("btCb"));
        }

        public BluetoothDevice getDevice(){
            return btGatt.getDevice();
        }

        public boolean enableSensor(final Sensor sensor){
            if(!busy)
                return busy = BleService.this.enableSensor(btGatt, sensor);
            else return requests.offer(new BtRequest("enable"+sensor.name()) {
                @Override
                public void execute() {
                    BleService.this.enableSensor(btGatt, sensor);
                }
            });
        }

        public boolean readSensor(final Sensor sensor){
            if(!busy)
                return busy = BleService.this.readSensor(btGatt, sensor);
            else return requests.offer(new BtRequest("read"+sensor.name()) {
                @Override
                public void execute() {
                    BleService.this.readSensor(btGatt, sensor);
                }
            });
        }

        public boolean setSensorNotification(final Sensor sensor, final boolean notify){
            if(!busy)
                return busy = BleService.this.setSensorNotification(btGatt, sensor, notify);
            else return requests.offer(new BtRequest("set"+sensor.name()+"Notification") {
                @Override
                public void execute() {
                    BleService.this.setSensorNotification(btGatt, sensor, notify);
                }
            });
        }

        public boolean enableAccelerometer(){
            return enableSensor(ACCELEROMETER);
        }

        public boolean readAccelerometer(){
            return readSensor(ACCELEROMETER);
        }

        public boolean setAccelerometerNotification(final boolean notify){
            return setSensorNotification(ACCELEROMETER,notify);
        }

        public boolean enableMagnetometer(){
            return enableSensor(MAGNETOMETER);
        }

        public boolean readMagnetometer(){
            return readSensor(MAGNETOMETER);
        }

        public boolean setMagnetonmeterNotification(final boolean notify){
            return setSensorNotification(MAGNETOMETER,notify);
        }
    }

    public boolean enableSensor(BluetoothGatt btGatt, Sensor sensor) {
        BluetoothGattService service = btGatt.getService(sensor.getService());
        if(service == null)
            return false;
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(sensor.getConfig());
        if(characteristic == null)
            return false;
        characteristic.setValue((int) sensor.getEnableSensorCode(), BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        return btGatt.writeCharacteristic(characteristic);
    }

    public boolean readSensor(BluetoothGatt btGatt, Sensor sensor){
        BluetoothGattService service = btGatt.getService(sensor.getService());
        if(service == null)
            return false;
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(sensor.getData());
        return characteristic!=null && btGatt.readCharacteristic(characteristic);
    }

    public boolean setSensorNotification(BluetoothGatt btGatt, Sensor sensor, boolean enable){
        BluetoothGattService service = btGatt.getService(sensor.getService());
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(sensor.getData());
        if(characteristic!=null && btGatt.setCharacteristicNotification(characteristic,enable)){
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            return btGatt.writeDescriptor(descriptor);
        }else return false;
    }

    @Override
    public void onCreate(){
        btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bcastManager = LocalBroadcastManager.getInstance(this);
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
