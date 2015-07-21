package tw.edu.ncku.letsdance;

import android.os.Bundle;
import android.app.Fragment;
import android.os.Environment;
import android.support.v4.util.SimpleArrayMap;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link SensorDataLoggerFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class SensorDataLoggerFragment extends Fragment {
    private String prefix;
    private SimpleArrayMap<String,ArrayList<float[]>> data = new SimpleArrayMap<>();

    public static SensorDataLoggerFragment newInstance() {
        return new SensorDataLoggerFragment();
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param prefix Prefix for filename to be saved.
     * @return A new instance of fragment SensorDataLoggerFragment.
     */
    public static SensorDataLoggerFragment newInstance(String prefix) {
        SensorDataLoggerFragment fragment = new SensorDataLoggerFragment();
        Bundle args = new Bundle();
        args.putString("prefix", prefix);
        fragment.setArguments(args);
        return fragment;
    }

    public SensorDataLoggerFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefix = getArguments() != null? getArguments().getString("prefix")+"-" : "";
    }

    public void logData(String key, float[] value){
        if(!data.containsKey(key))
            data.put(key,new ArrayList<float[]>());
        data.get(key).add(value);
    }

    public void writeToExtStorage() throws IOException{
        if(!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
            throw new IOException("ext storage not mounted! State: "+Environment.getExternalStorageState());
        final File dir = getActivity().getExternalFilesDir(null);
        for(int i = 0 ; i < data.size() ; i++){
            File fp = new File(dir,prefix+data.keyAt(i)+".csv");
            BufferedWriter writer = new BufferedWriter(new FileWriter(fp));
            for(float[] d : data.valueAt(i)) {
                writer.write(Arrays.toString(d));
                writer.newLine();
            }
            writer.close();
        }
    }
}
