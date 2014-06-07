package org.quuux.eyecandy;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.http.AndroidHttpClient;
import android.os.Build;

import com.android.volley.Network;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HttpClientStack;
import com.android.volley.toolbox.HttpStack;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.Volley;
import com.squareup.okhttp.OkHttpClient;

import org.quuux.eyecandy.utils.OkHttpStack;

import java.io.File;

public class EyeCandyVolley extends Volley {

    private static final int CACHE_SIZE = 50 * 1024 * 1024;
    private static RequestQueue sRequestQueue;
    private static OkHttpClient sClient;

    public static RequestQueue getRequestQueue(final Context context) {
        if (sRequestQueue == null) {

            final File cacheDir = new File(context.getExternalCacheDir(), "volley");

            sClient = new OkHttpClient();
            HttpStack stack = new OkHttpStack(context, sClient);
            Network network = new BasicNetwork(stack);

            sRequestQueue= new RequestQueue(new DiskBasedCache(cacheDir, CACHE_SIZE), network);
            sRequestQueue.start();

        }

        return sRequestQueue;
    }

    public static OkHttpClient getClient() {
        return sClient;
    }
}
