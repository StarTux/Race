package com.cavetale.race;

import com.cavetale.core.struct.Vec3i;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Entity;

/**
 * Runtime class of a goody on the race track.
 */
@RequiredArgsConstructor
public final class Goody {
    protected final Vec3i where;
    protected int cooldown = 0;
    protected Entity entity;
    protected float rotation;
}
