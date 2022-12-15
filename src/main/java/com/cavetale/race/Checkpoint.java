package com.cavetale.race;

import com.cavetale.core.struct.Cuboid;
import java.util.List;

public final class Checkpoint {
    protected int ax;
    protected int ay;
    protected int az;
    protected int bx;
    protected int by;
    protected int bz;
    protected Cuboid area;
    protected List<Cuboid> alt;

    @Override
    public String toString() {
        return area.toString();
    }

    public Checkpoint(final Cuboid area) {
        this.area = area;
    }

    public boolean fix() {
        if (area == null) {
            area = new Cuboid(ax, ay, az, bx, by, bz);
            ax = 0;
            ay = 0;
            az = 0;
            bx = 0;
            by = 0;
            bz = 0;
            return true;
        }
        return false;
    }
}
