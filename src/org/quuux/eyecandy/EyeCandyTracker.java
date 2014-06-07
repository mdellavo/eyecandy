package org.quuux.eyecandy;

import android.content.Context;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

public class EyeCandyTracker {
    private static final String TAG = Log.buildTag(EyeCandyTracker.class);
    private static EyeCandyTracker sTracker;

    private final Tracker mTracker;

    protected EyeCandyTracker(final Tracker tracker) {
        mTracker = tracker;
    }

    public void sendEvent(final String category, final String action) {
        sendEvent(category, action, null);
    }

    public void sendEvent(final String category, final String action, final String label) {
        mTracker.send(new HitBuilders.EventBuilder()
                .setCategory(category)
                .setAction(action)
                .setLabel(label)
                .build());

        Log.d(TAG, "event(%s, %s, %s)", category, action, label);
    }

    public static synchronized EyeCandyTracker get(final Context context) {

        if (sTracker == null) {
            GoogleAnalytics analytics = GoogleAnalytics.getInstance(context.getApplicationContext());
            sTracker = new EyeCandyTracker(analytics.newTracker(R.xml.ga));
        }

        return sTracker;
    }

    public Tracker getTracker() {
        return mTracker;
    }
}
