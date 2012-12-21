package org.quuux.eyecandy;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.net.Uri;

import java.lang.ref.WeakReference;

import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;

import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;

class FetchImageTask extends AsyncTask<Image, Integer, Image> {

    private static final String TAG = "FetchImageTask";

    private static final int BUF_SIZE = 1024;

    protected WeakReference mContext;
    protected FetchCompleteListener mListener;

    public FetchImageTask(Context context, FetchCompleteListener listener) {
        mContext = new WeakReference(context);
        mListener = listener;
    }

    @Override
    protected Image doInBackground(Image... images) {
        System.setProperty("http.keepAlive", "false");

        Image rv = null;

        Image image = images[0];

        Log.d(TAG, "fetching " + image);

        try {
            URL url = new URL(image.getUrl());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Accept-Encoding", "identity");
   
            try {
                int response_code = conn.getResponseCode();
                Log.d(TAG, "response code = " + response_code);

                if (response_code != 200) {
                    Log.d(TAG, "Fetch not successful: " + conn.getResponseMessage());

                    return null;
                }

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

                Log.d(TAG, "fetch " + (total/1024.0f) + " KB");
            
                output.flush();
                output.close();
                input.close();

                DatabaseHelper db = new DatabaseHelper((Context)mContext.get());
                db.markFetched(image);
            
                rv = image;
            } finally {
                conn.disconnect();
            }

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

