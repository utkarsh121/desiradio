package com.utkarsh.desiradio;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.mediarouter.app.MediaRouteButton;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.common.images.WebImage;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Full-screen player (Spotify-style): rounded artwork, title/artist row,
 * centered transport, favorite, default station, and Cast support.
 */
public class PlayerActivity extends AppCompatActivity {

    private StationStore store;
    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;

    private ImageView artwork;
    private TextView headerStation;
    private TextView songTitle;
    private TextView songArtist;
    private TextView liveBadge;
    private ImageButton btnPlay;
    private ImageButton btnStar;
    private MaterialButton btnDefault;
    private MediaRouteButton castButton;

    // ---- Cast state ----
    private CastContext castContext;
    private CastSession castSession;
    private boolean casting = false;
    private String lastCastKey = "";

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onMediaItemTransition(MediaItem mediaItem, int reason) {
            updateUi();
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            updateUi();
        }

        @Override
        public void onMediaMetadataChanged(MediaMetadata mediaMetadata) {
            updateUi();
        }
    };

    private final SessionManagerListener<CastSession> castSessionListener =
            new SessionManagerListener<CastSession>() {
                @Override
                public void onSessionStarted(CastSession session, String sessionId) {
                    onCastConnected(session);
                }

                @Override
                public void onSessionResumed(CastSession session, boolean wasSuspended) {
                    onCastConnected(session);
                }

                @Override
                public void onSessionEnded(CastSession session, int error) {
                    onCastDisconnected();
                }

                @Override public void onSessionStarting(CastSession session) {}
                @Override public void onSessionStartFailed(CastSession session, int error) {}
                @Override public void onSessionEnding(CastSession session) {}
                @Override public void onSessionResuming(CastSession session, String sessionId) {}
                @Override public void onSessionResumeFailed(CastSession session, int error) {}
                @Override public void onSessionSuspended(CastSession session, int reason) {}
            };

    private final RemoteMediaClient.Callback remoteCallback =
            new RemoteMediaClient.Callback() {
                @Override
                public void onStatusUpdated() {
                    updateUi();
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        // Android 15 draws edge-to-edge: keep content clear of the
        // status bar and navigation bar.
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
                    Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });
        store = new StationStore(this);

        artwork = findViewById(R.id.player_artwork);
        headerStation = findViewById(R.id.player_station);
        songTitle = findViewById(R.id.player_song);
        songArtist = findViewById(R.id.player_artist);
        liveBadge = findViewById(R.id.live_badge);
        btnPlay = findViewById(R.id.btn_play);
        ImageButton btnPrev = findViewById(R.id.btn_prev);
        ImageButton btnNext = findViewById(R.id.btn_next);
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnStar = findViewById(R.id.btn_star);
        btnDefault = findViewById(R.id.btn_default);
        castButton = findViewById(R.id.btn_cast);

        btnBack.setOnClickListener(v -> finish());
        btnPlay.setOnClickListener(v -> togglePlay());
        btnPrev.setOnClickListener(v -> skipStation(-1));
        btnNext.setOnClickListener(v -> skipStation(1));
        btnStar.setOnClickListener(v -> toggleFavorite());
        btnDefault.setOnClickListener(v -> setAsDefault());

        try {
            castContext = CastContext.getSharedInstance(this);
            castButton.setRouteSelector(castContext.getMergedSelector());
        } catch (Exception e) {
            castContext = null;
            castButton.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        SessionToken token =
                new SessionToken(this, new android.content.ComponentName(this,
                        RadioService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                controller = controllerFuture.get();
                controller.addListener(playerListener);
                updateUi();
            } catch (Exception ignored) {
            }
        }, ContextCompat.getMainExecutor(this));

        if (castContext != null) {
            castContext.getSessionManager()
                    .addSessionManagerListener(castSessionListener, CastSession.class);
            CastSession current =
                    castContext.getSessionManager().getCurrentCastSession();
            if (current != null && current.isConnected()) {
                onCastConnected(current);
            }
        }
    }

    @Override
    protected void onStop() {
        if (castContext != null) {
            try {
                castContext.getSessionManager()
                        .removeSessionManagerListener(castSessionListener,
                                CastSession.class);
            } catch (Exception ignored) {
            }
        }
        if (controller != null) {
            controller.removeListener(playerListener);
            controller = null;
        }
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }
        super.onStop();
    }

    // ---------------- playback controls ----------------

    private void togglePlay() {
        RemoteMediaClient rc = remoteClient();
        if (casting && rc != null) {
            if (rc.isPlaying()) rc.pause();
            else rc.play();
            return;
        }
        if (controller == null) return;
        if (controller.isPlaying()) controller.pause();
        else controller.play();
    }

    private void skipStation(int delta) {
        if (casting) {
            castSkip(delta);
            return;
        }
        if (controller == null) return;
        if (delta < 0) controller.seekToPreviousMediaItem();
        else controller.seekToNextMediaItem();
    }

    // ---------------- cast ----------------

    private RemoteMediaClient remoteClient() {
        return (casting && castSession != null)
                ? castSession.getRemoteMediaClient() : null;
    }

    private void onCastConnected(CastSession session) {
        castSession = session;
        casting = true;
        lastCastKey = "";
        RemoteMediaClient rc = session.getRemoteMediaClient();
        if (rc != null) rc.registerCallback(remoteCallback);
        // Move playback to the receiver; keep the local playlist for metadata.
        if (controller != null && controller.isPlaying()) controller.pause();
        loadOnReceiver(currentStation());
        updateUi();
    }

    private void onCastDisconnected() {
        casting = false;
        if (castSession != null) {
            try {
                RemoteMediaClient rc = castSession.getRemoteMediaClient();
                if (rc != null) rc.unregisterCallback(remoteCallback);
            } catch (Exception ignored) {
            }
        }
        castSession = null;
        lastCastKey = "";
        updateUi();
    }

    /** Previous/next station while casting: load the neighbour on the receiver. */
    private void castSkip(int delta) {
        if (!casting) return;
        java.util.List<Station> visible = store.getVisibleStations();
        if (visible.isEmpty()) return;
        Station cur = currentStation();
        int idx = 0;
        if (cur != null) {
            for (int i = 0; i < visible.size(); i++) {
                if (visible.get(i).streamUrl.equals(cur.streamUrl)) {
                    idx = i;
                    break;
                }
            }
        }
        int next = (idx + delta + visible.size()) % visible.size();
        loadOnReceiver(visible.get(next));
        updateUi();
    }

    /** Loads the station (with current song metadata) on the cast receiver. */
    private void loadOnReceiver(Station station) {
        RemoteMediaClient rc = remoteClient();
        if (rc == null) return;
        String url = station != null ? station.streamUrl : null;
        if ((url == null || url.isEmpty()) && controller != null
                && controller.getCurrentMediaItem() != null) {
            url = controller.getCurrentMediaItem().mediaId;
        }
        if (url == null || url.isEmpty()) return;

        MediaMetadata md = controller != null ? controller.getMediaMetadata() : null;
        String title = md != null && md.title != null ? md.title.toString() : "";
        String artist = md != null && md.artist != null ? md.artist.toString() : "";
        String key = url + "|" + title + "|" + artist;
        if (key.equals(lastCastKey)) return; // already loaded
        lastCastKey = key;

        com.google.android.gms.cast.MediaMetadata castMeta =
                new com.google.android.gms.cast.MediaMetadata(
                        com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MUSIC_TRACK);
        castMeta.putString(
                com.google.android.gms.cast.MediaMetadata.KEY_TITLE,
                title.isEmpty() && station != null ? station.name : title);
        if (!artist.isEmpty()) {
            castMeta.putString(
                    com.google.android.gms.cast.MediaMetadata.KEY_ARTIST, artist);
        }
        if (station != null && station.logoUrl != null
                && !station.logoUrl.isEmpty()) {
            try {
                castMeta.addImage(new WebImage(Uri.parse(station.logoUrl)));
            } catch (Exception ignored) {
            }
        }
        String contentType = url.contains(".m3u8")
                ? "application/x-mpegurl" : "audio/mpeg";
        MediaInfo info = new MediaInfo.Builder(url)
                .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
                .setContentType(contentType)
                .setMetadata(castMeta)
                .build();
        try {
            rc.load(new MediaLoadRequestData.Builder()
                    .setMediaInfo(info)
                    .setAutoplay(true)
                    .build());
        } catch (Exception ignored) {
        }
    }

    // ---------------- UI ----------------

    private Station currentStation() {
        if (controller == null) return null;
        MediaItem item = controller.getCurrentMediaItem();
        return item == null ? null : store.findByUrl(item.mediaId);
    }

    private boolean isPlaying() {
        RemoteMediaClient rc = remoteClient();
        if (casting && rc != null) return rc.isPlaying();
        return controller != null && controller.isPlaying();
    }

    private void updateUi() {
        if (controller == null || controller.getCurrentMediaItem() == null) {
            headerStation.setText("Desi Radio");
            songTitle.setText("Nothing playing");
            songArtist.setText("Pick a station from the list");
            artwork.setImageResource(R.drawable.ic_radio_small);
            liveBadge.setVisibility(View.GONE);
            btnPlay.setImageResource(R.drawable.ic_play);
            btnStar.setVisibility(View.INVISIBLE);
            btnDefault.setVisibility(View.GONE);
            return;
        }
        Station s = currentStation();
        MediaMetadata md = controller.getMediaMetadata();
        String title = md.title != null ? md.title.toString() : "";
        String artist = md.artist != null ? md.artist.toString() : "";
        boolean playing = isPlaying();

        if (s != null) {
            headerStation.setText(s.name);
            Ui.loadArtwork(this, artwork, s.logoUrl, 28);
            boolean songKnown = !title.isEmpty() && !title.equals(s.name);
            songTitle.setText(songKnown ? title : s.name);
            songArtist.setText(songKnown && !artist.isEmpty()
                    && !artist.equals(s.genre) ? artist : s.genre);
            btnStar.setVisibility(View.VISIBLE);
            btnDefault.setVisibility(View.VISIBLE);
            btnStar.setImageResource(store.isFavorite(s.streamUrl)
                    ? R.drawable.ic_star : R.drawable.ic_star_outline);
            boolean isDefault = s.streamUrl.equals(store.getDefaultStreamUrl());
            btnDefault.setText(isDefault ? "\u2605 Default station" : "Set as default");
            btnDefault.setEnabled(!isDefault);
        } else {
            headerStation.setText(title);
            songTitle.setText(title);
            songArtist.setText(artist);
            btnStar.setVisibility(View.INVISIBLE);
            btnDefault.setVisibility(View.GONE);
        }
        liveBadge.setVisibility(playing ? View.VISIBLE : View.GONE);
        btnPlay.setImageResource(
                playing ? R.drawable.ic_pause : R.drawable.ic_play);

        // Keep the receiver's now-playing metadata in sync with ICY updates.
        if (casting) loadOnReceiver(s);
    }

    private void toggleFavorite() {
        Station s = currentStation();
        if (s == null) return;
        store.toggleFavorite(s.streamUrl);
        updateUi();
    }

    private void setAsDefault() {
        Station s = currentStation();
        if (s == null) return;
        store.setDefaultStreamUrl(s.streamUrl);
        updateUi();
    }
}
