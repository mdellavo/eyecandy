package org.quuux.eyecandy;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.lang.ref.WeakReference;

class MarkImageShownTask extends AsyncTask<Image, Void, Image> {

    private static final String TAG = "MarkImageShownTask";

    protected WeakReference mContext;
 
    public MarkImageShownTask(Context context) {
        mContext = new WeakReference(context);
    }

    @Override
    protected Image doInBackground(Image... images) {
        DatabaseHelper db = new DatabaseHelper((Context)mContext.get());
        db.incShown(images[0]);
        return images[0];
    }
        
}

