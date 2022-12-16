package com.cavetale.race;

import com.cavetale.core.struct.Vec3i;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Entity;

/**
 * Runtime class of a patrolling enemy on the race track.
 */
@RequiredArgsConstructor
public final class Bogey {
    protected final Vec3i where;
    protected int cooldown = 0;
    protected Entity entity;
    protected boolean backwards;
}
