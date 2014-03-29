package org.quuux.eyecandy;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.text.Html;
import android.view.*;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.vending.billing.IInAppBillingService;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.nineoldandroids.animation.ObjectAnimator;
import com.nineoldandroids.view.*;
import com.nineoldandroids.view.ViewPropertyAnimator;

import org.json.JSONException;
import org.json.JSONObject;
import org.quuux.eyecandy.utils.ViewServer;
import org.quuux.orm.Query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity
        extends ActionBarActivity
        implements ActionBar.OnNavigationListener,
                   ViewerFragment.Listener,
                   RandomFragment.Listener,
                   GalleryFragment.Listener,
                   SourcesFragment.Listener,
                   SetupFragment.Listener {

    private static final String TAG = "MainActivity";

    private static final String ACTION_PURCHASE = "org.quuux.eyecandy.action.PURCHASE";

    private static final String FRAG_RANDOM = "random";
    private static final String FRAG_GALLERY = "gallery-%s";
    private static final String FRAG_VIEWER = "viewer-%s";
    private static final String FRAG_SOURCES = "subreddits";
    private static final String FRAG_SETUP = "setup";

    public static final int MODE_SLIDE_SHOW = 0;
    public static final int MODE_SOURCES = 1;
    public static final int MODE_GALLERY = 2;
    private static final int MODE_SETUP = 3;

    public static final int MODE_BURNS = -1;
    private static final String SKU_UNLOCK = "unlock";

    final private Handler mHandler = new Handler();

    private IInAppBillingService mService;

    private AdView mAdView;

    private GestureDetector mGestureDetector;
    private boolean mSquealch;
    private boolean mLeanback;

    private static boolean sNagShown;
    private Set<String> mPurchases = Collections.<String>emptySet();

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        bindService(new Intent("com.android.vending.billing.InAppBillingService.BIND"),
                mServiceConn, Context.BIND_AUTO_CREATE);

        if (findViewById(android.R.id.content) == null)
            getWindow().requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);

        setContentView(R.layout.base);

        final ActionBar actionBar = getSupportActionBar();
        actionBar.setBackgroundDrawable(getResources().getDrawable(R.drawable.actionbar));
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        actionBar.setDisplayShowTitleEnabled(false);

        final ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
                getSupportActionBar().getThemedContext(),
                R.array.modes,
                R.layout.support_simple_spinner_dropdown_item
        );
        spinnerAdapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
        actionBar.setListNavigationCallbacks(spinnerAdapter, this);


        int mode;
        if (EyeCandyPreferences.isFirstRun(this)) {
            mode = MODE_SETUP;
            onFirstRun();
        } else {
            mode = EyeCandyPreferences.getLastNavMode(this);

            //        mHandler.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                final Intent intent = new Intent(MainActivity.this, ScrapeService.class);
//                startService(intent);
//            }
//       }, 500);


        }

        actionBar.setSelectedNavigationItem(mode);

        mAdView = (AdView) findViewById(R.id.ad);
        mAdView.setAdListener(mAdListener);

        mGestureDetector = new GestureDetector(this, mGestureListener);

        setupSystemUi();

        if (BuildConfig.DEBUG)
            ViewServer.get(this).addWindow(this);

        mPurchases = EyeCandyPreferences.getPurchases(this);
        onPurchasesUpdated();
    }

    private void onFirstRun() {
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (BuildConfig.DEBUG)
            ViewServer.get(this).removeWindow(this);

        if (mServiceConn != null) {
            unbindService(mServiceConn);
        }

        mAdView.destroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (BuildConfig.DEBUG)
            ViewServer.get(this).setFocusedWindow(this);

        if(mLeanback)
            startLeanback();

        final IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PURCHASE);
        registerReceiver(mBroadcastReceiver, filter);

        mAdView.resume();

        checkPlayServices();
    }

    private void checkPlayServices() {
        final int result = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (result != ConnectionResult.SUCCESS) {
            final Dialog dialog = GooglePlayServicesUtil.getErrorDialog(result, this, 0);
            dialog.show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mBroadcastReceiver);
        mAdView.pause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onBackPressed() {

        final Fragment frag = getCurrentFragment();
        if (frag != null &&
                frag instanceof OnBackPressedListener &&
                ((OnBackPressedListener) frag).onBackPressed()) {
          return;
        }

        sNagShown = false;

        super.onBackPressed();
    }

    private final Runnable mLeanbackCallback = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "resuming leanback...");
            startLeanback();
        }
    };

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            if (mLeanback)
                startLeanback();
            else
                exitLeanback();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1001) {
            int responseCode = data.getIntExtra("RESPONSE_CODE", 0);
            String purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA");
            String dataSignature = data.getStringExtra("INAPP_DATA_SIGNATURE");

            if (resultCode == RESULT_OK) {
                onPurchaseComplete();
            }
        }
    }


    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.nag, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {

        boolean rv = false;
        switch (item.getItemId()) {
            case R.id.unlock:
                showNag(true);
                rv = true;
                break;

            default:
                rv = super.onOptionsItemSelected(item);
                break;
        }

        return rv;
    }

    private void onPurchaseComplete() {
        mPurchases.add(SKU_UNLOCK);
        EyeCandyPreferences.setPurchases(this, mPurchases);
        onPurchasesUpdated();
    }

    @Override
    public boolean onLeanbackTouch(final MotionEvent event) {
        // FIXME should just drive this from main with an onleanback listener
        return mGestureDetector.onTouchEvent(event);
    }

    @Override
    public boolean onNavigationItemSelected(final int position, final long id) {

        if (mSquealch) {
            Log.d(TAG, "squealching nav item selection");
            mSquealch = false;
            return true;
        }

        switch (position) {
            case MODE_SLIDE_SHOW:
                if (!isFragCurrently(FRAG_VIEWER))
                    onShowImage();
                break;

            case MODE_BURNS:
                if (!isFragCurrently(FRAG_RANDOM))
                    onShowRandom();
                break;

            case MODE_SOURCES:
                if (!isFragCurrently(FRAG_SOURCES))
                    onShowSources();
                break;

            case MODE_GALLERY:
                if (!isFragCurrently(FRAG_GALLERY))
                    onShowGallery();
                break;

            case MODE_SETUP:
                onShowSetup();
                break;
        }

        EyeCandyPreferences.setLastNavMode(this, position);

        return true;
    }

    private boolean isFragCurrently(final String tag) {
        final Fragment frag = getCurrentFragment();
        return frag != null && tag.equals(frag.getTag());
    }

    @TargetApi(11)
    private void setupSystemUi() {
        final View v = getWindow().getDecorView();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            v.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
                @Override
                public void onSystemUiVisibilityChange(final int visibility) {
                    final boolean isAwake = (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0;

                    Log.d(TAG, "system ui visibility change: isAwake=%s", isAwake);

                    if (isAwake) {
                        getSupportActionBar().show();
                    } else {
                        getSupportActionBar().hide();
                    }

                    slideAd();

                }
            });
        }
    }

    @TargetApi(11)
    private void hideSystemUi() {
        Log.d(TAG, "hide system ui");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                            | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                            | View.SYSTEM_UI_FLAG_IMMERSIVE
            );
        }
    }

    @TargetApi(11)
    private void showSystemUi() {
        Log.d(TAG, "show system ui");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }
    }

    @TargetApi(11)
    private void restoreSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            getWindow().getDecorView().setSystemUiVisibility(0);
        }
    }

    public void startLeanback() {
        Log.d(TAG, "start leanback");
        mLeanback = true;
        getSupportActionBar().hide();
        hideSystemUi();
    }

    public void endLeanback() {
        Log.d(TAG, "end leanback");
        mLeanback = false;
        mHandler.removeCallbacks(mLeanbackCallback);
        getSupportActionBar().show();
        showSystemUi();
    }

    public void exitLeanback() {
        Log.d(TAG, "exiting leanback");
        endLeanback();
        restoreSystemUi();
        slideAd();
    }

    public boolean isLeanback() {
        return mLeanback;
    }

    public void toggleLeanback() {
        if (mLeanback)
            endLeanback();
        else
            startLeanback();
    }

    @TargetApi(11)
    private void slideAd() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {

            final int visibility = getWindow().getDecorView().getSystemUiVisibility();
            final boolean isFloating =
                    (View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION & visibility) != 0 &&
                            (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION & visibility) == 0;

            final int duration = getResources().getInteger(android.R.integer.config_shortAnimTime);
            final int translationY = (int) ViewHelper.getTranslationY(mAdView);
            final int dy = getResources().getDimensionPixelSize(R.dimen.abc_action_bar_default_height);

            Log.d(TAG, "slide: leanback=%s | isFloating=%s | translationY=%s | dy=%s", mLeanback, isFloating, translationY, dy);

            final int px;
            if (!isFloating) {
                px = 0;
            } else {
                px = -dy;
            }

            ViewPropertyAnimator.animate(mAdView).translationY(px).setDuration(duration).start();
        }
    }

    private Fragment getCurrentFragment() {
        return getSupportFragmentManager().findFragmentById(R.id.root);
    }

    private void swapFrag(final Fragment fragment, final String tag, final boolean addToBackStack) {
        if (mLeanback)
            exitLeanback();



        final FragmentManager frags = getSupportFragmentManager();
        if (!addToBackStack)
            frags.popBackStack();
        final FragmentTransaction ft = frags.beginTransaction();
        ft.setTransition(FragmentTransaction.TRANSIT_ENTER_MASK);
        //ft.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
        ft.replace(R.id.root, fragment, tag);
        if (addToBackStack)
            ft.addToBackStack(null);
        ft.commit();
    }

    private void swapFrag(final Fragment fragment, final String tag) {
        swapFrag(fragment, tag, false);
    }

    private Fragment getFrag(final String tag, Object... args) {
        final FragmentManager fm = getSupportFragmentManager();
        return fm.findFragmentByTag(String.format(tag, args));
    }

    private void onShowRandom() {
        Fragment frag = getFrag(FRAG_RANDOM);
        if (frag == null)
            frag = RandomFragment.newInstance();
        swapFrag(frag, FRAG_RANDOM, false);
    }


    public void showImage(final Query query, final int position, final boolean addToBackStack) {
        Fragment frag = getFrag(FRAG_VIEWER, query.toSql().hashCode());
        if (frag == null)
            frag = ViewerFragment.newInstance(query, position);
        swapFrag(frag, FRAG_VIEWER, addToBackStack);
    }

    private void onShowSetup() {
        Fragment frag = getFrag(FRAG_SETUP);
        if (frag == null)
            frag = SetupFragment.newInstance();
        swapFrag(frag, FRAG_SETUP, false);
    }

    public void showImage(final Query query, final int position) {
        showImage(query, position, true);
    }

    public void showImage(final Query query) {
        showImage(query, 0);
    }

    private void onShowImage() {
        final Query q = EyeCandyDatabase.getSession(this).query(Image.class).orderBy("timesShown, RANDOM()");
        showImage(q, 0, false);
    }

    public void showImage(final Subreddit subreddit) {
        final Query q = EyeCandyDatabase.getSession(this).query(Image.class).filter("subreddit=?", subreddit.getSubreddit()).orderBy("\"id\" ASC");
        showImage(q, 0);
    }


    public void showGallery(final Query query, final Subreddit subreddit, final boolean addToBackStack) {
        Fragment frag = getFrag(FRAG_GALLERY, query != null ? query.toSql().hashCode() : 0);
        if (frag == null) {
            frag = GalleryFragment.newInstance(query, subreddit);
        }
        swapFrag(frag, FRAG_GALLERY, addToBackStack);
    }

    public void showGallery( final Query query, final Subreddit subreddit) {
        showGallery(query, subreddit, true);
    }

    public void showGallery(final Subreddit subreddit) {
        final Query q = EyeCandyDatabase.getSession(this).query(Image.class).filter("subreddit=?", subreddit.getSubreddit()).orderBy("\"id\" ASC");
        showGallery(q, subreddit);
    }

    private void onShowGallery() {
        showGallery(null, null, false);
    }

    private void onShowSources() {
        Fragment frag = getFrag(FRAG_SOURCES);
        if (frag == null)
            frag = SourcesFragment.newInstance();
        swapFrag(frag, FRAG_SOURCES, false);
    }

    public void setSelectedNavigationItemSilent(final int pos) {
        final ActionBar ab = getSupportActionBar();
        if (ab == null)
            return;

        ab.setSelectedNavigationItem(pos);
    }

    public void openImage(final Image image) {
        final OpenImageDialog dialog = OpenImageDialog.newInstance(image);
        dialog.show(getSupportFragmentManager(), String.format("open-image-%s", image.getId()));
    }

    private void onPurchaseResult(final Set<String> purchases) {
        if (purchases == null)
            return;

        mPurchases = purchases;
        EyeCandyPreferences.setPurchases(this, purchases);
        onPurchasesUpdated();

    }

    private void onPurchasesUpdated() {
        final boolean unlocked = mPurchases.contains(SKU_UNLOCK);

        if (unlocked) {
            hideAds();
            hideNag();
        } else {
            showNag();
            loadAds();
        }

        supportInvalidateOptionsMenu();
    }

    private void hideNag() {
        final FragmentManager fm = getSupportFragmentManager();
        final NagDialog nag = (NagDialog) fm.findFragmentByTag("nag");
        if (nag != null) {
            nag.dismiss();
        }

    }

    private void hideAds() {
        mAdView.setVisibility(View.GONE);
        mAdView.destroy();
    }

    private void loadAds() {
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);
        mAdView.setVisibility(View.VISIBLE);
    }

    private void showNag(final boolean forced) {
        if (!sNagShown || forced) {
            final FragmentManager fm = getSupportFragmentManager();
            if (fm.findFragmentByTag("nag") == null) {
                final NagDialog dialog = NagDialog.newInstance();
                dialog.show(getSupportFragmentManager(), "nag");
            }
            sNagShown = true;
        }
    }

    private void showNag() {
        showNag(false);
    }

    @Override
    public void onSetupComplete() {
        EyeCandyPreferences.markFirstRun(this);
        onShowSources();
    }

    static class ImageOpenerAdapter extends ArrayAdapter<ResolveInfo> {

        public ImageOpenerAdapter(final Context context, final Image image) {
            super(context, 0);
            init(context, image);
        }

        private void init(final Context context, final Image image) {
            final PackageManager manager = context.getPackageManager();

            final Intent viewIntent = buildViewIntent(image);
            final List<ResolveInfo> viewActivites = queryActvities(manager, viewIntent);
            for (final ResolveInfo i : viewActivites) {
                Log.d(TAG, "adding match - %s", i);
                add(i);
            }

            final Intent shareIntent = buildShareIntent(image);
            final List<ResolveInfo> shareActivites = queryActvities(manager, shareIntent);
            for (final ResolveInfo i : shareActivites) {
                Log.d(TAG, "adding match - %s", i);
                add(i);
            }

        }

        private Intent buildShareIntent(final Image image) {
            final Intent intent = new Intent();
            intent.setAction(Intent.ACTION_SEND);
            final String text = String.format("%s - %s", image.getTitle(), image.getUrl());
            intent.putExtra(Intent.EXTRA_TEXT, text);
            intent.setType("text/plain");
            return intent;
        }

        private Intent buildViewIntent(final Image image) {
            final Intent intent = new Intent();
            intent.setAction(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(image.getUrl()));
            return intent;
        }


        private List<ResolveInfo> queryActvities(final PackageManager manager, final Intent intent) {
            return manager.queryIntentActivities(
                    intent,
                    PackageManager.GET_INTENT_FILTERS | PackageManager.GET_RESOLVED_FILTER
            );
        }

        @Override
        public View getView(final int position, final View convertView, final ViewGroup parent) {

            final PackageManager pm = getContext().getPackageManager();
            final ResolveInfo info = getItem(position);

            View v = convertView;
            if (v == null) {
                final LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(LAYOUT_INFLATER_SERVICE);
                v = inflater.inflate(R.layout.open_image_item, parent, false);
            }

            final TextView header = (TextView)v.findViewById(R.id.header);

            final ResolveInfo prev = (position > 1 && position <= getCount() - 1) ? getItem(position - 1) : null;

            if (position == 0) {
                header.setVisibility(View.VISIBLE);
                header.setText(R.string.open_with);
            } else if (prev != null && prev.filter.hasAction(Intent.ACTION_VIEW) && info.filter.hasAction(Intent.ACTION_SEND)) {
                header.setVisibility(View.VISIBLE);
                header.setText(R.string.share_with);
            } else {
                header.setVisibility(View.GONE);
            }

            final ImageView icon = (ImageView) v.findViewById(R.id.icon);
            icon.setImageDrawable(info.loadIcon(pm));

            final TextView label = (TextView) v.findViewById(R.id.label);
            label.setText(info.loadLabel(pm));

            return v;
        }
    }

    public static class OpenImageDialog extends DialogFragment {
        public OpenImageDialog() {
            super();
        }

        public static OpenImageDialog newInstance(final Image image) {
            final OpenImageDialog rv = new OpenImageDialog();
            final Bundle args = new Bundle();
            args.putSerializable("image", image);
            rv.setArguments(args);
            return rv;
        }

        private Image getImage() {
            return (Image) getArguments().getSerializable("image");
        }

        @Override
        public Dialog onCreateDialog(final Bundle savedInstanceState) {

            final Activity act = getActivity();

            final AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(act, android.R.style.Theme_Holo_Light));
            builder.setTitle(R.string.open_dialog_title);

            final ImageOpenerAdapter adapter = new ImageOpenerAdapter(act, getImage());

            builder.setAdapter(
                    adapter,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog, final int which) {
                            final ResolveInfo info = adapter.getItem(which);

                            final Intent i;
                            if (info.filter.hasAction(Intent.ACTION_VIEW)) {
                                i = adapter.buildViewIntent(getImage());
                            } else if (info.filter.hasAction(Intent.ACTION_SEND)) {
                                i = adapter.buildShareIntent(getImage());
                            } else {
                                i = null;
                                return;
                            }

                            i.setClassName(info.activityInfo.applicationInfo.packageName, info.activityInfo.name);
                            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

                            startActivity(i);
                        }
                    });

            return builder.create();
        }
    }

    final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context context, final Intent intent) {

            final String action = intent.getAction();

            if (ACTION_PURCHASE.equals(action)) {
                startPurchase();
            }

        }
    };

    private void startPurchase() {

        if (mService == null)
            return;

        final Bundle response;
        try {
            response = mService.getBuyIntent(3, getPackageName(), SKU_UNLOCK, "inapp", null);
        } catch (final RemoteException e) {
            Log.e(TAG, "error starting purchase", e);
            return;
        }

        final int responseCode = response.getInt("RESPONSE_CODE");
        if (responseCode != 0) {
            Log.d(TAG, "error starting purchase: %s", response);
            return;
        }

        final PendingIntent pendingIntent = response.getParcelable("BUY_INTENT");
        try {
            startIntentSenderForResult(pendingIntent.getIntentSender(),
                    1001, new Intent(), Integer.valueOf(0), Integer.valueOf(0),
                    Integer.valueOf(0));
        } catch (final IntentSender.SendIntentException e) {
            Log.e(TAG, "error starting purchase: %s", e);
        }
    }

    final Runnable mTapCallback = new Runnable() {
        @Override
        public void run() {

        }
    };

    final GestureDetector.OnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener() {

        @Override
        public boolean onSingleTapUp(final MotionEvent e) {
            supportInvalidateOptionsMenu();
            toggleLeanback();

            if (mLeanback) {
                mHandler.removeCallbacks(mLeanbackCallback);
                mHandler.postDelayed(mLeanbackCallback, 2500);
            }

            return false;
        }

    };

    private AdListener mAdListener = new AdListener() {
        @Override
        public void onAdClosed() {
            super.onAdClosed();
            Log.d(TAG, "ad closed");
        }

        @Override
        public void onAdFailedToLoad(final int errorCode) {
            super.onAdFailedToLoad(errorCode);
            Log.d(TAG, "ad failed to load: %s", errorCode);
        }

        @Override
        public void onAdLeftApplication() {
            super.onAdLeftApplication();
            Log.d(TAG, "ad left application");
        }

        @Override
        public void onAdOpened() {
            super.onAdOpened();
            Log.d(TAG, "ad opened");
        }

        @Override
        public void onAdLoaded() {
            super.onAdLoaded();
            Log.d(TAG, "ad loaded");
        }
    };

    public static class NagDialog extends DialogFragment {

        public NagDialog() {
            super();
        }

        public static NagDialog newInstance() {
            final NagDialog rv = new NagDialog();
            return rv;
        }

        @Override
        public Dialog onCreateDialog(final Bundle savedInstanceState) {
            final Context context = getActivity();
            if (context == null)
                return null;

            final View v = getActivity().getLayoutInflater().inflate(R.layout.nag, null);
            if (v == null)
                return null;

            final TextView nagText = (TextView) v.findViewById(R.id.nag_text);
            nagText.setText(Html.fromHtml(getString(R.string.nag_text)));

            final Button button = (Button) v.findViewById(R.id.purchase_button);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    context.sendBroadcast(new Intent(ACTION_PURCHASE));
                }
            });

            final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            final Dialog dialog = builder.setTitle(R.string.nag_title)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, null)
                    .setView(v)
                    .create();

            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);

            return dialog;
        }
    }

    final ServiceConnection mServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            mService = IInAppBillingService.Stub.asInterface(service);
            initBilling();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

    };

    private void initBilling() {
        new QueryPurchasesTask().execute();
    }

    class QueryPurchasesTask extends AsyncTask<String, Void, Set<String>> {

        @Override
        protected Set<String> doInBackground(final String... params) {
            final Bundle purchases;
            try {
                purchases = mService.getPurchases(3, getPackageName(), "inapp", null);
            } catch (RemoteException e) {
                e.printStackTrace();
                return null;
            }

            final int response = purchases.getInt("RESPONSE_CODE");
            if (response != 0) {
                Log.e(TAG, "Error querying purchases: %s", purchases);
                return null;
            }

            final List<String> purchasedSkus = purchases.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");
            final Set<String> rv = new HashSet<String>();
            rv.addAll(purchasedSkus);
            return rv;
        }

        @Override
        protected void onPostExecute(final Set<String> purchases) {
            onPurchaseResult(purchases);
        }
    }

}
