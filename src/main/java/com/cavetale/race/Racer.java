package com.cavetale.race;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class Racer implements Comparable<Racer> {
    final UUID uuid;
    final String name;
    int checkpointIndex = 0;
    int checkpointDistance = 0;
    int lap = 0;
    boolean finished = false;
    long finishTime = 0;
    int finishIndex;
    int remountCooldown;

    Racer(final Player player) {
        this.uuid = player.getUniqueId();
        this.name = player.getName();
    }

    Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    @Override
    public int compareTo(Racer other) {
        if (finished && other.finished) {
            return Integer.compare(finishIndex, other.finishIndex);
        }
        if (finished) return -1;
        if (other.finished) return 1;
        int lapc = Integer.compare(other.lap, lap);
        if (lapc != 0) return lapc;
        int cp = Integer.compare(other.checkpointIndex, checkpointIndex);
        if (cp != 0) return cp;
        return Integer.compare(checkpointDistance, other.checkpointDistance);
    }
}
