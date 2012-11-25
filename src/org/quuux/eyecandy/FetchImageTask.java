package org.quuux.eyecandy;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.net.Uri;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLConnection;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.File;

class FetchImageTask extends AsyncTask<Image, Integer, Image> {

    private static final String TAG = "FetchTask";

    private static final int BUF_SIZE = 1024;

    protected WeakReference mContext;
    protected FetchCompleteListener mListener;

    public FetchImageTask(Context context, FetchCompleteListener listener) {
        mContext = new WeakReference(context);
        mListener = listener;
    }

    @Override
    protected Image doInBackground(Image... images) {
        Image rv = null;

        try {
            Image image = images[0];

            Log.d(TAG, "fetching " + image);

            URLConnection conn = new URL(image.getUrl()).openConnection();
            conn.connect();

            int size = conn.getContentLength();

            String cached_image_path = image.getCachedImagePath((Context)mContext.get());

            InputStream input = new BufferedInputStream(conn.getInputStream());
            OutputStream output = new FileOutputStream(cached_image_path);

            byte data[] = new byte[BUF_SIZE];
            long total = 0;
            int count;

            while ((count = input.read(data)) != -1) {
                total += count;
                output.write(data, 0, count);
                //onprofressupdate
            }

            output.flush();
            output.close();
            input.close();

            DatabaseHelper db = new DatabaseHelper((Context)mContext.get());
            db.markFetched(image);
            
            rv = image;
        } catch(Exception e) {
            Log.e(TAG, "Error fetching " + images[0], e);
        }

        return rv;
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {
    }

    @Override
    protected void onPostExecute(Image image) {
        if (mListener != null) {
            mListener.onFetchComplete(image);
        }
    }
}

