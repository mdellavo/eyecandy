package org.quuux.eyecandy;

import android.app.ActionBar;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.*;
import android.widget.ArrayAdapter;
import org.quuux.eyecandy.utils.ViewServer;
import org.quuux.orm.Database;

public class MainActivity
        extends FragmentActivity 
        implements ActionBar.OnNavigationListener, 
                   GalleryFragment.Listener {

    static {
        Database.attach(Image.class);
    }

    private static final String TAG = "MainActivity";
    private static final String FRAG_RANDOM = "random";
    private static final String FRAG_GALLERY = "gallery";
    private static final String FRAG_VIEWER = "viewer";

    final private Handler mHandler = new Handler();


    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        getWindow().requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final ActionBar actionBar = getActionBar();
        actionBar.setBackgroundDrawable(getResources().getDrawable(R.drawable.actionbar));
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

        final ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(this, R.array.modes, android.R.layout.simple_spinner_dropdown_item);
        actionBar.setListNavigationCallbacks(spinnerAdapter, this);

        summon();

        View v = findViewById(android.R.id.content);
        v.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);

        final Intent intent = new Intent(this, ScrapeService.class);
        //startService(intent);

        ViewServer.get(this).addWindow(this);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ViewServer.get(this).removeWindow(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ViewServer.get(this).setFocusedWindow(this);
    }

    @Override
    public void onBackPressed() {

        final Fragment frag = getCurrentFragment();
        if (frag != null &&
                frag instanceof OnBackPressedListener &&
                ((OnBackPressedListener) frag).onBackPressed()) {
          return;
        }

        super.onBackPressed();
    }

    @Override
    public boolean dispatchTouchEvent(final MotionEvent ev) {
        summon();
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onNavigationItemSelected(final int position, final long id) {

        switch (position) {
            case 0:
                onShowViewer();
                break;

            case 1:
                onShowGallery();
                break;

            case 2:
                onShowRandom();
                break;
        }

        return true;
    }

    @Override
    public void showImage(final Image i) {
        onShowImage(i);
    }

    private Fragment getCurrentFragment() {
        return getSupportFragmentManager().findFragmentById(android.R.id.content);
    }

    private void swapFrag(final Fragment fragment, final String tag) {
        final FragmentManager frags = getSupportFragmentManager();
        final FragmentTransaction ft = frags.beginTransaction();
        ft.setTransition(FragmentTransaction.TRANSIT_ENTER_MASK);
        //ft.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
        ft.replace(android.R.id.content, fragment, tag);
        ft.commit();
    }

    private Fragment getFrag(final String tag) {
        final FragmentManager fm = getSupportFragmentManager();
        return fm.findFragmentByTag(tag);
    }

    private void onShowRandom() {
        Fragment frag = getFrag(FRAG_RANDOM);
        if (frag == null)
            frag = RandomFragment.newInstance();
        swapFrag(frag, FRAG_RANDOM);
    }

    private void onShowGallery() {
        Fragment frag = getFrag(FRAG_GALLERY);
        if (frag == null)
            frag = GalleryFragment.newInstance();
        swapFrag(frag, FRAG_GALLERY);
    }

    private void onShowImage(final Image image) {
        Fragment frag = getFrag(FRAG_VIEWER);
        if (frag == null)
            frag = ViewerFragment.newInstance(image);
        swapFrag(frag, FRAG_VIEWER);
    }

    private void onShowViewer() {
        onShowImage(null);
    }


    private void dismiss() {
        //Log.d(TAG, "dismiss ui");
        getActionBar().hide();
    }

    private void dismissDelayed(long t) {
        mHandler.removeCallbacks(mDismissCallback);
        mHandler.postDelayed(mDismissCallback, t);
    }

    private void summon() {
        //Log.d(TAG, "summon ui");

        getActionBar().show();
        dismissDelayed(2500);
    }


    final Runnable mDismissCallback = new Runnable() {
        @Override
        public void run() {
            dismiss();
        }
    };
}
