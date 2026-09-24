package com.utkarsh.desiradio;

import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Metadata;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.extractor.metadata.icy.IcyInfo;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.MediaSession;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Media3 playback service. Exposes Favorites / All Stations to Android Auto,
 * parses ICY stream metadata into live song title/artist.
 */
public class RadioService extends MediaLibraryService {

    private ExoPlayer player;
    private MediaLibraryService.MediaLibrarySession session;
    private StationStore store;
    private String lastIcyTitle = "";

    /** Builds a playable MediaItem for a station (used by UI and Auto alike). */
    public static MediaItem toMediaItem(Station s) {
        MediaMetadata.Builder mb = new MediaMetadata.Builder()
                .setTitle(s.name)
                .setArtist(s.genre)
                .setIsPlayable(true);
        if (s.logoUrl != null && !s.logoUrl.isEmpty()
                && !s.logoUrl.equals("null")
                && (s.logoUrl.startsWith("http://")
                    || s.logoUrl.startsWith("https://"))) {
            mb.setArtworkUri(Uri.parse(s.logoUrl));
        }
        return new MediaItem.Builder()
                .setMediaId(s.streamUrl)
                .setUri(s.streamUrl)
                .setMediaMetadata(mb.build())
                .build();
    }

    private static MediaItem folder(String id, String title) {
        return new MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(title)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build())
                .build();
    }

    private final MediaLibraryService.MediaLibrarySession.Callback callback = new MediaLibraryService.MediaLibrarySession.Callback() {
        @Override
        public ListenableFuture<LibraryResult<MediaItem>> onGetLibraryRoot(
                MediaLibrarySession session,
                MediaSession.ControllerInfo browser,
                @Nullable MediaLibraryService.LibraryParams params) {
            try {
                MediaItem root = new MediaItem.Builder()
                        .setMediaId("root")
                        .setMediaMetadata(new MediaMetadata.Builder()
                                .setTitle("Desi Radio")
                                .setIsBrowsable(true)
                                .setIsPlayable(false)
                                .build())
                        .build();
                return Futures.immediateFuture(LibraryResult.ofItem(root, params));
            } catch (Exception e) {
                return Futures.immediateFuture(
                        LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN));
            }
        }

        @Override
        public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildren(
                MediaLibrarySession session,
                MediaSession.ControllerInfo browser,
                String parentId,
                int page,
                int pageSize,
                @Nullable MediaLibraryService.LibraryParams params) {
            try {
                List<MediaItem> all = new ArrayList<>();
                if ("root".equals(parentId)) {
                    all.add(folder("favorites", "Favorites"));
                    all.add(folder("all", "All Stations"));
                } else if ("favorites".equals(parentId)) {
                    List<Station> favs = store.getFavoriteStations();
                    if (favs.isEmpty()) favs = store.getVisibleStations();
                    for (Station s : favs) {
                        try {
                            all.add(toMediaItem(s));
                        } catch (Exception e) {
                            CrashLog.log(RadioService.this, "browse:favorites:" + s.name, e);
                        }
                    }
                } else if ("all".equals(parentId)) {
                    for (Station s : store.getVisibleStations()) {
                        try {
                            all.add(toMediaItem(s));
                        } catch (Exception e) {
                            CrashLog.log(RadioService.this, "browse:all:" + s.name, e);
                        }
                    }
                }
                // Media3 rejects the whole result if we return more than
                // pageSize items (IllegalStateException: Invalid size=..),
                // which Android Auto then shows as "no items".
                List<MediaItem> paged = paginate(all, page, pageSize);
                // Log browse for diagnostics (visible in files/crashes/ if needed).
                // Use ImmutableList explicitly: the framework validates size
                // against pageSize, and a mutable subList view has caused
                // subtle issues on some head units.
                ImmutableList<MediaItem> result = ImmutableList.copyOf(paged);
                return Futures.immediateFuture(LibraryResult.ofItemList(result, params));
            } catch (Exception e) {
                // Never let a browse request kill the service (and the car UI).
                CrashLog.log(RadioService.this, "browse:" + parentId, e);
                return Futures.immediateFuture(LibraryResult.ofItemList(
                        ImmutableList.of(), params));
            }
        }

        /** Returns at most pageSize items starting at page * pageSize. */
        private List<MediaItem> paginate(List<MediaItem> all, int page, int pageSize) {
            if (page < 0 || pageSize <= 0) return Collections.emptyList();
            if (pageSize == Integer.MAX_VALUE) return all;
            long from = (long) page * (long) pageSize;
            if (from >= all.size()) return Collections.emptyList();
            int to = (int) Math.min(all.size(), from + pageSize);
            return all.subList((int) from, to);
        }

        @Override
        public ListenableFuture<LibraryResult<MediaItem>> onGetItem(
                MediaLibrarySession session,
                MediaSession.ControllerInfo browser,
                String mediaId) {
            try {
                // Support direct item lookup (some cars/phones request this).
                if ("root".equals(mediaId)) {
                    MediaItem root = new MediaItem.Builder()
                            .setMediaId("root")
                            .setMediaMetadata(new MediaMetadata.Builder()
                                    .setTitle("Desi Radio")
                                    .setIsBrowsable(true)
                                    .setIsPlayable(false)
                                    .build())
                            .build();
                    return Futures.immediateFuture(LibraryResult.ofItem(root, null));
                }
                if ("favorites".equals(mediaId)) {
                    return Futures.immediateFuture(
                            LibraryResult.ofItem(folder("favorites", "Favorites"), null));
                }
                if ("all".equals(mediaId)) {
                    return Futures.immediateFuture(
                            LibraryResult.ofItem(folder("all", "All Stations"), null));
                }
                Station s = store.findByUrl(mediaId);
                if (s != null) {
                    return Futures.immediateFuture(
                            LibraryResult.ofItem(toMediaItem(s), null));
                }
                return Futures.immediateFuture(
                        LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
            } catch (Exception e) {
                CrashLog.log(RadioService.this, "onGetItem:" + mediaId, e);
                return Futures.immediateFuture(
                        LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN));
            }
        }

        @Override
        public ListenableFuture<List<MediaItem>> onAddMediaItems(
                MediaSession session,
                MediaSession.ControllerInfo controller,
                List<MediaItem> mediaItems) {
            try {
                // Radio plays one live stream at a time: resolve the tapped
                // station to a single playable item. No playlist, no queue.
                List<MediaItem> out = new ArrayList<>();
                for (MediaItem mi : mediaItems) {
                    if (mi == null) continue;
                    Station s = store.findByUrl(mi.mediaId);
                    if (s != null) {
                        out.add(toMediaItem(s));
                    } else if (mi.localConfiguration != null
                            && mi.localConfiguration.uri != null) {
                        // Only pass through items that can actually play.
                        out.add(mi);
                    }
                }
                return Futures.immediateFuture(out);
            } catch (Exception e) {
                CrashLog.log(RadioService.this, "onAddMediaItems", e);
                return Futures.immediateFuture(new ArrayList<>());
            }
        }

    };

    @Override
    public void onCreate() {
        super.onCreate();
        store = new StationStore(this);

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("DesiRadio/1.0 (Android)")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000);

        DefaultMediaSourceFactory mediaSourceFactory =
                new DefaultMediaSourceFactory(this).setDataSourceFactory(httpFactory);

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                .setAudioAttributes(AudioAttributes.DEFAULT, true)
                .setHandleAudioBecomingNoisy(true)
                .build();

        player.addListener(new Player.Listener() {
            @Override
            public void onMetadata(Metadata metadata) {
                for (int i = 0; i < metadata.length(); i++) {
                    Metadata.Entry entry = metadata.get(i);
                    if (entry instanceof IcyInfo) {
                        IcyInfo icy = (IcyInfo) entry;
                        handleIcy(icy.title, icy.url);
                    }
                }
            }
        });

        session = new MediaLibraryService.MediaLibrarySession.Builder(this, player, callback).build();
    }

    /** Applies ICY stream metadata: live song title/artist, plus stream-provided
     *  artwork when the feed actually sends an image URL (most don't). */
    private void handleIcy(String title, String icyUrl) {
        if (title == null) return;
        title = title.trim();
        if (title.isEmpty() || title.equals(lastIcyTitle)) return;
        lastIcyTitle = title;

        String artist = "";
        String song = title;
        int sep = title.indexOf(" - ");
        if (sep > 0) {
            artist = title.substring(0, sep).trim();
            song = title.substring(sep + 3).trim();
        }

        try {
            MediaItem current = player.getCurrentMediaItem();
            if (current == null) return;
            MediaMetadata old = current.mediaMetadata;
            CharSequence newArtist = artist.isEmpty() ? old.artist : artist;
            MediaMetadata.Builder mb = old.buildUpon()
                    .setTitle(song)
                    .setArtist(newArtist);
            if (looksLikeImageUrl(icyUrl)) {
                mb.setArtworkUri(Uri.parse(icyUrl));
            }
            MediaItem updated = current.buildUpon()
                    .setMediaMetadata(mb.build())
                    .build();
            int index = player.getCurrentMediaItemIndex();
            if (index >= 0 && player.getPlaybackState() != Player.STATE_IDLE) {
                player.replaceMediaItem(index, updated);
            }
        } catch (Exception e) {
            // Metadata refresh must never interrupt playback or crash the service.
            CrashLog.log(this, "handleIcy", e);
        }
    }

    /** Conservative check: only treat the ICY StreamUrl as artwork when it
     *  actually points at an image, never a station homepage or junk. */
    private static boolean looksLikeImageUrl(String url) {
        return url != null
                && url.matches("(?i)https?://\\S+\\.(jpg|jpeg|png|webp|gif)(\\?\\S*)?");
    }

    @Override
    public MediaLibrarySession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    @Override
    public void onDestroy() {
        if (session != null) {
            session.release();
            session = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    /**
     * Swiping the app away from recents kills playback. A media session
     * service otherwise survives the swipe and keeps the radio playing.
     */
    @Override
    public void onTaskRemoved(android.content.Intent rootIntent) {
        try {
            if (player != null) {
                player.stop();
                player.clearMediaItems();
            }
            if (session != null) {
                session.release();
                session = null;
            }
        } catch (Exception e) {
            CrashLog.log(this, "onTaskRemoved", e);
        }
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }
}
