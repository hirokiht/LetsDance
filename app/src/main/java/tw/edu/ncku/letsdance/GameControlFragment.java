package tw.edu.ncku.letsdance;


import android.app.Activity;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static tw.edu.ncku.letsdance.SensorFragment.*;


/**
 * A simple {@link Fragment} subclass.
 */
public class GameControlFragment extends Fragment {
    private int score = 0;
    private boolean started = false;
    private MediaPlayer musicPlayer;
    private ToneGenerator toneGenerator;
    private static final int[]  timings = {16200,19100,20300,23000,24250,26150,28000,30350,33350,35200,
            37150,39100,41100,43000,44350,48300,52150,56050,59400,63200,67100,71000,72300,73300,74300,75300};
    private static final byte[] gestures = {OPENING,OPENING,OPENING,EXPAND,EXPAND,EXPAND,EXPAND,OPENING,OPENING,OPENING,
        EXPAND,EXPAND,EXPAND,EXPAND,DOUBLE_SPIN,DOUBLE_SPIN,SINGLE_SPIN,SINGLE_SPIN,DOUBLE_SPIN,DOUBLE_SPIN,SINGLE_SPIN,SINGLE_SPIN,BONUS,BONUS,BONUS,BONUS};
    private static final byte[] sources = {0xF,0xF,0xF,0x5,0xA,0x5,0xA,0xF,0xF,0xF,
            0x5,0xA,0x5,0xA,0xF,0xF,0x1,0x8,0xF,0xF,0x1,0x8,0xF,0xF,0xF,0xF};
    private byte[] hits = new byte[22];
    private NotifyListener notifyCallback;

    public interface NotifyListener{
        void onNotifyDevices(byte devices);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        musicPlayer = MediaPlayer.create(getActivity(),R.raw.waltz);
        toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION,ToneGenerator.MAX_VOLUME);
        if(started)
            start();
    }

    @Override
    public void onAttach(Activity activity){
        super.onAttach(activity);
        try {
            notifyCallback = (NotifyListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement NotifyListener");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return super.onCreateView(inflater,container,savedInstanceState);
    }

    public int getScore(){
        return score;
    }

    public boolean processGesture(byte source, byte gesture){
        if(source < 0 || source > 0xF)
            return false;
        int time = musicPlayer.getCurrentPosition();
        for(int i = 0 ; i < timings.length ; i++){
            if(time < timings[i])   //sorted timing must be used for this method to work
                continue;
            if(time > timings[i]+1000 || (source&sources[i]) == 0 || (gesture&gestures[i]) == 0)
                return false;
            hits[i]++;
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP);
            return true;
        }
        return false;
    }

    public void start(){
        started = true;
        if(musicPlayer == null)
            return;
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(2);
        musicPlayer.start();
        for(int i = 0 ; i < timings.length ; i++) {
            final byte devices = sources[i];
            executor.schedule(new Runnable() {
                @Override
                public void run() {
                    notifyCallback.onNotifyDevices(devices);
                }
            }, timings[i], TimeUnit.MILLISECONDS);
            if(i >= 22)
                executor.schedule(new Runnable() {
                    @Override
                    public void run() {
                        notifyCallback.onNotifyDevices(devices);
                    }
                }, timings[i]+500, TimeUnit.MILLISECONDS);
        }
    }

    public void stop(){
        started = false;
    }
}
