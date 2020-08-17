package com.cavetale.race;

import lombok.Value;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Serializable Location.
 */
@Value
public final class Position {
    public static final Position ZERO = new Position(0, 0, 0, 0, 0);
    public final double x;
    public final double y;
    public final double z;
    public final float pitch;
    public final float yaw;

    public static Position of(final Location location) {
        return new Position(location.getX(), location.getY(), location.getZ(),
                            location.getPitch(), location.getYaw());
    }

    public Location toLocation(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }

    public String simpleString() {
        return Math.round(x) + "," + Math.round(y) + "," + Math.round(z);
    }
}
