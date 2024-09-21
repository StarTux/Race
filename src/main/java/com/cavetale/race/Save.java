package com.cavetale.race;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public final class Save {
    private boolean event;
    private String eventRaceWorld;
    private Map<UUID, Integer> scores = new HashMap<>();

    public List<UUID> rankScores() {
        List<UUID> uuids = new ArrayList<>();
        uuids.addAll(scores.keySet());
        Collections.sort(uuids, (a, b) -> Integer.compare(scores.get(b), scores.get(a)));
        return uuids;
    }
}
