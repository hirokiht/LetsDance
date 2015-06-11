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
import android.support.v4.util.SimpleArrayMap;

import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

public class BleService extends Service {
    private static BluetoothManager btManager = null;
    private static BluetoothAdapter btAdapter = null;
    private static LocalBroadcastManager bcastManager = null;
    private static SimpleArrayMap<BluetoothDevice,BluetoothGatt> btGatt = new SimpleArrayMap<>();
    private static SimpleArrayMap<BluetoothDevice,Boolean> busy = new SimpleArrayMap<>();
    private static SimpleArrayMap<BluetoothDevice,Queue<BtRequest>> requests = new SimpleArrayMap<>();

    public static final BluetoothGattCallback btGattCb = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if(newState == BluetoothProfile.STATE_CONNECTED){
                gatt.discoverServices();
            }else if(newState == BluetoothProfile.STATE_DISCONNECTED){
                busy.remove(gatt.getDevice());
                requests.remove(gatt.getDevice());
            }
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
            Sensor sensor = Sensor.getFromDataUuid(descriptor.getCharacteristic().getUuid());
            bcastManager.sendBroadcast(new Intent("btCb").putExtra("btDevice", gatt.getDevice())
                    .putExtra("type", "readDesc").putExtra("readDesc", sensor).putExtra("data", val));
            super.onDescriptorRead(gatt, descriptor, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Sensor sensor = Sensor.getFromDataUuid(descriptor.getCharacteristic().getUuid());
            bcastManager.sendBroadcast(new Intent("btCb").putExtra("btDevice", gatt.getDevice())
                    .putExtra("type", "writeDesc").putExtra("writeDesc", sensor)
                    .putExtra("status", status == BluetoothGatt.GATT_SUCCESS));
            super.onDescriptorWrite(gatt, descriptor, status);
        }
    };

    public BleService() {
        bcastManager = LocalBroadcastManager.getInstance(this);
        bcastManager.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                BluetoothDevice device = intent.getParcelableExtra("btDevice");
                String type = intent.getStringExtra("type");
                if(type.startsWith("read") || type.startsWith("write")) //ready starts with "read"
                    if(requests.get(device).isEmpty())
                        busy.put(device,false);
                    else {
                        BtRequest req = requests.get(device).poll();
                        req.execute();
                    }
            }
        }, new IntentFilter("btCb"));
    }

    private static abstract class BtRequest{
        public String action;
        public BtRequest(String action){
            this.action = action;
        }
        abstract boolean execute();
    }

    public static BluetoothGatt connectGattDevice(Context ctx, String mac){
        return connectGattDevice(ctx,btAdapter.getRemoteDevice(mac));
    }

    public static BluetoothGatt connectGattDevice(Context ctx, BluetoothDevice device){
        BluetoothGatt gatt = device.connectGatt(ctx,false,btGattCb);
        btGatt.put(device,gatt);
        busy.put(device,true);
        requests.put(device,new LinkedList<BtRequest>());
        return gatt;
    }

    public static boolean enableSensor(final BluetoothGatt btGatt, final Sensor sensor) {
        BtRequest req = new BtRequest("enable" + sensor.name()) {
            @Override
            boolean execute() {
                BluetoothGattService service = btGatt.getService(sensor.getService());
                if(service == null)
                    return false;
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(sensor.getConfig());
                if(characteristic == null)
                    return false;
                characteristic.setValue((int) sensor.getEnableSensorCode(), BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                return btGatt.writeCharacteristic(characteristic);
            }
        };
        return busy.get(btGatt.getDevice())? requests.get(btGatt.getDevice()).offer(req) : req.execute();
    }

    public static boolean readSensor(final BluetoothGatt btGatt, final Sensor sensor){
        BtRequest request = new BtRequest("read" + sensor.name()) {
            @Override
            boolean execute() {
                BluetoothGattService service = btGatt.getService(sensor.getService());
                if(service == null)
                    return false;
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(sensor.getData());
                return characteristic!=null && btGatt.readCharacteristic(characteristic);
            }
        };
        return busy.get(btGatt.getDevice())? requests.get(btGatt.getDevice()).offer(request) : request.execute();
    }

    public static boolean setSensorNotification(final BluetoothGatt btGatt, final Sensor sensor, final boolean enable){
        BtRequest request = new BtRequest("setNotification" + sensor.name()) {
            @Override
            boolean execute() {
                BluetoothGattService service = btGatt.getService(sensor.getService());
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(sensor.getData());
                if(characteristic == null || !btGatt.setCharacteristicNotification(characteristic,enable))
                    return false;
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                return btGatt.writeDescriptor(descriptor);
            }
        };
        return busy.get(btGatt.getDevice())? requests.get(btGatt.getDevice()).offer(request) : request.execute();
    }

    public static boolean setSensorNotificationPeriod(final BluetoothGatt btGatt, final Sensor sensor, final int period){
        BtRequest request = new BtRequest("setNotificationPeriod"+sensor.name()) {
            @Override
            boolean execute() {
                BluetoothGattService service = btGatt.getService(sensor.getService());
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(sensor.getPeriod());
                if(characteristic == null)
                    return false;
                characteristic.setValue(period / 10, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                return btGatt.writeCharacteristic(characteristic);
            }
        };
        return busy.get(btGatt.getDevice())? requests.get(btGatt.getDevice()).offer(request) : request.execute();
    }

    @Override
    public void onCreate(){
        if(btManager == null)
            btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        btAdapter = btManager.getAdapter();
        if(btAdapter == null || !btAdapter.isEnabled())
            throw new UnsupportedOperationException("Bluetooth adapter is not enabled!");    //the Activity should enable adapter first!
        return new Binder(){
            BleService getService(){
                return BleService.this;
            }
        };
    }
}
