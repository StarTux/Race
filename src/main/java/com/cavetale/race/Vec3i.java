package com.cavetale.race;

import lombok.Value;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

@Value
public final class Vec3i {
    public static final Vec3i ZERO = new Vec3i(0, 0, 0);
    public static final Vec3i ONE = new Vec3i(1, 1, 1);
    public final int x;
    public final int y;
    public final int z;

    public static Vec3i of(Block block) {
        return new Vec3i(block.getX(), block.getY(), block.getZ());
    }

    public static Vec3i of(Location loc) {
        return new Vec3i(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public Block toBlock(World world) {
        return world.getBlockAt(x, y, z);
    }

    public Location toLocation(World world) {
        return toBlock(world).getLocation().add(0.5, 0, 0.5);
    }

    public Vec3i add(int dx, int dy, int dz) {
        return new Vec3i(x + dx, y + dy, z + dz);
    }

    public Vec3i subtract(Vec3i o) {
        return new Vec3i(x - o.x, y - o.y, z - o.z);
    }

    public Vec3i multiply(int mul) {
        return new Vec3i(x * mul, y * mul, z * mul);
    }

    public int distanceSquared(Vec3i other) {
        int dx = other.x - x;
        int dy = other.y - y;
        int dz = other.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public int simpleDistance(Vec3i other) {
        return Math.max(Math.abs(other.y - y),
                        Math.max(Math.abs(other.x - x),
                                 Math.abs(other.z - z)));
    }

    @Override
    public String toString() {
        return "" + x + "," + y + "," + z;
    }

    public boolean contains(Location loc) {
        return x == loc.getBlockX() && y == loc.getBlockY() && z == loc.getBlockZ();
    }
}
