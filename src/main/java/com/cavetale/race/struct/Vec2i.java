package com.cavetale.race.struct;

import lombok.Value;

@Value
public final class Vec2i {
    public static final Vec2i ZERO = new Vec2i(0, 0);
    public static final Vec2i ONE = new Vec2i(1, 1);
    public final int x;
    public final int z;

    @Override
    public String toString() {
        return "" + x + "," + z;
    }
}
