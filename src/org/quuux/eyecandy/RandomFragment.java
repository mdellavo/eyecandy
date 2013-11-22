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
import android.view.MotionEvent;
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

public class RandomFragment extends Fragment implements View.OnTouchListener {

    public interface Listener {
        void startLeanback();
        void endLeanback();
        boolean isLeanback();
        void setSelectedNavigationItemSilent(int pos);
        void onLeanbackTouch(final MotionEvent ev);
    }

    private static final String TAG = Log.buildTag(RandomFragment.class);

    private BurnsView mBurnsView;
    private ImageAdapter mAdapter;
    private Listener mListener;
    private RequestQueue mRequestQueue;


    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);

        if (!(activity instanceof Listener)) {
            throw new IllegalArgumentException("Activity must implement Listener");
        }

        mListener = (Listener) activity;
    }

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
        mBurnsView.setOnTouchListener(this);

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

        mListener.setSelectedNavigationItemSilent(MainActivity.MODE_BURNS);
        mListener.startLeanback();
    }

    @Override
    public void onPause() {
        super.onPause();

        mBurnsView.stopAnimation();

        final Activity act = getActivity();
        if (act == null)
            return;

        act.unregisterReceiver(mBroadcastReceiver);
        mListener.endLeanback();
    }

    @Override
    public boolean onTouch(final View v, final MotionEvent event) {
        mListener.onLeanbackTouch(event);
        return false;
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
