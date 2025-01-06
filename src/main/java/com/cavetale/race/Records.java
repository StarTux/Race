package com.cavetale.race;

import com.cavetale.race.sql.SQLPlayerMapRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class Records {
    private final RacePlugin plugin;
    private final Map<String, MapRecords> mapRecords = new HashMap<>();

    public void enable() {
        load();
    }

    public SQLPlayerMapRecord find(String path, UUID player) {
        final MapRecords records = mapRecords.get(path);
        if (records == null) return null;
        return records.find(player);
    }

    public List<SQLPlayerMapRecord> rank(String path) {
        final MapRecords records = mapRecords.get(path);
        if (records == null) return List.of();
        return records.rank();
    }

    public void set(String path, UUID player, long time) {
        mapRecords.computeIfAbsent(path, MapRecords::new).set(player, time);
    }

    public List<SQLPlayerMapRecord> clear(String path) {
        final MapRecords records = mapRecords.get(path);
        if (records == null) return null;
        return records.clear();
    }

    @RequiredArgsConstructor
    private final class MapRecords {
        private final String mapPath;
        private final List<SQLPlayerMapRecord> rows = new ArrayList<>();

        public SQLPlayerMapRecord find(UUID player) {
            for (var it : rows) {
                if (player.equals(it.getPlayer())) return it;
            }
            return null;
        }

        public List<SQLPlayerMapRecord> rank() {
            Collections.sort(rows, Comparator.comparing(SQLPlayerMapRecord::getTime));
            return rows;
        }

        public void set(UUID player, long time) {
            SQLPlayerMapRecord row = find(player);
            if (row == null) {
                row = new SQLPlayerMapRecord(mapPath, player, time);
                plugin.getDatabase().insertAsync(row, null);
                rows.add(row);
            } else {
                if (row.getTime() <= time) return;
                row.setTime(time);
                row.setNow();
                plugin.getDatabase().updateAsync(row, null, "player", "time", "date");
            }
        }

        public List<SQLPlayerMapRecord> clear() {
            if (rows.isEmpty()) return List.of();
            final List<SQLPlayerMapRecord> oldRows = List.copyOf(rows);
            plugin.getDatabase().deleteAsync(rows, null);
            rows.clear();
            return oldRows;
        }
    }

    private void load() {
        mapRecords.clear();
        for (SQLPlayerMapRecord row : plugin.getDatabase().find(SQLPlayerMapRecord.class).findList()) {
            final MapRecords records = mapRecords.computeIfAbsent(row.getPath(), MapRecords::new);
            records.rows.add(row);
        }
    }
}
