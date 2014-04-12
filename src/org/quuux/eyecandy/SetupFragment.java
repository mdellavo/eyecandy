package org.quuux.eyecandy;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import org.quuux.orm.Entity;
import org.quuux.orm.FetchListener;
import org.quuux.orm.FlushListener;
import org.quuux.orm.QueryListener;
import org.quuux.orm.ScalarListener;
import org.quuux.orm.Session;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class SetupFragment extends ListFragment implements View.OnClickListener {
    interface Listener {
        void onSetupComplete();
    }

    private Listener mListener;

    private static final String TAG = Log.buildTag(SetupFragment.class);
    private Adapter mAdapter;
    private Button mButton;

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
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.setup_fragment, container, false);
        mButton = (Button)v.findViewById(R.id.done);
        mButton.setOnClickListener(this);

        final Session session = EyeCandyDatabase.getSession(getActivity());

        session.query(Subreddit.class).all(new QueryListener<Subreddit>() {
            @Override
            public void onResult(final List<Subreddit> result) {
                if (result == null)
                    return;

                final ListView listView = getListView();
                for (Subreddit s : result) {

                    int position = mAdapter.getPosition(s.getSubreddit());
                    if (position == -1) {
                        mAdapter.add(s.getSubreddit());
                        position = mAdapter.getPosition(s.getSubreddit());
                    }

                    listView.setItemChecked(position, true);

                }
            }
        });

        return v;
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mAdapter = new Adapter(getActivity());



        Arrays.sort(EyeCandyDatabase.SUBREDDITS, new SortIgnoreCase());
        for (final String s : EyeCandyDatabase.SUBREDDITS)
            mAdapter.add(s);

        setListAdapter(mAdapter);

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
    public void onListItemClick(final ListView l, final View v, final int position, final long id) {
        super.onListItemClick(l, v, position, id);

        final String item = mAdapter.getItem(position);
        Log.d(TAG, "clicked: position=%s | item=%s", position, item);

        if (l.isItemChecked(position)) {
            Subreddit.add(getActivity(), item, null);
        } else {
            Subreddit.remove(getActivity(), item, null);
        }

        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onClick(final View v) {
        mListener.onSetupComplete();
    }

    private void showAddDialog() {
        final AddSubredditDialog frag = new AddSubredditDialog();
        frag.setListener(new FetchListener<Subreddit>() {
            @Override
            public void onResult(final Subreddit result) {
                mAdapter.add(result.getSubreddit());
                mAdapter.notifyDataSetChanged();
                getListView().setItemChecked(mAdapter.getPosition(result.getSubreddit()), true);
            }
        });
        frag.show(getActivity().getSupportFragmentManager(), "add-subreddit");
    }

    public static SetupFragment newInstance() {
        final SetupFragment rv = new SetupFragment();
        return rv;
    }

    class ViewHolder {
        TextView text;
    }

    class Adapter extends ArrayAdapter<String> {

        final int mNormalColor;
        final int mSelectedColor;

        final LayoutInflater mInflater;

        Adapter(final Context context) {
            super(context, 0);
            mInflater = getLayoutInflater(null);
            mNormalColor = Color.WHITE;
            mSelectedColor = Color.YELLOW;
        }

        @Override
        public View getView(final int position, final View convertView, final ViewGroup parent) {
            final View v = convertView != null ? convertView : newView(position, parent);
            bindView(v, position, getItem(position));
            return v;
        }

        private void bindView(final View v, final int position, final String item) {
            ViewHolder holder = (ViewHolder) v.getTag();
            if (holder == null) {
                holder = new ViewHolder();
                holder.text = (TextView) v.findViewById(R.id.text);
                v.setTag(holder);
            }

            holder.text.setText(item);
            holder.text.setTextColor(getListView().isItemChecked(position) ? mSelectedColor : Color.WHITE);
        }

        private View newView(final int position, final ViewGroup parent) {
            return mInflater.inflate(R.layout.subreddit_item, parent, false);
        }
    }

    public class SortIgnoreCase implements Comparator<Object> {
        public int compare(Object o1, Object o2) {
            String s1 = (String) o1;
            String s2 = (String) o2;
            return s1.toLowerCase().compareToIgnoreCase(s2.toLowerCase());
        }
    }
}
