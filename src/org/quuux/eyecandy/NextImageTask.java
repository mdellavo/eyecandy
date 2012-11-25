package org.quuux.eyecandy;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.lang.ref.WeakReference;

class NextImageTask extends AsyncTask<Void, Void, Image> {

    private static final String TAG = "NextImageTask";

    protected WeakReference mContext;
    protected NextImageListener mListener;

    public NextImageTask(Context context, NextImageListener listener) {
        mContext = new WeakReference(context);
        mListener = listener;
    }

    @Override
    protected Image doInBackground(Void... blah) {
        DatabaseHelper db = new DatabaseHelper((Context)mContext.get());
        return db.nextImage();
    }

    @Override
    protected void onPostExecute(Image image) {
        if (mListener != null) {
            mListener.nextImage(image);
        }
    }
}

