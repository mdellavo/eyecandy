package org.quuux.eyecandy;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

public class EyeCandyPreferences {

    private static final String PREF_LAST_NAV_MODE = "last-nav-mode";
    private static final String PREFS_IS_FLIPPING = "is-flipping";

    static SharedPreferences get(final Context context) {
       return PreferenceManager.getDefaultSharedPreferences(context);
    }

    static SharedPreferences.Editor edit(final Context context) {
        return get(context).edit();
    }

    static void commit(SharedPreferences.Editor edit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            edit.apply();
        } else {
            edit.commit();
        }
    }

    static int getLastNavMode(final Context context) {
        return get(context).getInt(PREF_LAST_NAV_MODE, 0);
    }

    static void setLastNavMode(final Context context, final int pos) {
        final SharedPreferences.Editor edit = edit(context);
        edit.putInt(PREF_LAST_NAV_MODE, pos);
        commit(edit);
    }

    static boolean isFlipping(final Context context) {
        return get(context).getBoolean(PREFS_IS_FLIPPING, true);
    }

    static void setFlipping(final Context context, final boolean state) {
        final SharedPreferences.Editor edit = edit(context);
        edit.putBoolean(PREFS_IS_FLIPPING, state);
        commit(edit);
    }


}
