package com.cavetale.race;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public final class Tag {
    protected String worldName = "";
    protected RaceType type = RaceType.WALK;
    protected Cuboid area = Cuboid.ZERO;
    protected Position spawnLocation = Position.ZERO;
    protected List<Vec3i> startVectors = new ArrayList<>();
    protected List<Vec3i> goodies = new ArrayList<>();
    protected Cuboid spawnArea = Cuboid.ZERO;
    protected Phase phase = Phase.IDLE;
    protected List<Cuboid> checkpoints = new ArrayList<>();
    protected int phaseTicks;
    protected List<Racer> racers = new ArrayList<>();
    protected long startTime = 0;
    protected int finishIndex = 0;
    protected int laps = 1;
    protected int rareItemsAvailable = 0;
    protected long maxDuration = 0; // seconds

    public int countRacers() {
        int count = 0;
        for (Racer racer : racers) {
            if (racer.racing && !racer.finished && racer.isOnline()) count += 1;
        }
        return count;
    }

    public int countAllRacers() {
        int count = 0;
        for (Racer racer : racers) {
            if (racer.racing) count += 1;
        }
        return count;
    }
}
