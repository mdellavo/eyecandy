package org.quuux.eyecandy;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;

import android.view.ContextMenu;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageRequest;
import com.nineoldandroids.view.ViewHelper;
import com.squareup.picasso.Picasso;

import org.quuux.eyecandy.utils.ImageUtils;
import org.quuux.orm.Database;
import org.quuux.orm.FetchListener;
import org.quuux.orm.FlushListener;
import org.quuux.orm.Func;
import org.quuux.orm.Query;
import org.quuux.orm.ScalarListener;
import org.quuux.orm.Session;
import org.quuux.orm.util.QueryAdapter;

public class SourcesFragment extends Fragment implements AdapterView.OnItemClickListener {

    private static final String TAG = Log.buildTag(SourcesFragment.class);

    public interface Listener {
        void showImage(Subreddit subreddit);
        void showGallery(Subreddit subreddit);
    }

    private Listener mListener;
    private Picasso mPicasso;
    private Adapter mAdapter;
    private GridView mGrid;

    public static SourcesFragment newInstance() {
        final SourcesFragment frag = new SourcesFragment();
        final Bundle args = new Bundle();
        frag.setArguments(args);
        return frag;
    }

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

        setHasOptionsMenu(true);

        mPicasso = EyeCandyPicasso.getInstance(context);

        final Database db = EyeCandyDatabase.getInstance(context);
        final Session session = db.createSession();
        final Query query = session.query(Subreddit.class).orderBy("subreddit");
        mAdapter = new Adapter(context, query);

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

        // FIXME move to listener, do this elsewhere too
        ((MainActivity)act).setSelectedNavigationItemSilent(MainActivity.MODE_SOURCES);

    }

    @Override
    public void onPause() {
        super.onPause();

        final Context context = getActivity();
        if (context != null) {
            context.unregisterReceiver(mBroadcastReceiver);
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View rv = inflater.inflate(R.layout.sources, container, false);

        mGrid = (GridView) rv.findViewById(R.id.grid);
        mGrid.setAdapter(mAdapter);
        mGrid.setOnScrollListener(mAdapter);
        mGrid.setOnItemClickListener(this);
        registerForContextMenu(mGrid);

        final Session session = EyeCandyDatabase.getSession(getActivity());
        session.query(Image.class).orderBy(Func.RANDOM).limit(1).first(new FetchListener<Image>() {
            @Override
            public void onResult(final Image image) {
                if (image != null)
                    setBackground((ImageView) rv.findViewById(R.id.backing), image);
            }
        });


        return rv;
    }

    private void setBackground(final ImageView v, final Image image) {
        final Activity act = getActivity();
        if (act == null)
            return;

        final Display display = getActivity().getWindowManager().getDefaultDisplay();
        final int width = display.getWidth();
        final int height = display.getHeight();

        final Response.Listener<Bitmap> listener = new Response.Listener<Bitmap>() {
            @Override
            public void onResponse(final Bitmap src) {
                ImageUtils.blur(act, src, 25, new ImageUtils.Listener() {
                    @Override
                    public void complete(final Bitmap bitmap) {

                        if (bitmap != null) {
                            v.setImageBitmap(bitmap);
                            ViewHelper.setAlpha(v, .4f);
                        }
                    }
                });
            }
        };

        final Response.ErrorListener errorListener = new Response.ErrorListener() {
            @Override
            public void onErrorResponse(final VolleyError error) {
                Log.e(TAG, "error loading image for backing - %s", error, image);
            }
        };

        final ImageRequest request = new ImageRequest(
                image.getUrl(),
                listener,
                width,
                height,
                Bitmap.Config.ARGB_8888,
                errorListener
        );

        final RequestQueue requestQueue = EyeCandyVolley.getRequestQueue(act);
        requestQueue.add(request);

    }

    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v, final ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        final MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.source_context_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(final MenuItem item) {
        final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

        final Subreddit subreddit = mAdapter.getItem(info.position);

        final boolean rv;
        switch(item.getItemId()) {

            case R.id.slideshow:
                mListener.showImage(subreddit);
                rv = true;
                break;

            case R.id.gallery:
                mListener.showGallery(subreddit);
                rv = true;
                break;

            case R.id.refresh:
                refreshSubreddit(subreddit);
                rv = true;
                break;

            case R.id.delete:
                deleteSubreddit(subreddit);
                rv = true;
                break;

           default:
               rv = super.onContextItemSelected(item);
               break;
        }

        return rv;
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.sources, menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {

        final boolean rv;
        switch(item.getItemId()) {
            case R.id.add:
                showAddDialog();
                rv = true;
                break;

            default:
                rv = super.onOptionsItemSelected(item);
                break;
        }

        return rv;
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
        final Subreddit subreddit = (Subreddit) mAdapter.getItem(position);
        final OpenDialog dialog = OpenDialog.newInstance(subreddit);
        dialog.show(getActivity().getSupportFragmentManager(), "open-source-" + subreddit.getSubreddit());

    }

    private void openSubreddit(final Subreddit subreddit) {
        final Context context = getActivity();
        if (context == null)
            return;

        mListener.showGallery(subreddit);
    }

    private void showAddDialog() {
        final AddSubredditDialog frag = new AddSubredditDialog();
        frag.setListener(new FetchListener<Subreddit>() {
            @Override
            public void onResult(final Subreddit result) {
                if (mAdapter.getPositionForItem(result) == -1)
                    mAdapter.add(result);
            }
        });
        frag.show(getActivity().getSupportFragmentManager(), "add-subreddit");
    }

    private void refreshSubreddit(final Subreddit subreddit) {
        final Context context = getActivity();
        if (context == null)
            return;

        Subreddit.refresh(context, subreddit);
    }

    private void deleteSubreddit(final Subreddit subreddit) {
        final Context context = getActivity();
        if (context == null)
            return;

        Log.d(TAG, "deleting subreddit: %s", subreddit.getSubreddit());
        mAdapter.remove(subreddit);
        Subreddit.remove(context, subreddit.getSubreddit(), null);
        mAdapter.notifyDataSetChanged();
    }

    static class Adapter extends QueryAdapter<Subreddit> {

        private static class Holder {
            ImageView thumbnail;
            TextView subreddit;
        }

        private final LayoutInflater mInflater;
        private Picasso mPicasso;

        public Adapter(final Context context, final Query query) {
            super(context, query);
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mPicasso = EyeCandyPicasso.getInstance(context);
        }

        @Override
        protected View newView(final Context context, final Subreddit item, final ViewGroup parent) {
            final View rv = mInflater.inflate(R.layout.source_item, null);
            final Holder holder = new Holder();
            holder.thumbnail = (ImageView) rv.findViewById(R.id.thumbnail);
            holder.subreddit = (TextView) rv.findViewById(R.id.subreddit);
            rv.setTag(holder);
            return rv;
        }

        @Override
        protected void bindView(final Context context, final Subreddit item, final View view, final ViewGroup parent) {
            final Holder holder = (Holder) view.getTag();
            holder.subreddit.setText(item.getSubreddit());

            final EyeCandyDatabase db = EyeCandyDatabase.getInstance(context);

            db.createSession()
                    .query(Image.class)
                    .filter("subreddit=?",  item.getSubreddit())
                    .orderBy("id DESC")
                    .limit(1)
                    .first(new FetchListener<Image>() {
                        @Override
                        public void onResult(final Image result) {
                            if (result == null) {
                                holder.thumbnail.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_loading));
                                return;
                            }

                            final String thumbnailUrl = result.getThumbnailUrl();

                            if (thumbnailUrl != null)
                                mPicasso
                                        .load(thumbnailUrl)
                                        .placeholder(context.getResources().getDrawable(R.drawable.ic_loading))
                                        .resizeDimen(R.dimen.source_thumbnail, R.dimen.source_thumbnail)
                                        .centerCrop()
                                        .into(holder.thumbnail);
                        }
                    });

        }
        
    }

    public static class OpenDialog extends DialogFragment {

        static final int MODE_SLIDESHOW = 0;
        static final int MODE_GALLERY = 1;
        private Listener mListener;

        @Override
        public void onAttach(final Activity activity) {
            super.onAttach(activity);

            if (!(activity instanceof Listener)) {
                throw new IllegalArgumentException("Activity must implement Listener");
            }

            mListener = (Listener) activity;
        }

        @Override
        public Dialog onCreateDialog(final Bundle savedInstanceState) {

            final Context context = getActivity();

            final AlertDialog.Builder builder =  new AlertDialog.Builder(context);
            builder.setTitle(R.string.dialog_open_source);

            final String[] items = context.getResources().getStringArray(R.array.open_items);

            builder.setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int which) {

                    final Subreddit subreddit = (Subreddit) getArguments().getSerializable("subreddit");

                    switch(which) {

                        case MODE_SLIDESHOW:
                             mListener.showImage(subreddit);
                            break;

                        case MODE_GALLERY:
                            mListener.showGallery(subreddit);
                            break;
                    }
                }
            });

            return builder.create();
        }

        public static OpenDialog newInstance(final Subreddit subreddit) {
            final OpenDialog rv = new OpenDialog();
            final Bundle args = new Bundle();
            args.putSerializable("subreddit", subreddit);
            rv.setArguments(args);
            return rv;
        }
    }

    final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context context, final Intent intent) {

            final String action = intent.getAction();
            if (ScrapeService.ACTION_SCRAPE_COMPLETE.equals(action)) {
                if (mAdapter.getCount() == 0)
                    mAdapter.notifyDataSetChanged();
            }
        }
    };

}
