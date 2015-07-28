package tw.edu.ncku.letsdance;

import android.app.Activity;
import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.Arrays;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link SensorFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class SensorFragment extends Fragment {
    private String mac = "";
    private short interval = 500;
    private float[] acc = null, gyro = null, lpfAcc = null, deg = {0.0f,0.0f,0.0f};
    private final static float aAcc = 0.90f, alpha = 0.96f;              //alpha for lpf and complimentary filter
    private float[][] degRingBuffer = null;
    private int[] minIndex = {-1, -1, -1}, maxIndex = {-1, -1, -1};
    private int curPos = 0, gesturePos = 0;
    private byte[] gestureRingBuffer = null;
    public static final byte INVALID = 0, DOUBLE_SPIN = 1, SINGLE_SPIN = 2, OPENING = 4, EXPAND = 8, BONUS = 0x10;
    private GestureListener gestureCallback;

    private LineChart sensorChart;
    private LineData sensorData;

    // Container Activity must implement this interface
    public interface GestureListener {
        void onGestureDetected(byte gesture, String mac);
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment SensorFragment.
     */
    public static SensorFragment newInstance(String mac, short interval) {
        SensorFragment fragment = new SensorFragment();
        Bundle args = new Bundle();
        args.putString("mac", mac);
        args.putShort("interval",interval);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(getArguments() == null)
            return;
        setRetainInstance(true);    // retain this fragment
        mac = getArguments().getString("mac");
        interval = getArguments().getShort("interval");
        degRingBuffer = new float[3000/interval][]; //three second detection frame
        gestureRingBuffer = new byte[3000/interval];
    }

    @Override
    public void onAttach(Activity activity){
        super.onAttach(activity);
        try {
            gestureCallback = (GestureListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement GestureListener");
        }
    }

    public void setInterval(short interval){
        this.interval = interval;
    }

    public void addSensorData(Sensor s, float[] p){
        if(s == Sensor.ACCELEROMETER) {
            lpfAcc = lpfAcc == null? p.clone() : new float[]{lpfAcc[0]+aAcc*(p[0]-acc[0]),
                    lpfAcc[1]+aAcc*(p[1]-acc[1]), lpfAcc[2]+aAcc*(p[2]-acc[2])};
            acc = p.clone();
        }else if(s == Sensor.GYROSCOPE)
            gyro = p.clone();
        else return;
        if(gyro == null || acc == null)
            return;
        final float t = interval/1000.0f; //convert from ms to s
        final float[] accDeg = {(float)Math.atan2(-lpfAcc[1],lpfAcc[2])*180.0f/(float)Math.PI,
                (float)Math.atan2(-lpfAcc[0],lpfAcc[2])*180.0f/(float)Math.PI,
                (float)Math.atan2(lpfAcc[1],lpfAcc[0])*180.0f/(float)Math.PI};
        final float[] gyroDeg = {deg[0]+gyro[0]*t,deg[1]+gyro[1]*t};
        deg = new float[]{alpha*gyroDeg[0]+(1.0f-alpha)*accDeg[0],
                alpha*gyroDeg[1]+(1.0f-alpha)*accDeg[1], accDeg[2]};
        degRingBuffer[curPos] = deg.clone();
        for(int i = 0 ; i < deg.length ; i++) {
            if (curPos == minIndex[i]) {
                for(int j = 0 ; j < degRingBuffer.length ; j++)
                    if(j != curPos && degRingBuffer[minIndex[i]][i] > degRingBuffer[j][i])
                        minIndex[i] = j;
            } else if (minIndex[i] < 0 || degRingBuffer[minIndex[i]][i] > deg[i])
                minIndex[i] = curPos;
            if (curPos == maxIndex[i]){
                for(int j = 0 ; j < degRingBuffer.length ; j++)
                    if(j != curPos && degRingBuffer[maxIndex[i]][i] < degRingBuffer[j][i])
                        maxIndex[i] = j;
            }else if (maxIndex[i] < 0 || degRingBuffer[maxIndex[i]][i] < deg[i])
                maxIndex[i] = curPos;
        }
        if(curPos == degRingBuffer.length-1)
            curPos = 0;
        else curPos++;
        addEntry(deg);
        gestureRingBuffer[gesturePos] = gestureDetection();
        boolean newGesture = gestureRingBuffer[gesturePos] != INVALID;
        for(int i = 0 ; i < gestureRingBuffer.length && newGesture ; i++)
            if(i != gesturePos && (gestureRingBuffer[i]&gestureRingBuffer[gesturePos]) != INVALID)
                newGesture = false;
        if(newGesture)
            gestureCallback.onGestureDetected(gestureRingBuffer[gesturePos],mac);
        if(gesturePos == gestureRingBuffer.length-1)
            gesturePos = 0;
        else gesturePos++;
    }

    public byte gestureDetection(){
        float delta[] = {degRingBuffer[maxIndex[0]][0]-degRingBuffer[minIndex[0]][0],
                degRingBuffer[maxIndex[1]][1]-degRingBuffer[minIndex[1]][1],
                degRingBuffer[maxIndex[2]][2]-degRingBuffer[minIndex[2]][2] };
        if(delta[1] > 350)
            return DOUBLE_SPIN;
        if(delta[1] > 290)
            return SINGLE_SPIN;
        if(delta[0] > 100)
            return EXPAND;
        if(delta[1] > 90)
            return OPENING;
        if(delta[2] > 150)
            return BONUS;
        return INVALID;
    }

    public void addEntry(float[] entries){
        if(entries == null || entries.length > 3 || sensorData == null || sensorChart == null)
            return;
        sensorData.addXValue(String.valueOf(sensorData.getXValCount()));
        for(int i = 0 ; i < entries.length ; i++)
            sensorData.addEntry(new Entry(entries[i], sensorData.getXValCount()),i);
        sensorChart.notifyDataSetChanged();
        sensorChart.setVisibleXRange(25);
        if(sensorData.getXValCount() > 25) {
            sensorChart.moveViewToX(sensorData.getXValCount() - 25);
        }
        sensorChart.invalidate();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        if(container == null)
            return null;
        View view = inflater.inflate(R.layout.fragment_sensor, container, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sensorChart = (LineChart) view.findViewById(R.id.sensorChart);
        sensorData = new LineData(new ArrayList<String>(), Arrays.asList(new LineDataSet[]{
                new LineDataSet(null, "degX"), new LineDataSet(null, "degY"), new LineDataSet(null, "degZ")}));
        for(int i = 0 ; i < sensorData.getDataSetCount() ; i++) {
            sensorData.getDataSetByIndex(i).setColor( 0xff << (8 * i) |0xff000000);
            sensorData.getDataSetByIndex(i).setCircleColor(0xff<<(8*i)|0xff000000);
        }
        sensorData.setDrawValues(false);
        sensorChart.setData(sensorData);
        sensorChart.setDescription(mac+" Degree");
        sensorChart.getAxisLeft().setStartAtZero(false);
        sensorChart.getAxisLeft().setAxisMinValue(-450);
        sensorChart.getAxisLeft().setAxisMaxValue(450);
        view.setLayoutParams(params);
        return view;
    }
}
