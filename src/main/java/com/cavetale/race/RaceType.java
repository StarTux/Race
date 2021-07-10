package com.cavetale.race;

import com.cavetale.race.util.Rnd;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeSpecies;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Strider;
import org.bukkit.entity.Vehicle;
import org.bukkit.inventory.ItemStack;

public enum RaceType {
    WALK,
    STRIDER,
    PARKOUR,
    ICE_BOAT,
    BOAT,
    HORSE,
    PIG;

    public boolean isMounted() {
        switch (this) {
        case STRIDER:
        case ICE_BOAT:
        case BOAT:
        case HORSE:
        case PIG:
            return true;
        default:
            return false;
        }
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
        case BOAT:
        case ICE_BOAT: {
            TreeSpecies[] species = TreeSpecies.values();
            TreeSpecies theSpecies = species[ThreadLocalRandom.current().nextInt(species.length)];
            return location.getWorld().spawn(location, Boat.class, e -> {
                    e.setPersistent(false);
                    e.setWoodType(theSpecies);
                });
        }
        case HORSE: {
            Horse horse = location.getWorld().spawn(location, Horse.class, e -> {
                    e.setPersistent(false);
                    e.setAdult();
                    e.setAgeLock(true);
                    e.setColor(Rnd.pick(Horse.Color.values()));
                    e.setStyle(Rnd.pick(Horse.Style.values()));
                    double variance = 0.01;
                    e.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.29);
                    e.getAttribute(Attribute.HORSE_JUMP_STRENGTH).setBaseValue(0.7);
                    e.setTamed(true);
                    e.getInventory().setSaddle(new ItemStack(Material.SADDLE));
                });
            return horse;
        }
        case PIG: {
            Pig pig = location.getWorld().spawn(location, Pig.class, e -> {
                    e.setPersistent(false);
                    e.setAdult();
                    e.setAgeLock(true);
                    e.setSaddle(true);
                    e.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.25);
                });
            return pig;
        }
        default:
            return null;
        }
    }
}
