package org.quuux.eyecandy;

import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;

import org.quuux.orm.Database;
import org.quuux.orm.Entity;
import org.quuux.orm.FetchListener;
import org.quuux.orm.QueryListener;
import org.quuux.orm.ScalarListener;
import org.quuux.orm.Session;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EyeCandyDatabase extends Database {

    private static final String NAME = "eyecandy.db";
    private static final int VERSION = 13;
    private static final String TAG = Log.buildTag(EyeCandyDatabase.class);

    private static EyeCandyDatabase instance = null;

    // TODO
    // http://headlikeanorange.tumblr.com/
    // http://radar.weather.gov/Conus/Loop/NatLoop.gif
    // romain guy's
    // wikipaintings

    public static final String SUBREDDITS[] = {
            "woahdude",
            "Cinemagraphs",
            "gifs",
            "naturegifs",
            "oldschoolcool",
            "aww",
            "perfectloops",
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
            "newsporn",
            "geekporn",
            "bookporn",
            "mapporn",
            "adporn",
            "designporn",
            "roomporn",
            "militaryporn",
            "historyporn",
            "quotesporn",
            "skyporn",
            "fireporn",
            "infrastructureporn",
            "macroporn",
            "instrumentporn",
            "climbingporn",
            "architectureporn",
            "artporn",
            "cemeteryporn",
            "carporn",
            "fractalporn",
            "exposureporn",
            "gunporn",
            "culinaryporn",
            "dessertporn",
            "agricultureporn",
            "boatporn",
            "geologyporn",
            "futureporn",
            "winterporn",
            "ruralporn",
            "spaceart",
            "foodporn"
    };

    static {
        Arrays.sort(SUBREDDITS);
    }

    protected EyeCandyDatabase(final Context context, final String name, final int version) {
        super(context, name, version);
    }

    public static EyeCandyDatabase getInstance(final Context context) {

        if (instance == null) {
            instance = new EyeCandyDatabase(context.getApplicationContext(), NAME, VERSION);
        }

        return instance;
    }

    public static Session getSession(final Context context) {
        return getInstance(context).createSession();
    }

}

