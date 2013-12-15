package org.quuux.eyecandy.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.Element;
import android.support.v8.renderscript.RenderScript;
import android.support.v8.renderscript.ScriptIntrinsicBlur;

import org.quuux.eyecandy.Log;

import java.util.concurrent.Executor;

public class ImageUtils {

    public interface Listener {
        void complete(Bitmap bitmap);
    }

    static abstract class ImageOp extends AsyncTask<Void, Void, Bitmap> {

        private static final String TAG = Log.buildTag(ImageOp.class);
        private final Listener mListener;

        ImageOp(final Listener listener) {
            mListener = listener;
        }

        abstract Bitmap doImageOp();

        @Override
        protected Bitmap doInBackground(final Void... params) {
            try {
                return doImageOp();
            } catch (final Exception e) {
                Log.e(TAG, "Error during image op", e);
            }

            return null;
        }

        @Override
        protected void onPostExecute(final Bitmap bitmap) {
            if (mListener != null)
                mListener.complete(bitmap);
        }
    }

    public static void blur(final Context context, final Bitmap src,  final float radius, final Listener listener) {
        final Bitmap in = src.copy(Bitmap.Config.ARGB_8888, false);

        final ImageOp op = new ImageOp(listener) {
            @Override
            Bitmap doImageOp() {
                final Bitmap out = Bitmap.createBitmap(in.getWidth(), in.getHeight(), Bitmap.Config.ARGB_8888);

                final RenderScript rs = RenderScript.create(context);
                final Allocation tmpIn = Allocation.createFromBitmap(rs, in);

                final ScriptIntrinsicBlur theIntrinsic = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
                final Allocation tmpOut = Allocation.createFromBitmap(rs, out);

                theIntrinsic.setRadius(radius);
                theIntrinsic.setInput(tmpIn);
                theIntrinsic.forEach(tmpOut);
                tmpOut.copyTo(out);
                rs.destroy();

                return out;
            }
        };

        op.execute();
    }

}
