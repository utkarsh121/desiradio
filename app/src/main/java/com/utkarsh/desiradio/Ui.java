package com.utkarsh.desiradio;

import android.content.Context;
import android.util.TypedValue;
import android.widget.ImageView;

import coil.Coil;
import coil.request.ImageRequest;
import coil.transform.RoundedCornersTransformation;

/** Loads station logos with a fallback radio icon. */
public final class Ui {
    private Ui() {}

    public static void loadLogo(Context context, ImageView view, String url) {
        load(context, view, url, 0);
    }

    /** Loads artwork with rounded corners (radius in dp), Spotify-style. */
    public static void loadArtwork(Context context, ImageView view,
                                   String url, float radiusDp) {
        load(context, view, url, radiusDp);
    }

    private static void load(Context context, ImageView view,
                             String url, float radiusDp) {
        if (url == null || url.isEmpty()) {
            view.setImageResource(R.drawable.ic_radio_small);
            return;
        }
        ImageRequest.Builder b = new ImageRequest.Builder(context)
                .data(url)
                .placeholder(R.drawable.ic_radio_small)
                .error(R.drawable.ic_radio_small)
                .fallback(R.drawable.ic_radio_small)
                .target(view);
        if (radiusDp > 0) {
            float px = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, radiusDp,
                    context.getResources().getDisplayMetrics());
            b.transformations(new RoundedCornersTransformation(px));
        }
        Coil.imageLoader(context).enqueue(b.build());
    }
}
