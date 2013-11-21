package org.quuux.eyecandy;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.GridView;
import android.widget.ImageView;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.Volley;
import org.quuux.eyecandy.utils.BitmapLruCache;
import org.quuux.eyecandy.utils.HorizontalListView;
import org.quuux.eyecandy.utils.OkHttpStack;
import org.quuux.orm.Database;
import org.quuux.orm.Query;
import org.quuux.orm.Session;
import org.quuux.orm.util.QueryAdapter;

public class RandomFragment extends Fragment {

    private static final String TAG = Log.buildTag(RandomFragment.class);

    private BurnsView mBurnsView;
    private ImageAdapter mAdapter;

    private RequestQueue mRequestQueue;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Context context = getActivity();
        mRequestQueue = EyeCandyVolley.getRequestQueue(context);

        mAdapter = new ImageAdapter(context, mRequestQueue);

    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View rv = inflater.inflate(R.layout.main, null, false);

        mBurnsView = (BurnsView)rv.findViewById(R.id.burns);
        mBurnsView.setAdapter(mAdapter);

        return rv;
    }

    @Override
    public void onResume() {
        super.onResume();

        final Activity act = getActivity();
        if (act == null)
            return;

        final IntentFilter filter = new IntentFilter();
        filter.addAction(ScrapeService.ACTION_SCRAPE_COMPLETE);
        act.registerReceiver(mBroadcastReceiver, filter);

        mBurnsView.startAnimation();

        ((MainActivity)act).setSelectedNavigationItemSilent(MainActivity.MODE_BURNS);

    }

    @Override
    public void onPause() {
        super.onPause();

        mBurnsView.stopAnimation();

        final Context context = getActivity();
        if (context != null) {
            context.unregisterReceiver(mBroadcastReceiver);
        }
    }

    public static RandomFragment newInstance() {
        final RandomFragment rv = new RandomFragment();
        final Bundle args = new Bundle();
        rv.setArguments(args);
        return rv;
    }

    final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context context, final Intent intent) {

            final String action = intent.getAction();
            if (ScrapeService.ACTION_SCRAPE_COMPLETE.equals(action)) {
                mAdapter.fillQueue();
            }

        }
    };



}
