package com.utkarsh.desiradio;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;

/** Manage screen rows: show/hide switch + default-station radio button. */
public class ManageAdapter extends RecyclerView.Adapter<ManageAdapter.ViewHolder> {

    public interface Listener {
        void onVisibilityChanged(Station station, boolean visible);
        void onDefaultPicked(Station station);
    }

    private final Context context;
    private final Listener listener;
    private final StationStore store;
    private List<Station> stations = new ArrayList<>();

    public ManageAdapter(Context context, StationStore store, Listener listener) {
        this.context = context;
        this.store = store;
        this.listener = listener;
    }

    public void setStations(List<Station> stations) {
        this.stations = stations;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_manage, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Station s = stations.get(position);
        Ui.loadLogo(context, h.logo, s.logoUrl);
        h.name.setText(s.name);
        h.meta.setText(s.genre);

        h.visibleSwitch.setOnCheckedChangeListener(null);
        h.visibleSwitch.setChecked(!store.isHidden(s.streamUrl));
        h.visibleSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                listener.onVisibilityChanged(s, isChecked));

        h.defaultRadio.setOnCheckedChangeListener(null);
        h.defaultRadio.setChecked(s.streamUrl.equals(store.getDefaultStreamUrl()));
        h.defaultRadio.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                listener.onDefaultPicked(s);
                notifyDataSetChanged();
            }
        });
        h.itemView.setOnClickListener(v -> {
            listener.onDefaultPicked(s);
            notifyDataSetChanged();
        });
    }

    @Override
    public int getItemCount() {
        return stations.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView logo;
        final TextView name;
        final TextView meta;
        final SwitchMaterial visibleSwitch;
        final RadioButton defaultRadio;

        ViewHolder(View itemView) {
            super(itemView);
            logo = itemView.findViewById(R.id.row_logo);
            name = itemView.findViewById(R.id.row_name);
            meta = itemView.findViewById(R.id.row_meta);
            visibleSwitch = itemView.findViewById(R.id.row_visible);
            defaultRadio = itemView.findViewById(R.id.row_default);
        }
    }
}
