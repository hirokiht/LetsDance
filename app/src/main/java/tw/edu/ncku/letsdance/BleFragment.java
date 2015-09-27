package tw.edu.ncku.letsdance;

import android.annotation.TargetApi;
import android.app.Activity;
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
import android.os.Build;
import android.os.Bundle;
import android.app.Fragment;
import android.support.v4.util.SimpleArrayMap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link tw.edu.ncku.letsdance.BleFragment.BleEventListener} interface
 * to handle interaction events.
 */
public class BleFragment extends Fragment {
    private BleEventListener mListener;
    private static BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
    private static SimpleArrayMap<BluetoothDevice,BluetoothGatt> btGatts = new SimpleArrayMap<>();
    private static SimpleArrayMap<BluetoothDevice,Boolean> busy = new SimpleArrayMap<>();
    private static SimpleArrayMap<BluetoothDevice,Queue<BtRequest>> requests = new SimpleArrayMap<>();

    public final BluetoothGattCallback btGattCb = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if(newState == BluetoothProfile.STATE_CONNECTED){
                gatt.discoverServices();
            }else if(newState == BluetoothProfile.STATE_DISCONNECTED){
                busy.remove(gatt.getDevice());
                requests.remove(gatt.getDevice());
            }
            super.onConnectionStateChange(gatt, status, newState);
            mListener.onBleEvent(new Intent().putExtra("btDevice", gatt.getDevice())
                    .putExtra("type", "connection").putExtra("connection", status));
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            mListener.onBleEvent(new Intent().putExtra("btDevice", gatt.getDevice())
                    .putExtra("type", "ready").putExtra("ready", status == BluetoothGatt.GATT_SUCCESS));
            super.onServicesDiscovered(gatt, status);
            pollRequests(gatt.getDevice());
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            byte[] val = characteristic.getValue();
            Sensor sensor;
            try{
                sensor = Sensor.getFromDataUuid(characteristic.getUuid());
            }catch(RuntimeException re){
                sensor = null;
            }
            mListener.onBleEvent(new Intent().putExtra("btDevice", gatt.getDevice())
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
            mListener.onBleEvent(new Intent().putExtra("btDevice", gatt.getDevice())
                    .putExtra("type", "write").putExtra("write", sensor)
                    .putExtra("status", status == BluetoothGatt.GATT_SUCCESS));
            super.onCharacteristicWrite(gatt, characteristic, status);
            pollRequests(gatt.getDevice());
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] val = characteristic.getValue();
            Sensor sensor = Sensor.getFromDataUuid(characteristic.getUuid());
            mListener.onBleEvent(new Intent().putExtra("btDevice", gatt.getDevice())
                    .putExtra("type", "notify").putExtra("notify", sensor).putExtra("data", val));
            super.onCharacteristicChanged(gatt, characteristic);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            byte[] val = descriptor.getValue();
            Sensor sensor = Sensor.getFromDataUuid(descriptor.getCharacteristic().getUuid());
            mListener.onBleEvent(new Intent().putExtra("btDevice", gatt.getDevice())
                    .putExtra("type", "readDesc").putExtra("readDesc", sensor).putExtra("data", val));
            super.onDescriptorRead(gatt, descriptor, status);
            pollRequests(gatt.getDevice());
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Sensor sensor = Sensor.getFromDataUuid(descriptor.getCharacteristic().getUuid());
            mListener.onBleEvent(new Intent().putExtra("btDevice", gatt.getDevice())
                    .putExtra("type", "writeDesc").putExtra("writeDesc", sensor)
                    .putExtra("status", status == BluetoothGatt.GATT_SUCCESS));
            super.onDescriptorWrite(gatt, descriptor, status);
            pollRequests(gatt.getDevice());
        }
    };

    private static abstract class BtRequest{
        public String action;
        public BtRequest(String action){
            this.action = action;
        }
        abstract boolean execute();
    }

    public BleFragment() {
        // Required empty public constructor
    }

    private static void pollRequests(BluetoothDevice device){
        if (requests.get(device) == null)
            return;
        if(requests.get(device).isEmpty())
            busy.put(device, false);
        else requests.get(device).poll().execute();
    }

    public void discoverDevices(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btAdapter.getBluetoothLeScanner().startScan(new ScanCallback() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    mListener.onBleEvent(new Intent().putExtra("type", "device")
                            .putExtra("device", result.getDevice()));
                    super.onScanResult(callbackType, result);
                }
            });
        }else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2){
            //noinspection deprecation
            btAdapter.startLeScan(new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
                    mListener.onBleEvent(new Intent().putExtra("type","device")
                            .putExtra("device",device));
                }
            });
        }
    }

    public BluetoothDevice connectGattDevice(Context ctx, String mac){
        final BluetoothDevice device = btAdapter.getRemoteDevice(mac);
        connectGattDevice(ctx, device);
        return device;
    }

    public void connectGattDevice(Context ctx, BluetoothDevice device){
        if(device.getBondState() != BluetoothDevice.BOND_NONE)
            return;
        BluetoothGatt gatt = device.connectGatt(ctx, false, btGattCb);
        btGatts.put(device,gatt);
        busy.put(device, true);
        requests.put(device, new LinkedList<BtRequest>());
    }

    public static boolean enableSensor(final String device, final Sensor sensor) {
        return enableSensor(btAdapter.getRemoteDevice(device),sensor);
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
    public static boolean readSensor(final String device, final Sensor sensor){
        return readSensor(btAdapter.getRemoteDevice(device),sensor);
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

    public static boolean notifyDevice(final String device){
        return notifyDevice(btAdapter.getRemoteDevice(device));
    }

    public static boolean notifyDevice(final BluetoothDevice device){
        final BluetoothGatt btGatt = btGatts.get(device);
        if(btGatt == null || !busy.containsKey(device))
            return false;
        BtRequest request = new BtRequest("notify " + device.getAddress()) {
            @Override
            boolean execute() {
                busy.put(device,true);
                BluetoothGattService service = btGatt.getService(SensorTagGatt.UUID_NOTIFY_SERV);
                if(service == null)
                    return false;
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(SensorTagGatt.UUID_NOTIFY_DATA);
                return characteristic!=null && btGatt.readCharacteristic(characteristic);
            }
        };
        return busy.get(device)? requests.get(device).offer(request) : request.execute();
    }

    public static boolean setSensorNotification(final String device, final Sensor sensor, final boolean enable){
        return setSensorNotification(btAdapter.getRemoteDevice(device),sensor,enable);
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

    public static boolean setSensorNotificationPeriod(final String device, final Sensor sensor, final int period){
        return setSensorNotificationPeriod(btAdapter.getRemoteDevice(device),sensor,period);
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        TextView textView = new TextView(getActivity());
        textView.setText(R.string.hello_blank_fragment);
        return textView;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (BleEventListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement BleEventListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface BleEventListener {
        void onBleEvent(Intent intent);
    }

}
