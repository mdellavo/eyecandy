package org.quuux.eyecandy;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import org.quuux.orm.Database;
import org.quuux.orm.Entity;
import org.quuux.orm.FetchListener;
import org.quuux.orm.Query;
import org.quuux.orm.Session;
import org.quuux.orm.util.QueryAdapter;

public class SourcesFragment extends Fragment implements AdapterView.OnItemClickListener {

    public interface Listener {
        void showImage(Query query);
        void showGallery(Query query);
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
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View rv = inflater.inflate(R.layout.sources, container, false);
        mGrid = (GridView) rv.findViewById(R.id.grid);
        mGrid.setAdapter(mAdapter);
        mGrid.setOnScrollListener(mAdapter);
        mGrid.setOnItemClickListener(this);
        return rv;
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
        final Subreddit subreddit = (Subreddit) mAdapter.getItem(position);
        final Session session = EyeCandyDatabase.getInstance(getActivity()).createSession();
        final Query query = session.query(Image.class).filter("subreddit=?", subreddit.getSubreddit()).orderBy("id DESC");
        mListener.showGallery(query);
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
                            mPicasso
                                    .load(result.getThumbnailUrl())
                                    .placeholder(context.getResources().getDrawable(R.drawable.ic_loading))
                                    .resizeDimen(R.dimen.source_thumbnail, R.dimen.source_thumbnail)
                                    .centerCrop()
                                    .into(holder.thumbnail);
                        }
                    });

        }
        
    }
}
