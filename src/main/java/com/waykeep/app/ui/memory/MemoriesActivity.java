package com.waykeep.app.ui.memory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.waykeep.app.R;
import com.waykeep.app.data.ContentProviderUtils;
import com.waykeep.app.data.RunMemoryStore;
import com.waykeep.app.data.TrackListIterator;
import com.waykeep.app.data.models.DistanceFormatter;
import com.waykeep.app.data.models.RunMemory;
import com.waykeep.app.databinding.ActivityMemoriesBinding;
import com.waykeep.app.settings.PreferencesUtils;
import com.waykeep.app.ui.trackmap.TrackThumbnailLoader;
import com.waykeep.app.util.FileUtils;
import com.waykeep.app.util.IntentUtils;
import com.waykeep.app.util.StringUtils;
import com.waykeep.app.TrackRecordedActivity;

/**
 * Private timeline of how runs felt: mood sticker, optional photo and the route,
 * newest first. Tapping a memory opens the track.
 */
public class MemoriesActivity extends AppCompatActivity {

    private ActivityMemoriesBinding viewBinding;
    private TrackThumbnailLoader thumbnailLoader;

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBinding = ActivityMemoriesBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());

        thumbnailLoader = new TrackThumbnailLoader(this);

        viewBinding.memoriesList.setLayoutManager(new LinearLayoutManager(this));
        loadMemories();
    }

    private void loadMemories() {
        RunMemoryStore memoryStore = new RunMemoryStore(this);
        memoryStore.pruneOrphans();
        Map<Long, RunMemory> memories = memoryStore.getAll();

        List<TrackListIterator.Item> tracks = new ArrayList<>();
        try (TrackListIterator iterator = new ContentProviderUtils(this).searchTracks(null)) {
            while (iterator.hasNext()) {
                tracks.add(iterator.next());
            }
        }

        List<TrackListIterator.Item> withMemories = new ArrayList<>(tracks.stream()
                .filter(item -> memories.containsKey(item.id().id()))
                .toList());
        Collections.reverse(withMemories); // newest first

        viewBinding.memoriesEmpty.setVisibility(withMemories.isEmpty() ? View.VISIBLE : View.GONE);
        viewBinding.memoriesList.setAdapter(new MemoriesAdapter(withMemories, memories));
    }

    private class MemoriesAdapter extends RecyclerView.Adapter<MemoryViewHolder> {

        private final List<TrackListIterator.Item> items;
        private final Map<Long, RunMemory> memories;

        MemoriesAdapter(List<TrackListIterator.Item> items, Map<Long, RunMemory> memories) {
            this.items = items;
            this.memories = memories;
        }

        @NonNull
        @Override
        public MemoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new MemoryViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.memory_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull MemoryViewHolder holder, int position) {
            TrackListIterator.Item item = items.get(position);
            RunMemory memory = memories.get(item.id().id());
            Context context = holder.itemView.getContext();

            holder.name.setText(item.name());
            holder.detail.setText(StringUtils.formatDateTimeWithOffset(item.startTime().atOffset(item.zoneOffset())) + " · "
                    + DistanceFormatter.Builder().setUnit(PreferencesUtils.getUnitSystem()).build(context)
                    .formatDistance(item.totalDistance()));

            holder.mood.setImageResource(moodDrawable(memory != null ? memory.mood() : -1));

            List<String> photoNames = memory != null ? memory.photoNames() : List.of();
            if (!photoNames.isEmpty()) {
                File photo = new File(FileUtils.getPhotoDir(context, item.id()), photoNames.get(0));
                if (photo.isFile()) {
                    holder.photo.setImageURI(android.net.Uri.fromFile(photo));
                    holder.photo.setVisibility(View.VISIBLE);
                } else {
                    holder.photo.setVisibility(View.GONE);
                }
            } else {
                holder.photo.setVisibility(View.GONE);
            }
            if (photoNames.size() > 1) {
                holder.count.setVisibility(View.VISIBLE);
                holder.count.setText(context.getString(R.string.memory_photo_count, photoNames.size()));
            } else {
                holder.count.setVisibility(View.GONE);
            }

            int side = Math.round(72 * context.getResources().getDisplayMetrics().density);
            thumbnailLoader.load(item.id(), side, side, bitmap -> {
                holder.route.setImageBitmap(bitmap);
                holder.route.setVisibility(bitmap != null ? View.VISIBLE : View.GONE);
            });

            holder.itemView.setOnClickListener(v -> startActivity(
                    IntentUtils.newIntent(MemoriesActivity.this, TrackRecordedActivity.class)
                            .putExtra(TrackRecordedActivity.EXTRA_TRACK_ID, item.id())));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static int moodDrawable(int mood) {
        return switch (mood) {
            case 0 -> R.drawable.mood_sticker_0;
            case 1 -> R.drawable.mood_sticker_1;
            case 2 -> R.drawable.mood_sticker_2;
            case 3 -> R.drawable.mood_sticker_3;
            case 4 -> R.drawable.mood_sticker_4;
            default -> R.drawable.mood_sticker_2;
        };
    }

    private static class MemoryViewHolder extends RecyclerView.ViewHolder {

        final ImageView mood;
        final ImageView photo;
        final TextView name;
        final TextView detail;
        final TextView count;
        final ImageView route;

        MemoryViewHolder(@NonNull View itemView) {
            super(itemView);
            mood = itemView.findViewById(R.id.memory_item_mood);
            photo = itemView.findViewById(R.id.memory_item_photo);
            name = itemView.findViewById(R.id.memory_item_name);
            detail = itemView.findViewById(R.id.memory_item_detail);
            count = itemView.findViewById(R.id.memory_item_count);
            route = itemView.findViewById(R.id.memory_item_route);
        }
    }
}
