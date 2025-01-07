package com.cavetale.race;

import com.cavetale.race.sql.SQLPlayerMapRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
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

    public SQLPlayerMapRecord delete(String path, UUID player) {
        final MapRecords records = mapRecords.get(path);
        if (records == null) return null;
        return records.delete(player);
    }

    public List<SQLPlayerMapRecord> rank(String path) {
        final MapRecords records = mapRecords.get(path);
        if (records == null) return List.of();
        return records.rank();
    }

    public InsertResult set(String path, UUID player, long time) {
        return mapRecords.computeIfAbsent(path, MapRecords::new).set(player, time);
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

        public SQLPlayerMapRecord delete(UUID player) {
            SQLPlayerMapRecord result = null;
            for (var it : rows) {
                if (player.equals(it.getPlayer())) {
                    result = it;
                    break;
                }
            }
            if (result == null) return null;
            rows.remove(result);
            plugin.getDatabase().deleteAsync(result, null);
            return result;
        }

        public List<SQLPlayerMapRecord> rank() {
            Collections.sort(rows, Comparator.comparing(SQLPlayerMapRecord::getTime));
            long currentTime = -1L;
            int currentRank = 0;
            for (SQLPlayerMapRecord row : rows) {
                if (currentTime != row.getTime()) {
                    currentTime = row.getTime();
                    currentRank += 1;
                }
                row.setRank(currentRank);
            }
            return rows;
        }

        public InsertResult set(UUID player, long time) {
            SQLPlayerMapRecord row = find(player);
            final InsertResult result = new InsertResult();
            if (row == null) {
                row = new SQLPlayerMapRecord(mapPath, player, time);
                plugin.getDatabase().insertAsync(row, null);
                rows.add(row);
                result.setRow(row);
            } else {
                result.setRow(row);
                if (row.getTime() <= time) return result;
                rank();
                result.setOldRank(row.getRank());
                row.setTime(time);
                row.setNow();
                plugin.getDatabase().updateAsync(row, null, "player", "time", "date");
            }
            rank();
            result.setNewRank(result.getRow().getRank());
            return result;
        }

        public List<SQLPlayerMapRecord> clear() {
            if (rows.isEmpty()) return List.of();
            final List<SQLPlayerMapRecord> oldRows = List.copyOf(rows);
            plugin.getDatabase().deleteAsync(oldRows, null);
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

    @Data
    public final class InsertResult {
        private SQLPlayerMapRecord row;
        private int oldRank;
        private int newRank;

        public boolean hasRow() {
            return row != null;
        }

        public boolean isWorldRecord() {
            return oldRank != 1 && newRank == 1;
        }

        public boolean isPersonalBest() {
            return newRank != 0
                && (oldRank == 0 || oldRank > newRank);
        }
    }
}
