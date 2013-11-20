package org.quuux.eyecandy;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class EyeCandyPreferences {

    private static final String PREF_LAST_NAV_MODE = "last-nav-mode";

    final SharedPreferences get(final Context context) {
       return PreferenceManager.getDefaultSharedPreferences(context);
    }

    final SharedPreferences.Editor edit(final Context context) {
        return get(context).edit();
    }

    final int getLastNavMode(final Context context) {
        return get(context).getInt(PREF_LAST_NAV_MODE, -1);
    }

    final void setLastNavMode(final Context context, final int pos) {
        final SharedPreferences.Editor edit = edit(context);
        edit.putInt(PREF_LAST_NAV_MODE, pos);
        edit.apply();
    }


}
