package org.quuux.eyecandy.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.Element;
import android.support.v8.renderscript.RenderScript;
import android.support.v8.renderscript.ScriptIntrinsicBlur;

public class ImageUtils {

    public static void blur(final Context context, final Bitmap in, final Bitmap out, final float radius) {
        final RenderScript rs = RenderScript.create(context);
        final Allocation tmpIn = Allocation.createFromBitmap(rs, in);

        final ScriptIntrinsicBlur theIntrinsic = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
        final Allocation tmpOut = Allocation.createFromBitmap(rs, out);
        theIntrinsic.setRadius(radius);
        theIntrinsic.setInput(tmpIn);
        theIntrinsic.forEach(tmpOut);
        tmpOut.copyTo(out);
        rs.destroy();
    }

}
