package com.utkarsh.desiradio;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Loads the baked-in station list and persists user choices (favorites, hidden, default). */
public class StationStore {
    private static final String PREFS = "desi_radio_prefs";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_HIDDEN = "hidden";
    private static final String KEY_DEFAULT = "default_url";
    private static final String KEY_AUTOPLAY = "autoplay";

    private final SharedPreferences prefs;
    private final List<Station> all = new ArrayList<>();

    public StationStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load(context);
    }

    private void load(Context context) {
        try (InputStream in = context.getAssets().open("stations.json");
             java.io.ByteArrayOutputStream out =
                     new java.io.ByteArrayOutputStream()) {
            // Read fully: a single InputStream.read() call is not guaranteed
            // to fill the buffer, and a truncated read breaks JSON parsing.
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            String json = out.toString(StandardCharsets.UTF_8.name());
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                all.add(new Station(
                        o.optString("name", "Unknown station"),
                        o.optString("stream_url", ""),
                        o.optString("language", ""),
                        o.optString("genre", ""),
                        o.optString("logo_url", "")));
            }
        } catch (Exception ignored) {
        }
    }

    public List<Station> getAllStations() {
        return new ArrayList<>(all);
    }

    public List<Station> getVisibleStations() {
        Set<String> hidden = getHidden();
        List<Station> out = new ArrayList<>();
        for (Station s : all) {
            if (!hidden.contains(s.streamUrl)) out.add(s);
        }
        return out;
    }

    public List<Station> getFavoriteStations() {
        Set<String> favs = getFavorites();
        List<Station> out = new ArrayList<>();
        for (Station s : getVisibleStations()) {
            if (favs.contains(s.streamUrl)) out.add(s);
        }
        return out;
    }

    public Station findByUrl(String url) {
        if (url == null) return null;
        for (Station s : all) {
            if (url.equals(s.streamUrl)) return s;
        }
        return null;
    }

    private Set<String> getStringSet(String key) {
        return new HashSet<>(prefs.getStringSet(key, new HashSet<>()));
    }

    private void putStringSet(String key, Set<String> set) {
        prefs.edit().putStringSet(key, set).apply();
    }

    public Set<String> getFavorites() {
        return getStringSet(KEY_FAVORITES);
    }

    public boolean isFavorite(String url) {
        return getFavorites().contains(url);
    }

    public void toggleFavorite(String url) {
        Set<String> set = getStringSet(KEY_FAVORITES);
        if (set.contains(url)) set.remove(url);
        else set.add(url);
        putStringSet(KEY_FAVORITES, set);
    }

    public Set<String> getHidden() {
        return getStringSet(KEY_HIDDEN);
    }

    public boolean isHidden(String url) {
        return getHidden().contains(url);
    }

    public void setHidden(String url, boolean hidden) {
        Set<String> set = getStringSet(KEY_HIDDEN);
        if (hidden) set.add(url);
        else set.remove(url);
        putStringSet(KEY_HIDDEN, set);
    }

    public String getDefaultStreamUrl() {
        return prefs.getString(KEY_DEFAULT, null);
    }

    public void setDefaultStreamUrl(String url) {
        prefs.edit().putString(KEY_DEFAULT, url).apply();
    }

    public boolean isAutoplay() {
        return prefs.getBoolean(KEY_AUTOPLAY, true);
    }

    public void setAutoplay(boolean autoplay) {
        prefs.edit().putBoolean(KEY_AUTOPLAY, autoplay).apply();
    }
}
