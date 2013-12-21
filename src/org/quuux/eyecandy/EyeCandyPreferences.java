package org.quuux.eyecandy;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class EyeCandyPreferences {

    private static final String PREF_LAST_NAV_MODE = "last-nav-mode";
    private static final String PREFS_IS_FLIPPING = "is-flipping";

    static SharedPreferences get(final Context context) {
       return PreferenceManager.getDefaultSharedPreferences(context);
    }

    static SharedPreferences.Editor edit(final Context context) {
        return get(context).edit();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    static void commit(final SharedPreferences.Editor edit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            edit.apply();
        } else {
            edit.commit();
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    static void putStringSet(final SharedPreferences.Editor edit, final String key, final Set<String> val) {
        if (false && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            edit.putStringSet(key, val);
        }

        final StringBuilder sb = new StringBuilder();
        final String[] arr = val.toArray(new String[val.size()]);
        for (int i=0; i<arr.length; i++) {
            if (i>0)
                sb.append("|");
            sb.append(arr[i]);
        }

        edit.putString(key, sb.toString());
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    static Set<String> getStringSet(final SharedPreferences prefs, final String key, final Set<String> defaults) {
        if (false && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            return prefs.getStringSet(key, defaults);
        }

        final String values = prefs.getString(key, "");
        final String[] purchases = values.split("\\|", -1);

        final Set<String> rv = new HashSet<String>(purchases.length);
        for (final String p : purchases)
            rv.add(p);

        return rv;
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

    static void setPurchases(final Context context, final Set<String> purchases) {
        final SharedPreferences.Editor edit = edit(context);
        putStringSet(edit, "purchases", purchases);
        commit(edit);
    }

    static Set<String> getPurchases(final Context context) {
        return getStringSet(get(context), "purchases", Collections.<String>emptySet());
    }

    static boolean isFirstRun(final Context context) {
        return get(context).getBoolean("first-run", true);
    }

    static void markFirstRun(final Context context) {
        final SharedPreferences.Editor edit = edit(context);
        edit.putBoolean("first-run", false);
        commit(edit);
    }
}
