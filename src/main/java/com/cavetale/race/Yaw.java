package com.cavetale.race;

import com.cavetale.core.struct.Cuboid;
import com.cavetale.core.struct.Vec3i;
import static java.lang.Math.abs;

public final class Yaw {
    public static final float SOUTH = 0f;
    public static final float WEST = 90f;
    public static final float NORTH = 180f;
    public static final float EAST = 270f;

    /**
     * Yaw of a start vector facing the first checkpoint.
     */
    public static float yaw(Vec3i vector, Cuboid cuboid) {
        Vec3i target = cuboid.getCenter();
        if (vector.x >= cuboid.getAx() && vector.x <= cuboid.getBx()) {
            return vector.z < target.z
                ? SOUTH
                : NORTH;
        } else if (vector.z >= cuboid.getAz() && vector.z <= cuboid.getBz()) {
            return vector.x < target.x
                ? EAST
                : WEST;
        } else {
            return yaw(target.subtract(vector));
        }
    }

    /**
     * Yaw of a directional vector.
     */
    public static float yaw(Vec3i dir) {
        if (abs(dir.x) > abs(dir.z)) {
            return dir.x > 0
                ? EAST
                : WEST;
        } else {
            return dir.z > 0
                ? SOUTH
                : NORTH;
        }
    }

    private Yaw() { }
}
