package org.quuux.eyecandy;

import android.content.Context;
import android.net.Uri;

import com.squareup.picasso.Downloader;
import com.squareup.picasso.OkHttpDownloader;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.IOException;

public class EyeCandyPicasso {
    private static final String TAG = Log.buildTag(EyeCandyPicasso


            .class);

    private static Picasso sPicasso;

    public static Picasso getInstance(final Context context) {

        if (sPicasso == null) {
            final File cacheDir = new File(context.getExternalCacheDir(), "thumbnails");

            sPicasso = new Picasso.Builder(context.getApplicationContext()).downloader(new OkHttpDownloader(cacheDir) {

                @Override
                public Response load(final Uri uri, final boolean localCacheOnly) throws IOException {
                    final long t1 = System.currentTimeMillis();
                    final Response rv = super.load(uri, localCacheOnly);
                    final long t2 = System.currentTimeMillis();

                    Log.d(TAG, "fetched image %s in %sms", uri, t2-t1);

                    return rv;
                }

            }).build();

        }

        return sPicasso;
    }

}
