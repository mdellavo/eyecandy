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
import org.quuux.eyecandy.utils.OkHttpStack;

import java.io.File;

public class EyeCandyVolley extends Volley {

    private static final int CACHE_SIZE = 50 * 1024 * 1024;
    private static RequestQueue sRequestQueue;

    public static RequestQueue getRequestQueue(final Context context) {
        if (sRequestQueue == null) {

            File cacheDir = new File(context.getExternalCacheDir(), "volley");

            String userAgent = "volley/0";
            try {
                String packageName = context.getPackageName();
                PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
                userAgent = packageName + "/" + info.versionCode;
            } catch (PackageManager.NameNotFoundException e) {
            }

            HttpStack stack;
            if (Build.VERSION.SDK_INT >= 9) {
                stack = new HurlStack();
            } else {
                // Prior to Gingerbread, HttpUrlConnection was unreliable.
                // See: http://android-developers.blogspot.com/2011/09/androids-http-clients.html
                stack = new HttpClientStack(AndroidHttpClient.newInstance(userAgent));
            }

            Network network = new BasicNetwork(stack);

            sRequestQueue= new RequestQueue(new DiskBasedCache(cacheDir, CACHE_SIZE), network);
            sRequestQueue.start();

        }

        return sRequestQueue;
    }


}
