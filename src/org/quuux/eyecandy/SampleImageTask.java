package org.quuux.eyecandy;

import android.content.Context;
import android.os.AsyncTask;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.lang.ref.WeakReference;

class SampleImageTask extends AsyncTask<Image, Void, Bitmap> {

    private static final Log mLog = new Log(SampleImageTask.class);

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

            // Calculate ratios of height and width to requested height and width
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);

            // Choose the smallest ratio as inSampleSize value, this will guarantee
            // a final image with both dimensions larger than or equal to the
            // requested height and width.
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }

        return inSampleSize;
    }

    @Override
    protected Bitmap doInBackground(Image... images) {
        mImage = images[0];

        String image_path = mImage.getCachedImagePath((Context)mContext.get());
        mLog.d("opening image for sampling: " + image_path);

        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(image_path, options);
  
        options.inJustDecodeBounds = false;
        options.inSampleSize = calculateInSampleSize(options, mWidth, mHeight);

        mLog.d("sampling %dx%d image to requested %dx%d (sample size = %d)",
                options.outWidth, options.outHeight, mWidth, mHeight, options.inSampleSize);

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

