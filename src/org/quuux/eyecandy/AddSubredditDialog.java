package org.quuux.eyecandy;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import org.quuux.orm.FetchListener;

public class AddSubredditDialog extends DialogFragment {

    EditText mEditTextSubreddit;
    private FetchListener<Subreddit> mListener;

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {

        final Context context = getActivity();


        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.dialog_add_subreddit_title);

        final LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view = inflater.inflate(R.layout.add_subreddit, null);
        builder.setView(view);

        mEditTextSubreddit = (EditText) view.findViewById(R.id.subreddit);

        builder.setPositiveButton(R.string.add, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                final String s = mEditTextSubreddit.getText().toString().trim();
                if (!TextUtils.isEmpty(s)) {
                    Subreddit.add(getActivity(), s, mListener);
                }
            }
        });

        builder.setNegativeButton(R.string.cancel, null);

        return builder.create();
    }

    public void setListener(final FetchListener<Subreddit> listener) {
        mListener = listener;
    }

}
