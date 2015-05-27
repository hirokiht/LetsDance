package tw.edu.ncku.letsdance;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;


public class ProgressDialogFragment extends DialogFragment{

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     * @return A new instance of fragment ProgressDialogFragment.
     */
    public static ProgressDialogFragment newInstance(int title, int desc) {
        Bundle args = new Bundle();
        args.putInt("title", title);
        args.putInt("description", desc);
        ProgressDialogFragment pdf = new ProgressDialogFragment();
        pdf.setArguments(args);
        return pdf;
    }

    @Override
    public void onCancel(DialogInterface dialog){
        dismiss();
        getActivity().finish();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        int title = getArguments().getInt("title"), desc = getArguments().getInt("description");
        return ProgressDialog.show(getActivity(), getString(title), getString(desc), true, true);
    }
}
