package com.cavetale.race;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.TreeSpecies;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Strider;
import org.bukkit.entity.Vehicle;

public enum RaceType {
    WALK,
    STRIDER,
    PARKOUR,
    ICE_BOAT;

    public boolean isMounted() {
        return this == STRIDER
            || this == ICE_BOAT;
    }

    Vehicle spawnVehicle(Location location) {
        switch (this) {
        case STRIDER:
            return location.getWorld().spawn(location, Strider.class, e -> {
                    e.setPersistent(false);
                    e.setShivering(false);
                    e.setAdult();
                    e.setAgeLock(true);
                });
        case ICE_BOAT: {
            TreeSpecies[] species = TreeSpecies.values();
            TreeSpecies theSpecies = species[ThreadLocalRandom.current().nextInt(species.length)];
            return location.getWorld().spawn(location, Boat.class, e -> {
                    e.setPersistent(false);
                    e.setWoodType(theSpecies);
                });
        }
        default:
            return null;
        }
    }
}
