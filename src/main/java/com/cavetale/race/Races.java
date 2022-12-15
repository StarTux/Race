package com.cavetale.race;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * Races manager.
 */
@RequiredArgsConstructor
public final class Races {
    private final RacePlugin plugin;
    private final List<Race> races = new ArrayList<>();

    public void load() {
        races.clear();
        for (File file : plugin.getSaveFolder().listFiles()) {
            String fileName = file.getName();
            if (!fileName.endsWith(".json")) continue;
            Tag tag = Json.load(file, Tag.class);
            String name = fileName.substring(0, fileName.length() - 5);
            if (tag == null) {
                plugin.getLogger().warning("Failed to load race: " + file);
                continue;
            }
            Race race = new Race(plugin, name, tag);
            if (tag.fix()) {
                plugin.getLogger().info("Race fixed: " + name);
                race.save();
            }
            races.add(race);
        }
    }

    public void onEnable() {
        for (Race race : races) {
            race.onEnable();
        }
    }

    public void onDisable() {
        for (Race race : races) {
            race.onDisable();
        }
    }

    public void save() {
        for (Race race : races) {
            race.save();
        }
    }

    public Race at(Location loc) {
        for (Race race : races) {
            if (!race.isIn(loc.getWorld())) continue;
            if (Race.containsHorizontal(race.tag.area, loc)) return race;
        }
        return null;
    }

    public Race at(Block block) {
        for (Race race : races) {
            if (!race.isIn(block.getWorld())) continue;
            if (Race.containsHorizontal(race.tag.area, block)) return race;
        }
        return null;
    }

    public Race named(String name) {
        for (Race race : races) {
            if (race.name.equals(name)) return race;
        }
        return null;
    }

    public void tick(int ticks) {
        for (Race race : races) race.tick(ticks);
    }

    public boolean isRace(Location loc) {
        return at(loc) != null;
    }

    public boolean isRace(Block block) {
        return at(block) != null;
    }

    public List<Race> all() {
        return new ArrayList<>(races);
    }

    public List<String> names() {
        List<String> result = new ArrayList<>(races.size());
        for (Race race : races) {
            result.add(race.name);
        }
        return result;
    }

    public void add(Race race) {
        races.add(race);
    }
}
