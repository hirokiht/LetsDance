package tw.edu.ncku.letsdance;

import android.support.v7.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

public class AlertDialogFragment extends DialogFragment {
    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment AlertDialogFragment.
     */
    public static AlertDialogFragment newInstance(int title, int message) {
        AlertDialogFragment fragment = new AlertDialogFragment();
        Bundle args = new Bundle();
        args.putInt("title", title);
        args.putInt("msg", message);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCancel(DialogInterface dialog){
        dismiss();
        getActivity().finish();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        String title = getString(getArguments().getInt("title")), message = getString(getArguments().getInt("msg"));
        return new AlertDialog.Builder(getActivity()).setTitle(title).setMessage(message)
            .setNeutralButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dismiss();
                    getActivity().finish();
                }
            }).create();
    }

}
