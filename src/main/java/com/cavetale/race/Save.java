package com.cavetale.race;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Save {
    protected boolean event;
    protected String eventRace;
    protected Map<UUID, Integer> scores = new HashMap<>();

    public List<UUID> rankScores() {
        List<UUID> uuids = new ArrayList<>();
        uuids.addAll(scores.keySet());
        Collections.sort(uuids, (a, b) -> Integer.compare(scores.get(b), scores.get(a)));
        return uuids;
    }
}
