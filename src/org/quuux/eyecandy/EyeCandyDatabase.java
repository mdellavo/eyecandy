package org.quuux.eyecandy;

import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;

import org.quuux.orm.Database;
import org.quuux.orm.Entity;
import org.quuux.orm.FetchListener;
import org.quuux.orm.QueryListener;
import org.quuux.orm.Session;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EyeCandyDatabase extends Database {

    private static final String NAME = "eyecandy.db";
    private static final int VERSION = 8;
    private static final String TAG = Log.buildTag(EyeCandyDatabase.class);

    private static EyeCandyDatabase instance = null;

    // http://headlikeanorange.tumblr.com/

    private static final String SUBREDDITS[] = {

            "Cinemagraphs",
            "gifs",
            "naturegifs",
            "oldschoolcool",

            "earthporn",
            "villageporn",
            "cityporn",
            "spaceporn",
            "waterporn",
            "abandonedporn",
            "animalporn",
            "humanporn",
            "botanicalporn",
            "adrenalineporn",
            "destructionporn",
            "movieposterporn",
            "albumartporn",
            "machineporn",
            //"newsporn",
            "geekporn",
            "bookporn",
            //"mapporn",
            "adporn",
            "designporn",
            "roomporn",
            //"militaryporn",
            //"historyporn",
            //"quotesporn",
            "skyporn",
            "fireporn",
            "infrastructureporn",
            "macroporn",
            "instrumentporn",
            "climbingporn",
            "architectureporn",
            "artporn",
            "cemeteryporn",
            //"carporn",
            "fractalporn",
            "exposureporn",
            //"gunporn",
            //"culinaryporn",
            "dessertporn",
            "agricultureporn",
            "boatporn",
            "geologyporn",
            "futureporn",
            "winterporn",
            "JoshuaTree",
            "NZPhotos",
            "EyeCandy",
            "ruralporn",
            "spaceart"
            //"foodporn"
    };



    protected EyeCandyDatabase(final Context context, final String name, final int version) {
        super(context, name, version);
    }

    private static void initSources(final Context context, final EyeCandyDatabase db) {
        final Session session = db.createSession();

        session.query(Subreddit.class).all(new QueryListener<Subreddit>() {
            @Override
            public void onResult(final List<Subreddit> subreddits) {

                final Set<String> knownSubreddits = new HashSet<String>();

                if (subreddits != null) {
                    for (Subreddit subreddit : subreddits) {
                        knownSubreddits.add(subreddit.getSubreddit());
                    }
                }

                for (String s : SUBREDDITS) {
                    if (!knownSubreddits.contains(s)) {
                        Log.d(TAG, "populating subreddit %s", s);
                        final Subreddit subreddit = new Subreddit(s);
                        session.add(subreddit);
                        session.commit();

                        final Intent intent = new Intent(context, ScrapeService.class);
                        intent.putExtra(ScrapeService.EXTRA_SUBREDDIT, subreddit);
                        context.startService(intent);

                    }
                }

            }
        });

    }

    public static EyeCandyDatabase getInstance(final Context context) {

        final boolean doInit;

        if (instance == null) {
            instance = new EyeCandyDatabase(context.getApplicationContext(), NAME, VERSION);
            initSources(context, instance);
        }

        return instance;
    }

}

