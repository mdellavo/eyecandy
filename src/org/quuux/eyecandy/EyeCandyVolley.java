package org.quuux.eyecandy;

import android.content.Context;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import org.quuux.eyecandy.utils.OkHttpStack;

public class EyeCandyVolley extends Volley {

    private static RequestQueue sRequestQueue;

    public static RequestQueue getRequestQueue(final Context context) {
        if (sRequestQueue == null)
            sRequestQueue = newRequestQueue(context.getApplicationContext(), new OkHttpStack());

        return sRequestQueue;
    }


}
