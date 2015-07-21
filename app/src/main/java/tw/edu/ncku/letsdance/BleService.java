package tw.edu.ncku.letsdance;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.util.SimpleArrayMap;

import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

public class BleService extends Service {
    private static BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
    private static LocalBroadcastManager bcastManager = null;
    private static SimpleArrayMap<BluetoothDevice,BluetoothGatt> btGatts = new SimpleArrayMap<>();
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
            pollRequests(gatt.getDevice());
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            byte[] val = characteristic.getValue();
            Sensor sensor = Sensor.getFromDataUuid(characteristic.getUuid());
            bcastManager.sendBroadcast(new Intent("btCb").putExtra("btDevice", gatt.getDevice())
                    .putExtra("type", "read").putExtra("read", sensor).putExtra("data", val));
            super.onCharacteristicRead(gatt, characteristic, status);
            pollRequests(gatt.getDevice());
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status){
            Sensor sensor;
            try{
                sensor = Sensor.getFromDataUuid(characteristic.getUuid());
            }catch(RuntimeException re){
                sensor = null;
            }
            bcastManager.sendBroadcast(new Intent("btCb").putExtra("btDevice", gatt.getDevice())
                    .putExtra("type", "write").putExtra("write", sensor)
                    .putExtra("status", status == BluetoothGatt.GATT_SUCCESS));
            super.onCharacteristicWrite(gatt, characteristic, status);
            pollRequests(gatt.getDevice());
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
            pollRequests(gatt.getDevice());
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Sensor sensor = Sensor.getFromDataUuid(descriptor.getCharacteristic().getUuid());
            bcastManager.sendBroadcast(new Intent("btCb").putExtra("btDevice", gatt.getDevice())
                    .putExtra("type", "writeDesc").putExtra("writeDesc", sensor)
                    .putExtra("status", status == BluetoothGatt.GATT_SUCCESS));
            super.onDescriptorWrite(gatt, descriptor, status);
            pollRequests(gatt.getDevice());
        }
    };

    public BleService() {
        bcastManager = LocalBroadcastManager.getInstance(this);
    }

    private static abstract class BtRequest{
        public String action;
        public BtRequest(String action){
            this.action = action;
        }
        abstract boolean execute();
    }

    private static void pollRequests(BluetoothDevice device){
        if (requests.get(device) == null)
            return;
        if(requests.get(device).isEmpty())
            busy.put(device, false);
        else requests.get(device).poll().execute();
    }

    public static void discoverDevices(Context ctx){
        final LocalBroadcastManager bcastManager = LocalBroadcastManager.getInstance(ctx);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner().startScan(new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    bcastManager.sendBroadcast(new Intent("btCb").putExtra("type","device")
                    .putExtra("device",result.getDevice()));
                    super.onScanResult(callbackType, result);
                }
            });
        }else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2){
            //noinspection deprecation
            btAdapter.startLeScan(new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
                    bcastManager.sendBroadcast(new Intent("btCb").putExtra("type","device")
                            .putExtra("device",device));
                }
            });
        }
    }

    public static BluetoothDevice connectGattDevice(Context ctx, String mac){
        final BluetoothDevice device = btAdapter.getRemoteDevice(mac);
        connectGattDevice(ctx,device);
        return device;
    }

    public static void connectGattDevice(Context ctx, BluetoothDevice device){
        if(device.getBondState() != BluetoothDevice.BOND_NONE)
            return;
        BluetoothGatt gatt = device.connectGatt(ctx,false,btGattCb);
        btGatts.put(device,gatt);
        busy.put(device, true);
        requests.put(device, new LinkedList<BtRequest>());
    }

    public static boolean enableSensor(final BluetoothDevice device, final Sensor sensor) {
        final BluetoothGatt btGatt = btGatts.get(device);
        if(btGatt == null || !busy.containsKey(device))
            return false;
        BtRequest req = new BtRequest("enable" + sensor.name()) {
            @Override
            boolean execute() {
                busy.put(device,true);
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
        return busy.get(device)? requests.get(device).offer(req) : req.execute();
    }

    public static boolean readSensor(final BluetoothDevice device, final Sensor sensor){
        final BluetoothGatt btGatt = btGatts.get(device);
        if(btGatt == null || !busy.containsKey(device))
            return false;
        BtRequest request = new BtRequest("read" + sensor.name()) {
            @Override
            boolean execute() {
                busy.put(device,true);
                BluetoothGattService service = btGatt.getService(sensor.getService());
                if(service == null)
                    return false;
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(sensor.getData());
                return characteristic!=null && btGatt.readCharacteristic(characteristic);
            }
        };
        return busy.get(device)? requests.get(device).offer(request) : request.execute();
    }

    public static boolean setSensorNotification(final BluetoothDevice device, final Sensor sensor, final boolean enable){
        final BluetoothGatt btGatt = btGatts.get(device);
        if(btGatt == null || !busy.containsKey(device))
            return false;
        BtRequest request = new BtRequest("setNotification" + sensor.name()) {
            @Override
            boolean execute() {
                busy.put(device,true);
                BluetoothGattService service = btGatt.getService(sensor.getService());
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(sensor.getData());
                if(characteristic == null || !btGatt.setCharacteristicNotification(characteristic,enable))
                    return false;
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                return btGatt.writeDescriptor(descriptor);
            }
        };
        return busy.get(device)? requests.get(device).offer(request) : request.execute();
    }

    public static boolean setSensorNotificationPeriod(final BluetoothDevice device, final Sensor sensor, final int period){
        final BluetoothGatt btGatt = btGatts.get(device);
        if(btGatt == null || !busy.containsKey(device))
            return false;
        BtRequest request = new BtRequest("setNotificationPeriod"+sensor.name()) {
            @Override
            boolean execute() {
                busy.put(device,true);
                BluetoothGattService service = btGatt.getService(sensor.getService());
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(sensor.getPeriod());
                if(characteristic == null)
                    return false;
                characteristic.setValue(period / 10, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                return btGatt.writeCharacteristic(characteristic);
            }
        };
        return busy.get(device)? requests.get(device).offer(request) : request.execute();
    }

    @Override
    public void onCreate(){
        super.onCreate();
    }

    @Override
    public void onDestroy(){
        for(int i = 0 ; i < btGatts.size() ; i++)
            btGatts.valueAt(i).disconnect();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        if(btAdapter == null || !btAdapter.isEnabled())
            throw new UnsupportedOperationException("Bluetooth adapter is not enabled!");    //the Activity should enable adapter first!
        return new Binder();
    }
}
