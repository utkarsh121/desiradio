package com.utkarsh.desiradio;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Home screen: searchable/filterable station list, favorites, mini player. */
public class MainActivity extends AppCompatActivity {

    private StationStore store;
    private StationAdapter adapter;
    private RecyclerView recycler;
    private ChipGroup chipGroup;
    private MaterialCardView miniPlayer;
    private ImageView miniLogo;
    private TextView miniStation;
    private TextView miniSong;
    private ImageButton miniPlay;

    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;

    private String filterGenre = "All";
    private String query = "";

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onMediaItemTransition(MediaItem mediaItem, int reason) {
            updatePlaybackUi();
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            updatePlaybackUi();
        }

        @Override
        public void onMediaMetadataChanged(MediaMetadata mediaMetadata) {
            updatePlaybackUi();
        }

        @Override
        public void onPlayerError(androidx.media3.common.PlaybackException error) {
            Toast.makeText(MainActivity.this,
                    "Couldn't play this station — try another one",
                    Toast.LENGTH_SHORT).show();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Desi Radio");
        toolbar.setSubtitle("Live Hindi radio from India");
        toolbar.inflateMenu(R.menu.main_menu);
        toolbar.setOnMenuItemClickListener(this::onMenuItem);

        recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StationAdapter(this, store, new StationAdapter.Listener() {
            @Override
            public void onPlay(Station station) {
                playStation(station);
                startActivity(new Intent(MainActivity.this, PlayerActivity.class));
            }

            @Override
            public void onToggleFavorite(Station station) {
                store.toggleFavorite(station.streamUrl);
            }

            @Override
            public void onHide(Station station) {
                store.setHidden(station.streamUrl, true);
                refreshList();
                Toast.makeText(MainActivity.this,
                        station.name + " hidden — restore it in Manage",
                        Toast.LENGTH_SHORT).show();
            }
        });
        recycler.setAdapter(adapter);

        chipGroup = findViewById(R.id.chip_group);
        SearchView searchView = findViewById(R.id.search_view);
        searchView.setQueryHint(getString(R.string.search_hint));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String q) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String q) {
                query = q == null ? "" : q.trim().toLowerCase();
                refreshList();
                return true;
            }
        });

        miniPlayer = findViewById(R.id.mini_player);
        miniLogo = findViewById(R.id.mini_logo);
        miniStation = findViewById(R.id.mini_station);
        miniSong = findViewById(R.id.mini_song);
        miniPlay = findViewById(R.id.mini_play);
        miniPlay.setOnClickListener(v -> {
            if (controller == null) return;
            if (controller.isPlaying()) controller.pause();
            else controller.play();
        });
        miniPlayer.setOnClickListener(v ->
                startActivity(new Intent(this, PlayerActivity.class)));
        miniPlayer.setVisibility(View.GONE);

        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    private boolean onMenuItem(MenuItem item) {
        if (item.getItemId() == R.id.action_manage) {
            startActivity(new Intent(this, ManageActivity.class));
            return true;
        }
        return false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        SessionToken token = new SessionToken(this, new ComponentName(this, RadioService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                controller = controllerFuture.get();
                controller.addListener(playerListener);
                refreshChips();
                refreshList();
                updatePlaybackUi();
                maybeAutoplay();
            } catch (Exception e) {
                Toast.makeText(this, "Couldn't connect to playback service",
                        Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    protected void onStop() {
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

    @Override
    protected void onResume() {
        super.onResume();
        refreshChips();
        refreshList();
    }

    private void maybeAutoplay() {
        if (controller == null || !store.isAutoplay()) return;
        if (controller.getMediaItemCount() > 0) return;
        Station d = store.findByUrl(store.getDefaultStreamUrl());
        if (d != null && !store.isHidden(d.streamUrl)) {
            playStation(d);
        }
    }

    private void playStation(Station s) {
        if (controller == null) {
            Toast.makeText(this, "Connecting… try again in a second",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        // Keep the visible stations as the playlist so next/previous
        // cycles across channels (phone UI, notification, lock screen, car).
        List<Station> visible = store.getVisibleStations();
        List<MediaItem> items = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < visible.size(); i++) {
            Station st = visible.get(i);
            items.add(RadioService.toMediaItem(st));
            if (st.streamUrl.equals(s.streamUrl)) start = i;
        }
        controller.setMediaItems(items, start, 0);
        controller.prepare();
        controller.play();
    }

    private void refreshChips() {
        chipGroup.removeAllViews();
        Set<String> genres = new LinkedHashSet<>();
        for (Station s : store.getVisibleStations()) {
            if (s.genre != null && !s.genre.isEmpty()) genres.add(s.genre);
        }
        addChip(getString(R.string.all));
        for (String g : genres) addChip(g);
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            Chip c = (Chip) chipGroup.getChildAt(i);
            if (c.getText().toString().equals(filterGenre)) {
                chipGroup.check(c.getId());
                return;
            }
        }
        chipGroup.check(chipGroup.getChildAt(0).getId());
    }

    private void addChip(String text) {
        Chip chip = new Chip(this);
        chip.setText(text);
        chip.setCheckable(true);
        chip.setId(View.generateViewId());
        chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                filterGenre = text;
                refreshList();
            }
        });
        chipGroup.addView(chip);
    }

    private void refreshList() {
        List<Station> out = new ArrayList<>();
        for (Station s : store.getVisibleStations()) {
            boolean genreOk = "All".equals(filterGenre) || filterGenre.equals(s.genre);
            boolean queryOk = query.isEmpty()
                    || s.name.toLowerCase().contains(query)
                    || (s.genre != null && s.genre.toLowerCase().contains(query));
            if (genreOk && queryOk) out.add(s);
        }
        adapter.setStations(out);
    }

    private void updatePlaybackUi() {
        if (controller == null) return;
        MediaItem item = controller.getCurrentMediaItem();
        if (item == null) {
            miniPlayer.setVisibility(View.GONE);
            adapter.setNowPlaying(null, null, null, false);
            return;
        }
        Station s = store.findByUrl(item.mediaId);
        MediaMetadata md = controller.getMediaMetadata();
        String title = md.title != null ? md.title.toString() : "";
        String artist = md.artist != null ? md.artist.toString() : "";
        boolean playing = controller.isPlaying();

        miniPlayer.setVisibility(View.VISIBLE);
        miniStation.setText(s != null ? s.name : title);
        String songLine = title;
        if (!artist.isEmpty() && !artist.equals(s != null ? s.genre : "")) {
            songLine += " — " + artist;
        }
        miniSong.setText(songLine.isEmpty() ? (playing ? "Live" : "Paused") : songLine);
        miniSong.setVisibility(View.VISIBLE);
        miniPlay.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        Ui.loadLogo(this, miniLogo, s != null ? s.logoUrl : null);

        adapter.setNowPlaying(item.mediaId, title, artist, playing);
    }
}
