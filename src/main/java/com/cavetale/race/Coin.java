package com.cavetale.race;

import com.cavetale.core.struct.Vec3i;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Entity;

/**
 * Runtime class of a coin on the race track.
 */
@RequiredArgsConstructor
public final class Coin {
    protected final Vec3i where;
    protected final Set<UUID> collectedBy = new HashSet<>();
    protected Entity entity;
}
