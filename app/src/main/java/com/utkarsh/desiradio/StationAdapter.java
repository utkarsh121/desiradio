package com.utkarsh.desiradio;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/** Station list rows: logo, name, genre, live song line, star, hide on long-press. */
public class StationAdapter extends RecyclerView.Adapter<StationAdapter.ViewHolder> {

    public interface Listener {
        void onPlay(Station station);
        void onToggleFavorite(Station station);
        void onHide(Station station);
    }

    private final Context context;
    private final Listener listener;
    private final StationStore store;
    private List<Station> stations = new ArrayList<>();

    private String nowPlayingUrl;
    private String nowPlayingTitle;
    private String nowPlayingArtist;
    private boolean isPlaying;

    public StationAdapter(Context context, StationStore store, Listener listener) {
        this.context = context;
        this.store = store;
        this.listener = listener;
    }

    public void setStations(List<Station> stations) {
        this.stations = stations;
        notifyDataSetChanged();
    }

    public void setNowPlaying(String url, String title, String artist, boolean playing) {
        this.nowPlayingUrl = url;
        this.nowPlayingTitle = title;
        this.nowPlayingArtist = artist;
        this.isPlaying = playing;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_station, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Station s = stations.get(position);
        Ui.loadLogo(context, h.logo, s.logoUrl);
        h.name.setText(s.name);
        h.meta.setText(s.genre);

        boolean current = nowPlayingUrl != null && nowPlayingUrl.equals(s.streamUrl);
        if (current && nowPlayingTitle != null && !nowPlayingTitle.isEmpty()
                && !nowPlayingTitle.equals(s.name)) {
            h.song.setVisibility(View.VISIBLE);
            String line = (isPlaying ? "\u266A " : "\u275A\u275A ") + nowPlayingTitle;
            if (nowPlayingArtist != null && !nowPlayingArtist.isEmpty()
                    && !nowPlayingArtist.equals(s.genre)) {
                line += " \u2014 " + nowPlayingArtist;
            }
            h.song.setText(line);
        } else {
            h.song.setVisibility(current ? View.VISIBLE : View.GONE);
            if (current) h.song.setText(isPlaying ? "\u266A Live" : "\u275A\u275A Paused");
        }

        h.star.setImageResource(store.isFavorite(s.streamUrl)
                ? R.drawable.ic_star : R.drawable.ic_star_outline);
        h.star.setColorFilter(ContextCompat.getColor(context,
                store.isFavorite(s.streamUrl) ? R.color.saffron : R.color.muted));
        h.star.setOnClickListener(v -> {
            listener.onToggleFavorite(s);
            notifyItemChanged(h.getBindingAdapterPosition());
        });

        h.card.setOnClickListener(v -> listener.onPlay(s));
        h.card.setOnLongClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle(s.name)
                    .setItems(new CharSequence[]{"Hide station"}, (d, which) -> {
                        if (which == 0) listener.onHide(s);
                    })
                    .show();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return stations.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView logo;
        final TextView name;
        final TextView meta;
        final TextView song;
        final ImageButton star;

        ViewHolder(View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            logo = itemView.findViewById(R.id.row_logo);
            name = itemView.findViewById(R.id.row_name);
            meta = itemView.findViewById(R.id.row_meta);
            song = itemView.findViewById(R.id.row_song);
            star = itemView.findViewById(R.id.row_star);
        }
    }
}
