package org.quuux.eyecandy;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.lang.ref.WeakReference;

class SampleImageTask extends AsyncTask<Image, Void, Bitmap> {

    private static final String TAG = "SampleImageTask";

    protected WeakReference mContext;
    protected SampleCompleteListener mListener;
    protected Image mImage;
    protected int mWidth, mHeight;
    
    public SampleImageTask(Context context, int width, int height, SampleCompleteListener listener) {
        mContext = new WeakReference(context);
        mListener = listener;
        mWidth = width;
        mHeight = height;
    }

    // http://developer.android.com/training/displaying-bitmaps/load-bitmap.html
    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            if (width > height) {
                inSampleSize = Math.round((float)height / (float)reqHeight);
            } else {
                inSampleSize = Math.round((float)width / (float)reqWidth);
            }
        }

        return inSampleSize;
    }

    @Override
    protected Bitmap doInBackground(Image... images) {
        mImage = images[0];

        String image_path = mImage.getCachedImagePath((Context)mContext.get());
        final BitmapFactory.Options options = new BitmapFactory.Options();

        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(image_path, options);
  
        options.inJustDecodeBounds = false;
        options.inSampleSize = calculateInSampleSize(options, mWidth, mHeight);

        Log.d(TAG, "original size = " + options.outWidth + "x" + options.outHeight);
        Log.d(TAG, "requested size = " + mWidth + "x" + mHeight);
        Log.d(TAG, "sample size = " + options.inSampleSize);

        Bitmap sampled = BitmapFactory.decodeFile(image_path, options);

        // TODO cache sampled bitmap

        return sampled;
    }

    @Override
    protected void onPostExecute(Bitmap sampled) {
        if (mListener != null) {
            mListener.onSampleComplete(mImage, sampled);
        }
    }
}

