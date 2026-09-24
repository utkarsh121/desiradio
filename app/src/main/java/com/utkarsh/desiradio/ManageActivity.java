package com.utkarsh.desiradio;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

/** Pick-and-choose: show/hide stations, pick the default station, autoplay toggle. */
public class ManageActivity extends AppCompatActivity {

    private StationStore store;
    private ManageAdapter adapter;
    private TextView defaultLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage);
        store = new StationStore(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Manage stations");
        toolbar.setNavigationOnClickListener(v -> finish());

        defaultLabel = findViewById(R.id.default_label);
        MaterialButton clearDefault = findViewById(R.id.clear_default);
        clearDefault.setOnClickListener(v -> {
            store.setDefaultStreamUrl(null);
            refresh();
        });

        SwitchMaterial autoplay = findViewById(R.id.autoplay_switch);
        autoplay.setChecked(store.isAutoplay());
        autoplay.setOnCheckedChangeListener((buttonView, isChecked) ->
                store.setAutoplay(isChecked));

        RecyclerView recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ManageAdapter(this, store, new ManageAdapter.Listener() {
            @Override
            public void onVisibilityChanged(Station station, boolean visible) {
                store.setHidden(station.streamUrl, !visible);
            }

            @Override
            public void onDefaultPicked(Station station) {
                store.setDefaultStreamUrl(station.streamUrl);
                if (store.isHidden(station.streamUrl)) {
                    store.setHidden(station.streamUrl, false);
                }
                refresh();
            }
        });
        recycler.setAdapter(adapter);
        refresh();
    }

    private void refresh() {
        adapter.setStations(store.getAllStations());
        Station d = store.findByUrl(store.getDefaultStreamUrl());
        defaultLabel.setText(d == null
                ? "No default station"
                : "Default: " + d.name);
        defaultLabel.setVisibility(View.VISIBLE);
    }
}
