package com.cavetale.race;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Persistenct type, maintained by Race.
 */
@RequiredArgsConstructor
public final class Racer implements Comparable<Racer> {
    protected final UUID uuid;
    protected final String name;
    protected int checkpointIndex = 0;
    protected int checkpointDistance = 0;
    protected int checkpointDistanceIncreaseTicks = 0;
    protected int lap = 0;
    protected boolean finished = false;
    protected long finishTime = 0;
    protected int finishIndex;
    protected int remountCooldown;
    protected Vec3i startVector;
    protected boolean racing = false;
    protected int rank = 0;
    protected long goodyCooldown = 0L;
    protected int coins = 0;
    protected int invincibleTicks = 0;

    Racer(final Player player) {
        this.uuid = player.getUniqueId();
        this.name = player.getName();
    }

    Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    public boolean isOnline() {
        return getPlayer() != null;
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

    public boolean isInvincible() {
        return invincibleTicks > 0;
    }
}
